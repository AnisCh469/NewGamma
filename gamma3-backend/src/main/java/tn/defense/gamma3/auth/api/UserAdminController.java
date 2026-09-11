package tn.defense.gamma3.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.audit.domain.TypeActionAudit;
import tn.defense.gamma3.audit.service.JournalAuditService;
import tn.defense.gamma3.auth.api.dto.UserAdminDto;
import tn.defense.gamma3.auth.domain.FonctionUnite;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.repository.UserRepository;
import tn.defense.gamma3.stock.domain.Magasin;
import tn.defense.gamma3.stock.repository.MagasinRepository;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;
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
 *      existante, via un lien explicite (`User.unite`, sélectionné dans une liste déroulante) —
 *      PLUS la convention historique "matricule = code de l'unité" (elle ne permettait qu'UN
 *      SEUL compte par unité). Depuis la règle du 2026-09-04 ("chaque unité dispose de deux
 *      comptes"), chaque unité a un compte GESTIONNAIRE et un compte COMMANDANT distincts
 *      (voir FonctionUnite), avec des matricules libres. On ne crée jamais l'unité à la volée
 *      ici : il faut d'abord créer l'unité (Tiers > Unités), puis ses comptes de connexion.
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
    private final JournalAuditService journalAuditService;

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

    /**
     * Liste des Unités Utilisatrices, pour peupler le sélecteur "Unité" du formulaire de
     * création/modification d'un compte UNIT_USER (Gestionnaire ou Commandant).
     */
    @GetMapping("/unites")
    public ResponseEntity<?> listUnites(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Réservé aux administrateurs."));
        }
        List<UniteUtilisatrice> unites = uniteRepository.findAll(Sort.by(Sort.Direction.ASC, "code"));
        return ResponseEntity.ok(unites);
    }

    // ------------------------------------------------------------------
    // Création
    // ------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserAdminDto.CreateUserRequest request,
                                         Authentication authentication, HttpServletRequest httpRequest) {
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
        UniteUtilisatrice unite = null;
        FonctionUnite fonctionUnite = null;
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
            ResponseEntity<?> uniteError = validerUniteEtFonction(request.getUniteId(), request.getFonctionUnite(), null);
            if (uniteError != null) return uniteError;
            unite = uniteRepository.findById(request.getUniteId()).orElseThrow();
            fonctionUnite = request.getFonctionUnite();
        }

        User user = User.builder()
                .matricule(matricule)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .role(role)
                .magasin(magasin)
                .unite(unite)
                .fonctionUnite(fonctionUnite)
                .enabled(true)
                .build();

        try {
            User saved = userRepository.save(user);
            User admin = (User) authentication.getPrincipal();
            journalAuditService.enregistrer(admin.getMatricule(), admin.getFullName(), TypeActionAudit.COMPTE_CREE,
                    "Création du compte " + matricule + " (rôle " + role + ").", httpRequest.getRemoteAddr());
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
                                         Authentication authentication, HttpServletRequest httpRequest) {
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
        UniteUtilisatrice unite = null;
        FonctionUnite fonctionUnite = null;
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
            ResponseEntity<?> uniteError = validerUniteEtFonction(request.getUniteId(), request.getFonctionUnite(), id);
            if (uniteError != null) return uniteError;
            unite = uniteRepository.findById(request.getUniteId()).orElseThrow();
            fonctionUnite = request.getFonctionUnite();
        }

        user.setFullName(fullName);
        user.setRole(role);
        user.setMagasin(magasin);
        user.setUnite(unite);
        user.setFonctionUnite(fonctionUnite);

        User saved = userRepository.save(user);
        User admin = (User) authentication.getPrincipal();
        journalAuditService.enregistrer(admin.getMatricule(), admin.getFullName(), TypeActionAudit.COMPTE_MODIFIE,
                "Modification du compte " + saved.getMatricule() + " (rôle " + role + ").", httpRequest.getRemoteAddr());
        return ResponseEntity.ok(saved);
    }

    /**
     * Valide le couple (Unité, Fonction) d'un compte UNIT_USER : l'unité doit exister, la
     * fonction doit être renseignée, et AUCUN AUTRE compte actif ne doit déjà occuper cette
     * fonction pour cette unité (règle métier : un Gestionnaire ET un Commandant par unité,
     * jamais deux du même). `excludeUserId` exclut le compte en cours de modification de cette
     * dernière vérification (sinon un compte se bloquerait lui-même à chaque mise à jour).
     * Retourne une ResponseEntity d'erreur si invalide, ou null si tout est valide.
     */
    private ResponseEntity<?> validerUniteEtFonction(Long uniteId, FonctionUnite fonctionUnite, Long excludeUserId) {
        if (uniteId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Une Unité Utilisatrice doit être sélectionnée pour un compte UNIT_USER."));
        }
        if (uniteRepository.findById(uniteId).isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Unité Utilisatrice introuvable (id=" + uniteId + ")."));
        }
        if (fonctionUnite == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "La fonction (Gestionnaire ou Commandant) doit être précisée."));
        }
        boolean dejaOccupe = userRepository.findByUnite_IdAndFonctionUnite(uniteId, fonctionUnite)
                .filter(User::isEnabled)
                .filter(u -> excludeUserId == null || !u.getId().equals(excludeUserId))
                .isPresent();
        if (dejaOccupe) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Un compte " + fonctionUnite + " actif existe déjà pour cette unité. "
                            + "Bloquez-le d'abord si vous souhaitez le remplacer."));
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Blocage / déblocage
    // ------------------------------------------------------------------

    @PutMapping("/{id}/enabled")
    public ResponseEntity<?> setEnabled(@PathVariable Long id,
                                         @RequestBody UserAdminDto.SetEnabledRequest request,
                                         Authentication authentication, HttpServletRequest httpRequest) {
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
        journalAuditService.enregistrer(currentUser.getMatricule(), currentUser.getFullName(),
                request.isEnabled() ? TypeActionAudit.COMPTE_DEBLOQUE : TypeActionAudit.COMPTE_BLOQUE,
                (request.isEnabled() ? "Déblocage" : "Blocage") + " du compte " + saved.getMatricule() + ".",
                httpRequest.getRemoteAddr());
        return ResponseEntity.ok(saved);
    }

    // ------------------------------------------------------------------
    // Réinitialisation de mot de passe
    // ------------------------------------------------------------------

    @PutMapping("/{id}/password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id,
                                            @RequestBody UserAdminDto.ResetPasswordRequest request,
                                            Authentication authentication, HttpServletRequest httpRequest) {
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
        User admin = (User) authentication.getPrincipal();
        journalAuditService.enregistrer(admin.getMatricule(), admin.getFullName(), TypeActionAudit.MOT_DE_PASSE_REINITIALISE,
                "Réinitialisation du mot de passe du compte " + user.getMatricule() + ".", httpRequest.getRemoteAddr());
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
