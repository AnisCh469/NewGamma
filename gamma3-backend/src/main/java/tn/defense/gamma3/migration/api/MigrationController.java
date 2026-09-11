package tn.defense.gamma3.migration.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.defense.gamma3.migration.MouvementMigrationService;
import tn.defense.gamma3.migration.PlanArmementMigrationService;

@RestController
@RequestMapping("/api/v1/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final PlanArmementMigrationService planArmementMigrationService;
    private final MouvementMigrationService mouvementMigrationService;

    @PostMapping("/plan-armement")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> runPlanArmementMigration() {
        try {
            planArmementMigrationService.runMigration();
            return ResponseEntity.ok("Migration du Plan d'Armement terminée avec succès. Consultez les logs du serveur pour les détails.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erreur lors de la migration : " + e.getMessage());
        }
    }

    /**
     * Rapatrie l'historique des mouvements de stock de Gamma 2 dans {@code mouvements_stock}
     * (archives — ne modifie pas les niveaux de stock actuels). Idempotent : purge d'abord
     * les lignes {@code reference_bon LIKE 'G2-%'} d'un import précédent.
     */
    @PostMapping("/mouvements-stock")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> runMouvementsMigration() {
        try {
            MouvementMigrationService.Resultat r = mouvementMigrationService.runMigration();
            return ResponseEntity.ok(java.util.Map.of(
                    "message", "Migration de l'historique des mouvements terminée.",
                    "lignesLues", r.lus(),
                    "mouvementsInseres", r.inseres(),
                    "ignoresArticleNonMigre", r.ignoresArticleInconnu(),
                    "dureeMs", r.dureeMs()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erreur lors de la migration : " + e.getMessage());
        }
    }
}
