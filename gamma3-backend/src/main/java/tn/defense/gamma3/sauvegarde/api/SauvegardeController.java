package tn.defense.gamma3.sauvegarde.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.sauvegarde.service.SauvegardeService;

/**
 * Administration > Sauvegardes (Phase 2 de la gouvernance des rôles, voir
 * docs/PROPOSITION_GOUVERNANCE_ROLES.md §4.1) — réservé au SUPER_ADMIN, comme tout ce qui
 * touche à l'infrastructure du système (voir aussi MigrationController, LegacyAuditController,
 * UserAdminController). Aucun endpoint de téléchargement du fichier produit n'est exposé ici
 * par conception — voir la javadoc de {@link SauvegardeService}.
 */
@RestController
@RequestMapping("/api/v1/sauvegardes")
@RequiredArgsConstructor
@Slf4j
public class SauvegardeController {

    private final SauvegardeService service;

    @GetMapping
    public ResponseEntity<?> lister(Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (u.getRole() != Role.SUPER_ADMIN) return ResponseEntity.status(403).body("Réservé à l'administrateur système.");
        return ResponseEntity.ok(service.lister());
    }

    @PostMapping
    public ResponseEntity<?> declencher(Authentication authentication, HttpServletRequest httpRequest) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (u.getRole() != Role.SUPER_ADMIN) return ResponseEntity.status(403).body("Réservé à l'administrateur système.");
        try {
            return ResponseEntity.ok(service.declencher(u, httpRequest.getRemoteAddr()));
        } catch (IllegalStateException e) {
            log.error("Échec de la sauvegarde déclenchée par {} : {}", u.getMatricule(), e.getMessage());
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    private static User currentUser(Authentication auth) {
        return (auth != null && auth.getPrincipal() instanceof User u) ? u : null;
    }
}
