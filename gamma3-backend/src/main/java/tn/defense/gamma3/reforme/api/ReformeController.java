package tn.defense.gamma3.reforme.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.domain.FonctionDA;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.util.AccesDAUtil;
import tn.defense.gamma3.reforme.domain.DossierReforme;
import tn.defense.gamma3.reforme.domain.LigneReforme;
import tn.defense.gamma3.reforme.service.ReformeService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reformes")
@RequiredArgsConstructor
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

    /**
     * Un dossier de réforme est INITIÉ par l'unité détentrice du matériel (via l'écran
     * "détail unité") ou par la DA. Un compte UNIT_USER ne peut le faire que pour SA propre
     * unité — sans ce contrôle (audit 2026-09-09), n'importe quel compte pouvait soumettre
     * un dossier au nom de n'importe quelle unité.
     */
    @PostMapping
    public ResponseEntity<?> creerDossier(
            @RequestParam Long uniteId,
            @RequestParam String motif,
            @RequestBody List<LigneReforme> lignes,
            Authentication authentication) {
        if (lignes == null || lignes.isEmpty()) {
            return ResponseEntity.badRequest().body("Au moins un article est requis.");
        }
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (u.getRole() == Role.UNIT_USER
                && (u.getUnite() == null || !u.getUnite().getId().equals(uniteId))) {
            return ResponseEntity.status(403).body("Vous ne pouvez soumettre un dossier de réforme que pour votre unité.");
        }
        return ResponseEntity.ok(reformeService.creerDossier(uniteId, motif, lignes));
    }

    /**
     * La décision de la commission de réforme est un acte de la DA : ADMIN / DA_MANAGER.
     *
     * CLOISONNEMENT PAR SECTION (Phase B, 2026-09-14, voir docs/PROPOSITION_ROLES_SECTIONS_DA.md
     * §4) : correspond à la Section Réforme et Remise du CCF (acronyme officiel SRR, "SeRef"
     * dans le tableau des groupes). Comportement historique inchangé pour tout compte ADMIN
     * sans `fonctionDA` renseignée — voir {@link AccesDAUtil}.
     */
    @PostMapping("/{id}/commission")
    public ResponseEntity<?> traiterCommission(
            @PathVariable Long id,
            @RequestParam String decision,
            @RequestParam String membres,
            @RequestParam String observations,
            @RequestParam(required = false) String dateCommission,
            Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (!AccesDAUtil.estAutorise(u, true, FonctionDA.SRR)) {
            return ResponseEntity.status(403).body("Seule la Division de l'Approvisionnement (Section Réforme et Remise) statue sur une commission de réforme.");
        }
        LocalDate date = dateCommission != null ? LocalDate.parse(dateCommission) : LocalDate.now();
        return ResponseEntity.ok(reformeService.traiterDossierCommission(id, decision, membres, observations, date, u.getMatricule()));
    }

    private static User currentUser(Authentication authentication) {
        return (authentication != null && authentication.getPrincipal() instanceof User user) ? user : null;
    }
}
