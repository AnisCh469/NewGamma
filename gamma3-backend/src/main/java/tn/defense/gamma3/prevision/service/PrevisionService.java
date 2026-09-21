package tn.defense.gamma3.prevision.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.catalogue.domain.CodeSoutien;
import tn.defense.gamma3.catalogue.domain.Criticite;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.domain.TypeConsommabilite;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.distribution.repository.LigneDemandeRepository;
import tn.defense.gamma3.prevision.dto.PrevisionDto;
import tn.defense.gamma3.stock.repository.MouvementStockRepository;
import tn.defense.gamma3.stock.repository.StockRepository;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * RÔLE : Procédure P01 du CCF (Prévisions) — version allégée, voir
 * docs/PROPOSITION_PREVISIONS_ET_COLLATIONNEMENT.md §2.3 (proposition validée par l'utilisateur,
 * 2026-09-21). Calcule, pour chaque article réellement en circulation (au moins une ligne de
 * stock ou une dotation au Plan d'Armement — le catalogue complet migré de Gamma2 compte
 * 63 000+ articles, la plupart jamais mouvementés : les parcourir tous serait à la fois lent et
 * sans intérêt opérationnel), le seuil d'alerte (Sa) officiel du CCF et signale les articles
 * dont le stock potentiel est descendu à ce niveau ou en dessous.
 *
 * FORMULES (CCF, Procédure P01, RG3 à RG20) :
 *   - Sa = 0 si l'article est en soutien ARRÊTÉ (S50) ou SUSPENDU (S40) — "stock mort" (RG8).
 *   - Sa = Cmoy3 × n pour un article CONSOMMABLE (RG5).
 *   - Sa = 10% du parc × n pour un article NON_CONSOMMABLE / armement (RG6).
 *   - Ss (seuil de sécurité) = Sa / 3, Smin (seuil minimal) = Sa − Ss — UNIQUEMENT pour un
 *     article Critique (RG13-RG15) ; calculés ici à titre indicatif, jamais appliqués
 *     automatiquement — voir la note sur `Item.stockSecurite` dans {@link PrevisionDto}.
 *   - Stock potentiel = stock réel − réservé − stockSecurite (RG9, simplifié : ne soustrait pas
 *     une éventuelle "quantité commandée", non suivie par GAMMA 3 aujourd'hui — Procédure P02
 *     hors périmètre).
 *
 * PERFORMANCE : chaque composant (stock total, sorties 3 ans, dotation Plan d'Armement,
 * réservations) est calculé par UNE requête agrégée (GROUP BY), jamais une requête par article —
 * indispensable vu le volume de `mouvements_stock` (163 000+ lignes historiques migrées de
 * Gamma2). Déclenché à la demande (bouton "Recalculer" côté écran), pas à chaque affichage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrevisionService {

    private final StockRepository stockRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final PlanArmementRepository planArmementRepository;
    private final LigneDemandeRepository ligneDemandeRepository;
    private final ItemRepository itemRepository;

    private static final int DELAI_ANNEES_DEFAUT = 1; // CCF RG7 : 1 an, cas le plus courant (bon de commande/marché cadre/gré à gré)
    private static final BigDecimal TAUX_ARMEMENT = new BigDecimal("0.10"); // CCF RG6 : 10% du parc

    @Transactional(readOnly = true)
    public List<PrevisionDto> calculerAlertes() {
        long debut = System.currentTimeMillis();

        Map<UUID, BigDecimal> stockParItem = versMap(stockRepository.getAllTotalQuantitiesByItem());
        Map<UUID, BigDecimal> parcParItem = versMap(planArmementRepository.sommeQuantiteReelleParItem());

        // Périmètre : tout article ayant du stock OU une dotation Plan d'Armement (voir javadoc).
        Set<UUID> candidats = new HashSet<>(stockParItem.keySet());
        candidats.addAll(parcParItem.keySet());
        if (candidats.isEmpty()) return List.of();
        List<UUID> candidatsList = new ArrayList<>(candidats);

        LocalDateTime depuis3Ans = LocalDateTime.now().minus(3 * 365, ChronoUnit.DAYS);
        Map<UUID, BigDecimal> sortiesParItem = versMap(mouvementStockRepository.sommeSortiesParItemDepuis(depuis3Ans));
        Map<UUID, BigDecimal> reserveParItem = versMap(ligneDemandeRepository.getReservedQuantitiesByItemIds(candidatsList));

        Map<UUID, Item> itemsParId = new HashMap<>();
        for (Item item : itemRepository.findAllById(candidatsList)) {
            itemsParId.put(item.getId(), item);
        }

        List<PrevisionDto> alertes = new ArrayList<>();
        for (UUID itemId : candidatsList) {
            Item item = itemsParId.get(itemId);
            if (item == null) continue; // article supprimé entre-temps

            BigDecimal stockActuel = stockParItem.getOrDefault(itemId, BigDecimal.ZERO);
            BigDecimal sorties3Ans = sortiesParItem.getOrDefault(itemId, BigDecimal.ZERO);
            BigDecimal cmoy3 = sorties3Ans.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            BigDecimal parc = parcParItem.getOrDefault(itemId, BigDecimal.ZERO);
            BigDecimal reserve = reserveParItem.getOrDefault(itemId, BigDecimal.ZERO);
            BigDecimal stockSecurite = item.getStockSecurite() != null ? item.getStockSecurite() : BigDecimal.ZERO;

            int n = item.getDelaiApprovisionnementAnnees() != null ? item.getDelaiApprovisionnementAnnees() : DELAI_ANNEES_DEFAUT;

            boolean soutienArreteOuSuspendu = item.getCodeSoutien() == CodeSoutien.S40 || item.getCodeSoutien() == CodeSoutien.S50;
            BigDecimal sa;
            if (soutienArreteOuSuspendu) {
                sa = BigDecimal.ZERO;
            } else if (item.getTypeConsommabilite() == TypeConsommabilite.NON_CONSOMMABLE) {
                sa = parc.multiply(TAUX_ARMEMENT).multiply(BigDecimal.valueOf(n));
            } else {
                sa = cmoy3.multiply(BigDecimal.valueOf(n));
            }
            if (sa.compareTo(BigDecimal.ZERO) <= 0) continue; // aucun seuil pertinent à calculer pour cet article

            BigDecimal stockPotentiel = stockActuel.subtract(reserve).subtract(stockSecurite);
            if (stockPotentiel.compareTo(sa) > 0) continue; // au-dessus du seuil, pas d'alerte

            BigDecimal ss = null;
            BigDecimal smin = null;
            if (item.getCriticite() == Criticite.CRITIQUE) {
                ss = sa.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
                smin = sa.subtract(ss);
            }

            alertes.add(new PrevisionDto(
                    item.getId(), item.getNomenclature(), item.getDesignation(),
                    item.getTypeConsommabilite() != null ? item.getTypeConsommabilite().name() : null,
                    stockActuel, reserve, stockPotentiel, cmoy3, parc, n,
                    sa, ss, smin, stockSecurite
            ));
        }

        // Urgence = déficit le plus important en premier (stockPotentiel - Sa le plus négatif).
        alertes.sort(Comparator.comparing(a -> a.stockPotentiel().subtract(a.seuilAlerte())));

        log.info("Prévisions (P01) : {} article(s) en alerte sur {} candidat(s) analysé(s), en {} ms",
                alertes.size(), candidatsList.size(), System.currentTimeMillis() - debut);
        return alertes;
    }

    private static Map<UUID, BigDecimal> versMap(List<Object[]> lignes) {
        Map<UUID, BigDecimal> resultat = new HashMap<>();
        for (Object[] ligne : lignes) {
            resultat.put((UUID) ligne[0], (BigDecimal) ligne[1]);
        }
        return resultat;
    }
}
