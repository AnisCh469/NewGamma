package tn.defense.gamma3.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.FonctionUnite;

/**
 * DTOs de la console d'administration des comptes utilisateurs (voir UserAdminController).
 * Séparés de AuthDto : ces requêtes ne concernent jamais l'appelant lui-même (self-service
 * login/2FA) mais un compte TIERS géré par un ADMIN.
 */
public class UserAdminDto {

    /** Création d'un compte. `magasinId` requis si role=DA_MANAGER ; `uniteId`+`fonctionUnite`
     *  requis si role=UNIT_USER (voir FonctionUnite : chaque unité a un Gestionnaire ET un
     *  Commandant, deux comptes distincts) ; ignorés sinon. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateUserRequest {
        private String matricule;
        private String password;
        private String fullName;
        private Role role;
        private Long magasinId;
        private Long uniteId;
        private FonctionUnite fonctionUnite;
    }

    /** Mise à jour d'un compte existant (matricule et mot de passe non modifiables ici). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateUserRequest {
        private String fullName;
        private Role role;
        private Long magasinId;
        private Long uniteId;
        private FonctionUnite fonctionUnite;
    }

    /** Blocage / déblocage d'un compte (voir User.enabled). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetEnabledRequest {
        private boolean enabled;
    }

    /** Réinitialisation du mot de passe d'un compte par un ADMIN. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetPasswordRequest {
        private String newPassword;
    }
}
