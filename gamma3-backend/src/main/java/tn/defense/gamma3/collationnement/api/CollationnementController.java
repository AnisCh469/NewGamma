package tn.defense.gamma3.collationnement.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.domain.FonctionDA;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.util.AccesDAUtil;
import tn.defense.gamma3.collationnement.domain.CampagneCollationnement;
import tn.defense.gamma3.collationnement.domain.LigneCollationnement;
import tn.defense.gamma3.collationnement.dto.LigneComptageDto;
import tn.defense.gamma3.collationnement.service.CollationnementService;

import java.time.LocalDate;
import java.util.List;

/**
 * Administration > Collationnement (Procédure P07 du CCF) — voir
 * docs/PROPOSITION_PREVISIONS_ET_COLLATIONNEMENT.md. Réservé à la Division de l'Approvisionnement,
 * cloisonné Section Comptabilité (SC) — même patron que PrevisionController/ReformeController/
 * ReceptionController (voir {@link AccesDAUtil}).
 */
@RestController
@RequestMapping("/api/v1/collationnements")
@RequiredArgsConstructor
public class CollationnementController {

    private final CollationnementService service;

    public record CreerCampagneRequest(Long uniteId, LocalDate datePlanifiee, String membresCommission) {}
    public record ComptageRequest(List<LigneComptageDto> lignes) {}
    public record ClotureRequest(String observationsGenerales) {}

    @GetMapping
    public ResponseEntity<?> getAllCampagnes(Authentication authentication) {
        ResponseEntity<?> refus = exigerAcces(authentication);
        if (refus != null) return refus;
        return ResponseEntity.ok(service.getAllCampagnes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCampagne(@PathVariable Long id, Authentication authentication) {
        ResponseEntity<?> refus = exigerAcces(authentication);
        if (refus != null) return refus;
        try {
            return ResponseEntity.ok(service.getCampagne(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/lignes")
    public ResponseEntity<?> getLignes(@PathVariable Long id, Authentication authentication) {
        ResponseEntity<?> refus = exigerAcces(authentication);
        if (refus != null) return refus;
        return ResponseEntity.ok(service.getLignes(id));
    }

    @PostMapping
    public ResponseEntity<?> creerCampagne(@RequestBody CreerCampagneRequest request, Authentication authentication) {
        ResponseEntity<?> refus = exigerAcces(authentication);
        if (refus != null) return refus;
        if (request.uniteId() == null) return ResponseEntity.badRequest().body("L'unité est obligatoire.");
        try {
            CampagneCollationnement campagne = service.creerCampagne(request.uniteId(), request.datePlanifiee(), request.membresCommission());
            return ResponseEntity.ok(campagne);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/comptage")
    public ResponseEntity<?> saisirComptage(@PathVariable Long id, @RequestBody ComptageRequest request, Authentication authentication) {
        ResponseEntity<?> refus = exigerAcces(authentication);
        if (refus != null) return refus;
        try {
            return ResponseEntity.ok(service.saisirComptage(id, request.lignes()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/cloturer")
    public ResponseEntity<?> cloturer(@PathVariable Long id, @RequestBody ClotureRequest request, Authentication authentication) {
        ResponseEntity<?> refus = exigerAcces(authentication);
        if (refus != null) return refus;
        try {
            return ResponseEntity.ok(service.cloturerCampagne(id, request.observationsGenerales()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** @return une réponse d'erreur (401/403) si l'appelant n'a pas accès, sinon {@code null}. */
    private static ResponseEntity<?> exigerAcces(Authentication authentication) {
        User u = (authentication != null && authentication.getPrincipal() instanceof User user) ? user : null;
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (!AccesDAUtil.estAutorise(u, false, FonctionDA.SC)) {
            return ResponseEntity.status(403).body("Le collationnement est réservé à la Division de l'Approvisionnement (Section Comptabilité).");
        }
        return null;
    }
}
