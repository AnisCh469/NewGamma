package tn.defense.gamma3.reforme.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.reforme.domain.DossierReforme;
import tn.defense.gamma3.reforme.domain.LigneReforme;
import tn.defense.gamma3.reforme.service.ReformeService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reformes")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class ReformeController {

    private final ReformeService reformeService;

    @GetMapping
    public List<DossierReforme> getAllDossiers() {
        return reformeService.getAllDossiers();
    }

    @GetMapping("/unite/{uniteId}")
    public List<DossierReforme> getDossiersByUnite(@PathVariable Long uniteId) {
        return reformeService.getDossiersByUnite(uniteId);
    }

    @GetMapping("/{id}/lignes")
    public List<LigneReforme> getLignesByDossier(@PathVariable Long id) {
        return reformeService.getLignesByDossier(id);
    }

    @PostMapping
    public ResponseEntity<DossierReforme> creerDossier(
            @RequestParam Long uniteId,
            @RequestParam String motif,
            @RequestBody List<LigneReforme> lignes) {
        if (lignes == null || lignes.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(reformeService.creerDossier(uniteId, motif, lignes));
    }

    @PostMapping("/{id}/commission")
    public ResponseEntity<DossierReforme> traiterCommission(
            @PathVariable Long id,
            @RequestParam String decision,
            @RequestParam String membres,
            @RequestParam String observations,
            @RequestParam(required = false) String dateCommission,
            Authentication authentication) {
        LocalDate date = dateCommission != null ? LocalDate.parse(dateCommission) : LocalDate.now();
        String userMatricule = authentication != null ? authentication.getName() : "admin";
        return ResponseEntity.ok(reformeService.traiterDossierCommission(id, decision, membres, observations, date, userMatricule));
    }
}
