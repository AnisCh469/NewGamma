package tn.defense.gamma3.auth.api;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.api.dto.UserAdminDto;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.repository.UserRepository;
import tn.defense.gamma3.stock.domain.Magasin;
import tn.defense.gamma3.stock.repository.MagasinRepository;
import tn.defense.gamma3.tiers.repository.UniteUtilisatriceRepository;

import java.util.List;
import java.util.Map;

/**
 * Console d'administration des comptes utilisateurs GAMMA 3.0.
 *
 * Contexte (voir DEV_JOURNAL.md, entrée sécurité du 2026-09-04) : la création de comptes
 * via /api/v1/auth/register est désormais réservée à un ADMIN authentifié (correctif de
 * sécurité). Ce contrôleur fournit l'interface d'administration correspondante, conforme
 * aux 3 décisions de conception validées par le client :
 *
 *   1) Un compte UNIT_USER doit obligatoirement être rattaché à une Unité Utilisatrice déjà
 *      existante : son matricule doit être STRICTEMENT ÉGAL au code d'une UniteUtilisatrice
 *      déjà créée (écran Tiers > Unités). On ne crée jamais l'unité à la volée ici : il faut
 *      d'abord créer l'unité, puis son compte de connexion.
 *   2) Seul un ADMIN peut créer, modifier, bloquer/débloquer ou réinitialiser le mot de passe
 *      d'un compte (aucune auto-gestion, aucune délégation à un DA_MANAGER).
 *   3) La désactivation d'un compte est un BLOCAGE réversible (User.enabled = false), jamais
 *      une suppression : l'historique (mouvements de stock, demandes d'approvisionnement,
 *      etc. créés par ce compte) reste intact et traçable.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserRepository userRepository;
    private final UniteUtilisatriceRepository uniteRepository;
    private final MagasinRepository magasinRepository;
    private final PasswordEncoder passwordEncoder;

    private static final int MIN_PASSWORD_LENGTH = 4;

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<?> listUsers(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Réservé aux administrateurs."));
        }
        List<User> users = userRepository.findAll(Sort.by(Sort.Direction.ASC, "matricule"));
        return ResponseEntity.ok(users);
    }

    /**
     * Liste des Magasins, pour peupler le sélecteur "Magasin" du formulaire de création/
     * modification d'un compte DA_MANAGER (aucun autre écran de GAMMA 3 n'expose encore les
     * Magasins : ils ne sont référencés jusqu'ici que par code, en dur, dans le code métier).
     */
    @GetMapping("/magasins")
    public ResponseEntity<?> listMagasins(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Réservé aux administrateurs."));
        }
        List<Magasin> magasins = magasinRepository.findAll(Sort.by(Sort.Direction.ASC, "code"));
        return ResponseEntity.ok(magasins);
    }

    // ------------------------------------------------------------------
    // Création
    // ------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserAdminDto.CreateUserRequest request,
                                         Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Réservé aux administrateurs."));
        }

        String matricule = request.getMatricule() == null ? "" : request.getMatricule().trim();
        String password = request.getPassword();
        String fullName = request.getFullName() == null ? "" : request.getFullName().trim();
        Role role = request.getRole();

        if (matricule.isBlank() || fullName.isBlank() || role == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Matricule, nom complet et rôle sont obligatoires."));
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Le mot de passe doit contenir au moins "
                            + MIN_PASSWORD_LENGTH + " caractères."));
        }

        Magasin magasin = null;
        if (role == Role.DA_MANAGER) {
            if (request.getMagasinId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Un Magasin doit être sélectionné pour un compte DA_MANAGER."));
            }
            magasin = magasinRepository.findById(request.getMagasinId()).orElse(null);
            if (magasin == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Magasin introuvable (id=" + request.getMagasinId() + ")."));
            }
        } else if (role == Role.UNIT_USER) {
            boolean uniteExiste = uniteRepository.findByCode(matricule).isPresent();
            if (!uniteExiste) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Aucune Unité Utilisatrice avec le code '" + matricule
                                + "'. Créez d'abord l'unité (Tiers > Unités) avant de créer son compte."));
            }
        }

        User user = User.builder()
                .matricule(matricule)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .role(role)
                .magasin(magasin)
                .enabled(true)
                .build();

        try {
            User saved = userRepository.save(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Un compte avec le matricule '" + matricule + "' existe déjà."));
        }
    }

    // ------------------------------------------------------------------
    // Mise à jour
    // ------------------------------------------------------------------

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id,
                                         @RequestBody UserAdminDto.UpdateUserRequest request,
                                         Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Réservé aux administrateurs."));
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Compte introuvable (id=" + id + ")."));
        }

        String fullName = request.getFullName() == null ? "" : request.getFullName().trim();
        Role role = request.getRole();
        if (fullName.isBlank() || role == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Nom complet et rôle sont obligatoires."));
        }

        Magasin magasin = null;
        if (role == Role.DA_MANAGER) {
            if (request.getMagasinId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Un Magasin doit être sélectionné pour un compte DA_MANAGER."));
            }
            magasin = magasinRepository.findById(request.getMagasinId()).orElse(null);
            if (magasin == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Magasin introuvable (id=" + request.getMagasinId() + ")."));
            }
        } else if (role == Role.UNIT_USER) {
            boolean uniteExiste = uniteRepository.findByCode(user.getMatricule()).isPresent();
            if (!uniteExiste) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Aucune Unité Utilisatrice avec le code '" + user.getMatricule()
                                + "'. Ce compte ne peut pas être repassé en UNIT_USER."));
            }
        }

        user.setFullName(fullName);
        user.setRole(role);
        user.setMagasin(magasin);

        User saved = userRepository.save(user);
        return ResponseEntity.ok(saved);
    }

    // ------------------------------------------------------------------
    // Blocage / déblocage
    // ------------------------------------------------------------------

    @PutMapping("/{id}/enabled")
    public ResponseEntity<?> setEnabled(@PathVariable Long id,
                                         @RequestBody UserAdminDto.SetEnabledRequest request,
                                         Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Réservé aux administrateurs."));
        }

        User currentUser = (User) authentication.getPrincipal();
        if (currentUser.getId().equals(id) && !request.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Vous ne pouvez pas bloquer votre propre compte."));
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Compte introuvable (id=" + id + ")."));
        }

        user.setEnabled(request.isEnabled());
        User saved = userRepository.save(user);
        return ResponseEntity.ok(saved);
    }

    // ------------------------------------------------------------------
    // Réinitialisation de mot de passe
    // ------------------------------------------------------------------

    @PutMapping("/{id}/password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id,
                                            @RequestBody UserAdminDto.ResetPasswordRequest request,
                                            Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Réservé aux administrateurs."));
        }

        String newPassword = request.getNewPassword();
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Le mot de passe doit contenir au moins "
                            + MIN_PASSWORD_LENGTH + " caractères."));
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Compte introuvable (id=" + id + ")."));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé."));
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof User u
                && u.getRole() == Role.ADMIN;
    }
}
