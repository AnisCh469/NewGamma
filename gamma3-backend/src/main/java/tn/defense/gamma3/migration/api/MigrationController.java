package tn.defense.gamma3.migration.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.defense.gamma3.migration.PlanArmementMigrationService;

@RestController
@RequestMapping("/api/v1/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final PlanArmementMigrationService planArmementMigrationService;

    @PostMapping("/plan-armement")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> runPlanArmementMigration() {
        try {
            planArmementMigrationService.runMigration();
            return ResponseEntity.ok("Migration du Plan d'Armement terminée avec succès. Consultez les logs du serveur pour les détails.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erreur lors de la migration : " + e.getMessage());
        }
    }
}
