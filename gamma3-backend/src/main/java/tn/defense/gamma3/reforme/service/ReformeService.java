package tn.defense.gamma3.reforme.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.reforme.domain.DossierReforme;
import tn.defense.gamma3.reforme.domain.LigneReforme;
import tn.defense.gamma3.reforme.repository.DossierReformeRepository;
import tn.defense.gamma3.reforme.repository.LigneReformeRepository;
import tn.defense.gamma3.tiers.domain.PlanArmement;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.stock.domain.Magasin;
import tn.defense.gamma3.stock.domain.MouvementStock;
import tn.defense.gamma3.stock.domain.Stock;
import tn.defense.gamma3.stock.domain.TypeMouvement;
import tn.defense.gamma3.stock.repository.MagasinRepository;
import tn.defense.gamma3.stock.repository.MouvementStockRepository;
import tn.defense.gamma3.stock.repository.StockRepository;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.repository.UserRepository;
import tn.defense.gamma3.notification.domain.Notification;
import tn.defense.gamma3.notification.repository.NotificationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReformeService {

    private final DossierReformeRepository dossierRepository;
    private final LigneReformeRepository ligneRepository;
    private final UniteUtilisatriceRepository uniteRepository;
    private final ItemRepository itemRepository;
    private final PlanArmementRepository planArmementRepository;
    private final MagasinRepository magasinRepository;
    private final StockRepository stockRepository;
    private final MouvementStockRepository mouvementRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<DossierReforme> getAllDossiers() {
        return dossierRepository.findAllWithRelations();
    }

    @Transactional(readOnly = true)
    public List<DossierReforme> getDossiersByUnite(Long uniteId) {
        return dossierRepository.findByUniteId(uniteId);
    }

    @Transactional(readOnly = true)
    public List<LigneReforme> getLignesByDossier(Long dossierId) {
        return ligneRepository.findByDossierReformeId(dossierId);
    }

    @Transactional
    public DossierReforme creerDossier(Long uniteId, String motif, List<LigneReforme> lignes) {
        var unite = uniteRepository.findById(uniteId).orElseThrow();
        
        var dossier = DossierReforme.builder()
                .numeroDossier("DR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .dateDemande(LocalDate.now())
                .unite(unite)
                .statut("SOUMIS")
                .motif(motif)
                .build();

        var savedDossier = dossierRepository.save(dossier);

        for (var ligne : lignes) {
            var item = itemRepository.findById(ligne.getItem().getId()).orElseThrow();
            ligne.setDossierReforme(savedDossier);
            ligne.setItem(item);
            ligneRepository.save(ligne);
        }

        // Trigger Notification for Admin/Operator
        notificationRepository.save(Notification.builder()
                .title("Nouveau Dossier de Réforme")
                .message("L'unité " + savedDossier.getUnite().getNom() + " a soumis un dossier de réforme (N° " + savedDossier.getNumeroDossier() + ") pour " + lignes.size() + " article(s) (Motif : " + motif + ").")
                .type("REFORME")
                .uniteCode(null)
                .referenceId(savedDossier.getUnite().getId().toString())
                .build());

        return savedDossier;
    }

    @Transactional
    public DossierReforme traiterDossierCommission(Long dossierId, String decision, String membres, String observations, LocalDate dateCommission, String userMatricule) {
        var dossier = dossierRepository.findByIdWithRelations(dossierId).orElseThrow();
        
        dossier.setStatut("TRAITE");
        dossier.setDecisionCommission(decision);
        dossier.setMembresCommission(membres);
        dossier.setObservationsCommission(observations);
        dossier.setDateCommission(dateCommission != null ? dateCommission : LocalDate.now());
        
        var savedDossier = dossierRepository.save(dossier);
        var lignes = ligneRepository.findByDossierReformeId(dossierId);

        User user = userRepository.findByMatricule(userMatricule)
                .orElseGet(() -> userRepository.findByMatricule("admin").orElseThrow());

        // Si la décision est REFORME_COMPLETE ou DECLASSEMENT, mettre à jour la dotation réelle et le stock SRR
        if ("REFORME_COMPLETE".equals(decision) || "DECLASSEMENT".equals(decision)) {
            // Trouver le magasin SRR (Service Réforme et Remise)
            Magasin magasinSrr = magasinRepository.findByCode("SRR")
                    .orElseGet(() -> magasinRepository.save(Magasin.builder()
                            .code("SRR")
                            .nom("Service Réforme et Remise")
                            .localisation("Zone de Réforme")
                            .build()));

            for (var ligne : lignes) {
                // 1. Mettre à jour le Plan d'Armement de l'unité (déduire la quantité réelle)
                planArmementRepository.findByUniteIdAndItemId(dossier.getUnite().getId(), ligne.getItem().getId())
                        .ifPresent(plan -> {
                            BigDecimal newQty = plan.getQuantiteReelle().subtract(ligne.getQuantite());
                            if (newQty.compareTo(BigDecimal.ZERO) < 0) newQty = BigDecimal.ZERO;
                            plan.setQuantiteReelle(newQty);
                            planArmementRepository.save(plan);
                        });

                // 2. Si DECLASSEMENT physique, transférer au magasin SRR et incrémenter son stock
                if ("DECLASSEMENT".equals(decision)) {
                    Stock stockSrr = stockRepository.findByItem_IdAndMagasin_Id(ligne.getItem().getId(), magasinSrr.getId())
                            .orElseGet(() -> Stock.builder()
                                    .magasin(magasinSrr)
                                    .item(ligne.getItem())
                                    .quantite(BigDecimal.ZERO)
                                    .emplacement("Zone de Réforme")
                                    .build());

                    stockSrr.setQuantite(stockSrr.getQuantite().add(ligne.getQuantite()));
                    stockRepository.save(stockSrr);

                    // Générer un mouvement de stock comptable de type REFORME
                    mouvementRepository.save(MouvementStock.builder()
                            .item(ligne.getItem())
                            .magasin(magasinSrr)
                            .typeMouvement(TypeMouvement.REFORME)
                            .quantite(ligne.getQuantite())
                            .dateMouvement(LocalDateTime.now())
                            .referenceBon(dossier.getNumeroDossier())
                            .motif("Déclassement commission réforme")
                            .creePar(user)
                            .build());
                }
            }
        }

        // Trigger Notification for Client Unit
        notificationRepository.save(Notification.builder()
                .title("Décision Commission Réforme")
                .message("La commission de réforme s'est prononcée sur le dossier N° " + savedDossier.getNumeroDossier() + ". Décision : " + decision + " (Observations : " + observations + ").")
                .type("REFORME")
                .uniteCode(savedDossier.getUnite().getCode())
                .referenceId(savedDossier.getUnite().getId().toString())
                .build());

        return savedDossier;
    }
}
