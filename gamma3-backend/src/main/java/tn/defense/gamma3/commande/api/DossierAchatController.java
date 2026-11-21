package tn.defense.gamma3.commande.api;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.domain.FonctionDA;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.util.AccesDAUtil;
import tn.defense.gamma3.commande.domain.StatutAchat;
import tn.defense.gamma3.commande.dto.DossierAchatDto;
import tn.defense.gamma3.commande.service.DossierAchatService;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/commandes/achats")
@RequiredArgsConstructor
public class DossierAchatController {

    private final DossierAchatService dossierAchatService;

    @GetMapping
    public ResponseEntity<?> lister(
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) StatutAchat statut,
            Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (!AccesDAUtil.estAutorise(u, false, FonctionDA.SCS, FonctionDA.GEST)) {
            return ResponseEntity.status(403).body("Réservé à la Section Commande et Suivi (SCS) / DA.");
        }
        return ResponseEntity.ok(dossierAchatService.lister(annee, statut));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id, Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        return ResponseEntity.ok(dossierAchatService.getById(id));
    }

    @PostMapping
    public ResponseEntity<?> enregistrer(@RequestBody DossierAchatDto dto, Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (!AccesDAUtil.estAutorise(u, false, FonctionDA.SCS, FonctionDA.GEST)) {
            return ResponseEntity.status(403).body("Réservé à la Section Commande et Suivi (SCS) / DA.");
        }
        return ResponseEntity.ok(dossierAchatService.creerOuModifier(dto, u));
    }

    @PatchMapping("/{id}/statut")
    public ResponseEntity<?> changerStatut(
            @PathVariable UUID id,
            @RequestParam StatutAchat statut,
            @RequestParam(required = false) String referenceMarche,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateNotification,
            @RequestParam(required = false) String observations,
            Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (!AccesDAUtil.estAutorise(u, false, FonctionDA.SCS, FonctionDA.GEST)) {
            return ResponseEntity.status(403).body("Réservé à la Section Commande et Suivi (SCS) / DA.");
        }
        return ResponseEntity.ok(dossierAchatService.changerStatut(id, statut, referenceMarche, dateNotification, observations));
    }

    private static User currentUser(Authentication authentication) {
        return (authentication != null && authentication.getPrincipal() instanceof User user) ? user : null;
    }
}
