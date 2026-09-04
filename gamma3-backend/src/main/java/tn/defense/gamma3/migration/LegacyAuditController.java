package tn.defense.gamma3.migration;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.defense.gamma3.auth.repository.UserRepository;
import tn.defense.gamma3.catalogue.repository.ItemRepository;
import tn.defense.gamma3.distribution.repository.BonSortieRepository;
import tn.defense.gamma3.distribution.repository.DemandeMaterielRepository;
import tn.defense.gamma3.reception.repository.BonEntreeRepository;
import tn.defense.gamma3.reception.repository.BonProvisoireReceptionRepository;
import tn.defense.gamma3.reception.repository.LigneReceptionRepository;
import tn.defense.gamma3.reforme.repository.DossierReformeRepository;
import tn.defense.gamma3.stock.repository.MagasinRepository;
import tn.defense.gamma3.stock.repository.MouvementStockRepository;
import tn.defense.gamma3.stock.repository.StockRepository;
import tn.defense.gamma3.tiers.repository.FournisseurRepository;
import tn.defense.gamma3.tiers.repository.PlanArmementRepository;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Audit de migration Gamma2 → Gamma3.
 *
 * Contexte : la reprise de données depuis Gamma2 (SQL Server) n'a JAMAIS été un processus
 * automatisé complet et rejouable. Deux mécanismes distincts, découverts en relisant le
 * projet, ont chacun migré UN SEUL sous-ensemble de données :
 *   1. `migration/gamma2_to_gamma3.load` (script pgloader, exécuté manuellement, une fois) :
 *      "INCLUDING ONLY TABLE NAMES LIKE 'Item'" — copie brute de la seule table SQL Server
 *      `dbo.Item` vers une table de préparation Postgres `legacy.item`. Le peuplement final
 *      de la table `items` (63 283 articles) à partir de `legacy.item` a été fait manuellement
 *      (aucune trace dans le code Java : DataSeeder ne fait que SYNCHRONISER une colonne,
 *      `type_consommabilite`, en supposant que `items` est déjà peuplée).
 *   2. `PlanArmementMigrationService` (endpoint POST /api/v1/migration/plan-armement, ADMIN) :
 *      pull JDBC live (pas pgloader) de la table SQL Server `Catalogue` (quantités de Plan
 *      d'Armement par unité/article) vers `plan_armement`. Celui-ci EST rejouable.
 *
 * Aucune autre table de Gamma2 (mouvements de stock historiques, anciens bons de sortie,
 * anciennes réceptions, anciens comptes utilisateurs, historique des demandes, etc.) n'a
 * de mécanisme de migration connu dans ce code. Cet endpoint répond directement à la
 * question "que reste-t-il dans Gamma2 qui n'a pas été repris ?" en listant TOUTES les
 * tables réellement présentes dans la base SQL Server Gamma2 (avec leur nombre de lignes),
 * à comparer avec l'état actuel de Gamma3 ci-dessous — plutôt que de deviner depuis le code.
 */
@RestController
@RequestMapping("/api/v1/migration")
@RequiredArgsConstructor
public class LegacyAuditController {

    private final ItemRepository itemRepository;
    private final PlanArmementRepository planArmementRepository;
    private final UniteUtilisatriceRepository uniteRepository;
    private final MagasinRepository magasinRepository;
    private final UserRepository userRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final StockRepository stockRepository;
    private final DemandeMaterielRepository demandeMaterielRepository;
    private final BonSortieRepository bonSortieRepository;
    private final LigneReceptionRepository ligneReceptionRepository;
    private final BonEntreeRepository bonEntreeRepository;
    private final BonProvisoireReceptionRepository bonProvisoireReceptionRepository;
    private final FournisseurRepository fournisseurRepository;
    private final DossierReformeRepository dossierReformeRepository;

    @Value("${spring.legacy.datasource.url}")
    private String legacyDbUrl;

    @Value("${spring.legacy.datasource.username}")
    private String legacyDbUser;

    @Value("${spring.legacy.datasource.password}")
    private String legacyDbPassword;

    public record LegacyTableInfo(String schema, String table, long rowCount) {}

    public record AuditReport(
            boolean legacyReachable,
            String legacyError,
            List<LegacyTableInfo> legacyTables,
            Map<String, Long> gamma3Counts,
            List<String> connuMigre,
            List<String> aVerifier
    ) {}

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuditReport> auditGamma2(){
        List<LegacyTableInfo> legacyTables = new ArrayList<>();
        boolean reachable = true;
        String error = null;

        // Compte rapide des lignes par table (sys.partitions), sans scanner chaque table
        // en entier : essentiel car certaines tables Gamma2 peuvent être volumineuses et le
        // lien réseau vers ce serveur SQL Server peut être lent depuis certains postes.
        String sql = "SELECT s.name AS schema_name, t.name AS table_name, "
                + "SUM(p.rows) AS row_count "
                + "FROM sys.tables t "
                + "JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "JOIN sys.partitions p ON t.object_id = p.object_id "
                + "WHERE p.index_id IN (0, 1) "
                + "GROUP BY s.name, t.name "
                + "ORDER BY s.name, t.name";

        try (Connection conn = DriverManager.getConnection(legacyDbUrl, legacyDbUser, legacyDbPassword);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                legacyTables.add(new LegacyTableInfo(
                        rs.getString("schema_name"),
                        rs.getString("table_name"),
                        rs.getLong("row_count")
                ));
            }
        } catch (Exception e) {
            reachable = false;
            error = e.getClass().getSimpleName() + " : " + e.getMessage();
        }

        Map<String, Long> gamma3Counts = new LinkedHashMap<>();
        gamma3Counts.put("items (catalogue)", itemRepository.count());
        gamma3Counts.put("plan_armement", planArmementRepository.count());
        gamma3Counts.put("unites_utilisatrices", uniteRepository.count());
        gamma3Counts.put("magasins", magasinRepository.count());
        gamma3Counts.put("users (comptes de connexion)", userRepository.count());
        gamma3Counts.put("mouvements_stock", mouvementStockRepository.count());
        gamma3Counts.put("stocks", stockRepository.count());
        gamma3Counts.put("demandes_materiel", demandeMaterielRepository.count());
        gamma3Counts.put("bons_sortie", bonSortieRepository.count());
        gamma3Counts.put("lignes_reception", ligneReceptionRepository.count());
        gamma3Counts.put("bons_entree", bonEntreeRepository.count());
        gamma3Counts.put("bons_provisoires_reception", bonProvisoireReceptionRepository.count());
        gamma3Counts.put("fournisseurs", fournisseurRepository.count());
        gamma3Counts.put("dossiers_reforme", dossierReformeRepository.count());

        List<String> connuMigre = List.of(
                "dbo.Item → items (pgloader, ONE-SHOT MANUEL — voir migration/gamma2_to_gamma3.load ; "
                        + "aucun mécanisme rejouable connu dans le code)",
                "dbo.Catalogue → plan_armement (PlanArmementMigrationService, REJOUABLE via "
                        + "POST /api/v1/migration/plan-armement)"
        );
        List<String> aVerifier = List.of(
                "Toute autre table listée ci-dessus dans legacyTables (mouvements de stock, "
                        + "bons de sortie, réceptions, comptes utilisateurs, fournisseurs, historique "
                        + "des demandes, etc.) n'a AUCUN mécanisme de migration connu dans ce code — "
                        + "comparez son rowCount ici à zéro (ou à une valeur faible) côté gamma3Counts "
                        + "pour repérer les écarts probables."
        );

        AuditReport report = new AuditReport(reachable, error, legacyTables, gamma3Counts, connuMigre, aVerifier);
        return ResponseEntity.ok(report);
    }
}
