package tn.defense.gamma3.prevision.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.defense.gamma3.auth.domain.FonctionDA;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.util.AccesDAUtil;
import tn.defense.gamma3.prevision.service.PrevisionService;

/**
 * Administration > Prévisions (Procédure P01 du CCF) — voir
 * docs/PROPOSITION_PREVISIONS_ET_COLLATIONNEMENT.md. Réservé à la Division de l'Approvisionnement,
 * cloisonné à la Section Étude et Prévision (SEP) — même patron que {@code ReceptionController}/
 * {@code ReformeController} (Phase B de docs/PROPOSITION_ROLES_SECTIONS_DA.md, voir
 * {@link AccesDAUtil}) : un ADMIN sans `fonctionDA` (ou taguée GEST) garde l'accès, un ADMIN
 * restreint à une autre section (SC, SCS…) est refusé.
 */
@RestController
@RequestMapping("/api/v1/previsions")
@RequiredArgsConstructor
public class PrevisionController {

    private final PrevisionService previsionService;

    @GetMapping
    public ResponseEntity<?> getAlertes(Authentication authentication) {
        User u = currentUser(authentication);
        if (u == null) return ResponseEntity.status(401).body("Authentification requise.");
        if (!AccesDAUtil.estAutorise(u, false, FonctionDA.SEP)) {
            return ResponseEntity.status(403).body("Les prévisions sont réservées à la Division de l'Approvisionnement (Section Étude et Prévision).");
        }
        return ResponseEntity.ok(previsionService.calculerAlertes());
    }

    private static User currentUser(Authentication authentication) {
        return (authentication != null && authentication.getPrincipal() instanceof User user) ? user : null;
    }
}
