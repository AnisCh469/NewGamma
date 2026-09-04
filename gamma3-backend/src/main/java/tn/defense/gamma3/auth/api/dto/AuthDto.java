package tn.defense.gamma3.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.FonctionUnite;

public class AuthDto {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LoginRequest {
        private String matricule;
        private String password;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AuthResponse {
        private String token;
        private String matricule;
        private String fullName;
        private Role role;
        private boolean requires2fa;

        // ─── Rattachement Unité (pour Role.UNIT_USER) ──────────────────────────
        // RÔLE : Le frontend n'a plus le droit de déduire l'unité d'un compte depuis son
        //        matricule (voir User.unite) — il a désormais besoin de le savoir dès la
        //        connexion, sans requête supplémentaire. NULL pour ADMIN/DA_MANAGER.
        private Long uniteId;
        private String uniteCode;
        private FonctionUnite fonctionUnite;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RegisterRequest {
        private String matricule;
        private String password;
        private String fullName;
        private Role role;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Verify2FaRequest {
        private String matricule;
        private String code;
    }
}
