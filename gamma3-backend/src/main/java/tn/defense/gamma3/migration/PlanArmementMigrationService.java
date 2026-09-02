package tn.defense.gamma3.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.tiers.domain.PlanArmement;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanArmementMigrationService {

    private final ItemRepository itemRepository;
    private final UniteUtilisatriceRepository uniteRepository;
    private final PlanArmementRepository planArmementRepository;

    @Value("${spring.legacy.datasource.url}")
    private String legacyDbUrl;

    @Value("${spring.legacy.datasource.username}")
    private String legacyDbUser;

    @Value("${spring.legacy.datasource.password}")
    private String legacyDbPassword;

    @Transactional
    public void runMigration() {
        log.info("Démarrage de la migration du Plan d'Armement depuis Gamma2 (SQL Server)...");

        log.info("Chargement des unités utilisatrices existantes...");
        Map<String, UniteUtilisatrice> uniteCache = uniteRepository.findAll().stream()
                .collect(Collectors.toMap(UniteUtilisatrice::getCode, u -> u));

        log.info("Chargement du catalogue des articles existants...");
        Map<String, Item> itemCache = itemRepository.findAll().stream()
                .collect(Collectors.toMap(Item::getNomenclature, i -> i));

        int totalMigrated = 0;
        int skippedUnites = 0;
        int skippedItems = 0;
        
        log.info("Suppression des anciens plans d'armement (idempotence)...");
        planArmementRepository.deleteAllInBatch();

        // Utilisation d'une Map pour dédupliquer/agréger les lignes (UniteId_ItemId)
        Map<String, PlanArmement> deduplicatedBatch = new java.util.HashMap<>();

        String sql = "SELECT UniteCode, ClasseCode, SousClasseCode, CategorieCode, SerieCode, ItemCode, " +
                     "CataloguePlanArmementQtitType, CatalogueQtitReel, CatalogueQtitVirt " +
                     "FROM Catalogue " +
                     "WHERE CataloguePlanArmementQtitType > 0 OR CatalogueQtitReel > 0 OR CatalogueQtitVirt > 0";

        try (Connection conn = DriverManager.getConnection(legacyDbUrl, legacyDbUser, legacyDbPassword);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String uniteCode = rs.getString("UniteCode");
                String classeCode = rs.getString("ClasseCode");
                String sousClasseCode = rs.getString("SousClasseCode");
                String categorieCode = rs.getString("CategorieCode");
                String serieCode = rs.getString("SerieCode");
                String itemCode = rs.getString("ItemCode");

                uniteCode = uniteCode != null ? uniteCode.trim() : "";
                String nomenclature = pad(classeCode, 2) + pad(sousClasseCode, 2) + pad(categorieCode, 2) + pad(serieCode, 2) + pad(itemCode, 4);

                BigDecimal qteType = rs.getBigDecimal("CataloguePlanArmementQtitType");
                BigDecimal qteReelle = rs.getBigDecimal("CatalogueQtitReel");
                BigDecimal qteVirtuelle = rs.getBigDecimal("CatalogueQtitVirt");

                UniteUtilisatrice unite = uniteCache.get(uniteCode);
                if (unite == null) {
                    skippedUnites++;
                    continue;
                }

                Item item = itemCache.get(nomenclature);
                if (item == null) {
                    skippedItems++;
                    continue;
                }

                String dedupKey = unite.getId() + "_" + item.getId();
                
                PlanArmement existingPlan = deduplicatedBatch.get(dedupKey);
                if (existingPlan != null) {
                    // Agréger en cas de doublon dans Gamma2
                    existingPlan.setQuantiteType(existingPlan.getQuantiteType().add(qteType != null ? qteType : BigDecimal.ZERO));
                    existingPlan.setQuantiteReelle(existingPlan.getQuantiteReelle().add(qteReelle != null ? qteReelle : BigDecimal.ZERO));
                    existingPlan.setQuantiteVirtuelle(existingPlan.getQuantiteVirtuelle().add(qteVirtuelle != null ? qteVirtuelle : BigDecimal.ZERO));
                } else {
                    PlanArmement plan = PlanArmement.builder()
                            .unite(unite)
                            .item(item)
                            .quantiteType(qteType != null ? qteType : BigDecimal.ZERO)
                            .quantiteReelle(qteReelle != null ? qteReelle : BigDecimal.ZERO)
                            .quantiteVirtuelle(qteVirtuelle != null ? qteVirtuelle : BigDecimal.ZERO)
                            .build();
                    deduplicatedBatch.put(dedupKey, plan);
                }
            }

            // Sauvegarder tout le dictionnaire dédupliqué en une seule fois (Spring Data JPA gère l'optimisation batch)
            if (!deduplicatedBatch.isEmpty()) {
                log.info("Sauvegarde de {} plans d'armement dédupliqués en base...", deduplicatedBatch.size());
                planArmementRepository.saveAll(deduplicatedBatch.values());
                totalMigrated = deduplicatedBatch.size();
            }

            log.info("Migration terminée avec succès !");
            log.info("Total migré : {}", totalMigrated);
            log.info("Ignorés (Unité inconnue) : {}", skippedUnites);
            log.info("Ignorés (Article inconnu) : {}", skippedItems);

        } catch (Exception e) {
            log.error("Erreur lors de la migration du Plan d'Armement", e);
            throw new RuntimeException("Échec de la migration ETL", e);
        }
    }

    private String pad(String s, int len) {
        if (s == null) return "0".repeat(len);
        s = s.trim();
        if (s.length() >= len) return s.substring(0, len);
        return "0".repeat(len - s.length()) + s;
    }
}
