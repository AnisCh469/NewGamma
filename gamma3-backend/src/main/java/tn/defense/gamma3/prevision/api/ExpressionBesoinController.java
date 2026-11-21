package tn.defense.gamma3.prevision.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.domain.FonctionDA;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.util.AccesDAUtil;
import tn.defense.gamma3.commande.domain.DossierAchat;
import tn.defense.gamma3.prevision.domain.ModeAcquisition;
import tn.defense.gamma3.prevision.domain.StatutBesoin;
import tn.defense.gamma3.prevision.dto.ExpressionBesoinDto;
import tn.defense.gamma3.prevision.dto.LigneExpressionBesoinDto;
import tn.defense.gamma3.prevision.service.ExpressionBesoinService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/previsions/besoins")
@RequiredArgsConstructor
public class ExpressionBesoinController {

    private final ExpressionBesoinService expressionBesoinService;

    @GetMapping
    public ResponseEntity<?> lister(
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) StatutBesoin statut,
            Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        return ResponseEntity.ok(expressionBesoinService.lister(annee, statut, u));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id, Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        return ResponseEntity.ok(expressionBesoinService.getById(id, u));
    }

    @PostMapping
    public ResponseEntity<?> enregistrer(@RequestBody ExpressionBesoinDto dto, Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        try {
            return ResponseEntity.ok(expressionBesoinService.enregistrer(dto, u));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Erreur enregistrement: " + e.getClass().getName() + " : " + e.getMessage());
        }
    }

    @PostMapping("/{id}/soumettre")
    public ResponseEntity<?> soumettre(@PathVariable UUID id, Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        return ResponseEntity.ok(expressionBesoinService.soumettre(id, u));
    }

    @PostMapping("/{id}/etudier-sep")
    public ResponseEntity<?> etudierSEP(
            @PathVariable UUID id,
            @RequestBody List<LigneExpressionBesoinDto> etudes,
            Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (!AccesDAUtil.estAutorise(u, false, FonctionDA.SEP)) {
            return ResponseEntity.status(403).body("Réservé à la Section Étude et Prévision (SEP).");
        }
        return ResponseEntity.ok(expressionBesoinService.etudierSEP(id, etudes, u));
    }

    @PostMapping("/{id}/arbitrer")
    public ResponseEntity<?> arbitrer(
            @PathVariable UUID id,
            @RequestBody List<LigneExpressionBesoinDto> arbitrages,
            Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (u.getRole() != Role.SUPER_ADMIN && u.getRole() != Role.ADMIN && u.getRole() != Role.DA_MANAGER) {
            return ResponseEntity.status(403).body("Réservé aux membres de la Commission DA.");
        }
        return ResponseEntity.ok(expressionBesoinService.arbitrerCommission(id, arbitrages, u));
    }

    @PostMapping("/{id}/valider-padi")
    public ResponseEntity<?> validerPADI(@PathVariable UUID id, Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (u.getRole() != Role.SUPER_ADMIN && u.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body("Réservé au commandement DA.");
        }
        return ResponseEntity.ok(expressionBesoinService.validerPADI(id, u));
    }

    @PostMapping("/transferer-achats")
    public ResponseEntity<?> transfererAchats(
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) ModeAcquisition mode,
            @RequestBody List<UUID> ligneIds,
            Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (u.getRole() != Role.SUPER_ADMIN && u.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body("Réservé à la DA.");
        }
        DossierAchat da = expressionBesoinService.transfererVersAchats(annee, ligneIds, mode, u);
        return ResponseEntity.ok(da.getId());
    }

    @GetMapping("/synthese-consolidee")
    public ResponseEntity<?> getSynthese(@RequestParam(required = false) Integer annee, Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (!AccesDAUtil.estAutorise(u, false, FonctionDA.SEP)) {
            return ResponseEntity.status(403).body("Réservé à la Section Étude et Prévision (SEP).");
        }
        return ResponseEntity.ok(expressionBesoinService.getSyntheseConsolidee(annee));
    }

    private static User currentUser(Authentication authentication) {
        return (authentication != null && authentication.getPrincipal() instanceof User user) ? user : null;
    }
}
