package tn.defense.gamma3.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.auth.domain.FonctionUnite;
import tn.defense.gamma3.auth.domain.FonctionDA;

/**
 * Objets de Transfert de Données (DTO) pour le module d'authentification et de session.
 * Centralise les requêtes de connexion, validation TOTP 2FA et la réponse d'habilitation complète.
 */
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

        // ─── Rattachement Section DA (pour Role.ADMIN) ─────────────────────────
        // RÔLE : Indique la section spécialisée de la Direction de l'Approvisionnement
        //        (SRR, SRE, SEP, SC, SCS, GEST, ADEST). Permet au frontend de respecter
        //        le principe Zero-Trust UI en masquant les modules hors habilitation.
        private FonctionDA fonctionDA;
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
