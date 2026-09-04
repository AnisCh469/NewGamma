package tn.defense.gamma3.auth.api;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.api.dto.AuthDto;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.service.AuthenticationService;

import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * PROTOCOLE DE DOCUMENTATION ÉDUCATIVE — Contrôleur d'Authentification
 * Classe : AuthenticationController
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * SÉCURITÉ (correctif 2026-09-04) : Seuls "/login" et "/verify-2fa" sont accessibles
 * sans authentification préalable (voir SecurityConfig) — ce sont les deux seules
 * étapes du protocole où l'appelant ne possède pas encore de jeton JWT. Tous les
 * autres endpoints ci-dessous exigent désormais un jeton JWT valide :
 *   - "/register" exigeait auparavant AUCUNE authentification et acceptait un champ
 *     "role" fourni tel quel par le client : n'importe qui pouvait donc créer un
 *     compte ADMIN sans jamais se connecter. Restreint désormais aux appelants
 *     authentifiés avec le rôle ADMIN (voir register()).
 *   - "/2fa/setup", "/2fa/enable", "/2fa/disable" acceptaient un paramètre
 *     "matricule" fourni par le client SANS vérifier qu'il correspondait à
 *     l'appelant : n'importe quel compte pouvait désactiver la double
 *     authentification d'un autre compte (y compris ADMIN). Le matricule est
 *     désormais dérivé du jeton JWT authentifié, jamais du paramètre client.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService service;

    /**
     * RÔLE :
     * Crée un nouveau compte utilisateur (matricule, mot de passe, rôle).
     *
     * POURQUOI CETTE LOGIQUE :
     * Créer un compte avec un rôle arbitraire (y compris ADMIN) est une opération
     * hautement privilégiée : seul un administrateur déjà authentifié peut
     * l'effectuer. Un doublon de matricule (contrainte unique en base) renvoie une
     * erreur 409 explicite plutôt qu'un plantage 500 brut.
     *
     * PARAMÈTRES :
     * @param request        matricule, mot de passe, nom complet et rôle du nouveau compte
     * @param authentication l'appelant authentifié, injecté par Spring Security
     *
     * RETOUR :
     * @return 200 + jeton du nouveau compte ; 403 si l'appelant n'est pas ADMIN ;
     *         409 si le matricule existe déjà.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody AuthDto.RegisterRequest request,
            Authentication authentication
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User currentUser) || currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Seul un administrateur peut créer un compte utilisateur."));
        }
        try {
            return ResponseEntity.ok(service.register(request));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Un compte avec le matricule '" + request.getMatricule() + "' existe déjà."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> authenticate(
            @RequestBody AuthDto.LoginRequest request
    ) {
        return ResponseEntity.ok(service.authenticate(request));
    }

    @PostMapping("/verify-2fa")
    public ResponseEntity<AuthDto.AuthResponse> verify2Fa(
            @RequestBody AuthDto.Verify2FaRequest request
    ) {
        try {
            return ResponseEntity.ok(service.verify2Fa(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * RÔLE :
     * Démarre la configuration de la double authentification (2FA) POUR LE COMPTE
     * ACTUELLEMENT CONNECTÉ — jamais pour un autre matricule fourni par le client
     * (voir note de sécurité en tête de fichier).
     */
    @PostMapping("/2fa/setup")
    public ResponseEntity<Map<String, String>> setup2Fa(Authentication authentication) {
        String secret = service.setup2Fa(((User) authentication.getPrincipal()).getMatricule());
        return ResponseEntity.ok(Map.of("secret", secret));
    }

    /** RÔLE : Active la 2FA pour le compte actuellement connecté, après vérification du code. */
    @PostMapping("/2fa/enable")
    public ResponseEntity<Void> enable2Fa(@RequestParam String code, Authentication authentication) {
        boolean enabled = service.enable2Fa(((User) authentication.getPrincipal()).getMatricule(), code);
        return enabled ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    /** RÔLE : Désactive la 2FA pour le compte actuellement connecté. */
    @PostMapping("/2fa/disable")
    public ResponseEntity<Void> disable2Fa(Authentication authentication) {
        service.disable2Fa(((User) authentication.getPrincipal()).getMatricule());
        return ResponseEntity.ok().build();
    }
}
