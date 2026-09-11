package tn.defense.gamma3.parametre.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.audit.domain.TypeActionAudit;
import tn.defense.gamma3.audit.service.JournalAuditService;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.parametre.service.ParametreSystemeService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/parametres")
@RequiredArgsConstructor
public class ParametreSystemeController {

    private final ParametreSystemeService service;
    private final JournalAuditService journalAuditService;

    /** Lu par tout utilisateur authentifié : c'est ce délai que le front applique lui-même. */
    @GetMapping("/session")
    public ResponseEntity<?> getSession(Authentication authentication) {
        if (currentUser(authentication) == null) return ResponseEntity.status(401).body("Authentification requise.");
        return ResponseEntity.ok(Map.of("inactiviteMinutes", service.getInactiviteMinutes()));
    }

    @PutMapping("/session")
    public ResponseEntity<?> setSession(@RequestBody Map<String, Integer> body,
                                         Authentication authentication,
                                         HttpServletRequest httpRequest) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (u.getRole() != Role.ADMIN) return ResponseEntity.status(403).body("Réservé à l'administrateur.");

        Integer minutes = body.get("inactiviteMinutes");
        if (minutes == null) return ResponseEntity.badRequest().body("Le champ 'inactiviteMinutes' est requis.");

        try {
            int applique = service.setInactiviteMinutes(minutes, u);
            journalAuditService.enregistrer(u.getMatricule(), u.getFullName(), TypeActionAudit.PARAMETRE_MODIFIE,
                    "Délai d'inactivité avant déconnexion fixé à " + applique + " minutes.", httpRequest.getRemoteAddr());
            return ResponseEntity.ok(Map.of("inactiviteMinutes", applique));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private static User currentUser(Authentication auth) {
        return (auth != null && auth.getPrincipal() instanceof User u) ? u : null;
    }
}
