package tn.defense.gamma3.audit.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.audit.domain.JournalAudit;
import tn.defense.gamma3.audit.domain.TypeActionAudit;
import tn.defense.gamma3.audit.service.JournalAuditService;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;

/**
 * Consultation du journal d'audit — traçabilité de chaque intervenant (connexions,
 * déconnexions, modifications de comptes, changements de paramètres système…).
 * Lecture seule, réservée à l'ADMIN. Aucun endpoint de modification/suppression n'existe :
 * le journal est append-only par construction (voir {@code JournalAuditService}).
 */
@RestController
@RequestMapping("/api/v1/audit-journal")
@RequiredArgsConstructor
public class JournalAuditController {

    private final JournalAuditService service;

    @GetMapping
    public ResponseEntity<?> lister(
            @RequestParam(required = false) String matricule,
            @RequestParam(required = false) TypeActionAudit action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (u.getRole() != Role.ADMIN) return ResponseEntity.status(403).body("Réservé à l'administrateur.");

        Page<JournalAudit> resultat = service.lister(matricule, action, PageRequest.of(page, Math.min(Math.max(size, 1), 200)));
        return ResponseEntity.ok(resultat);
    }

    private static User currentUser(Authentication auth) {
        return (auth != null && auth.getPrincipal() instanceof User u) ? u : null;
    }
}
