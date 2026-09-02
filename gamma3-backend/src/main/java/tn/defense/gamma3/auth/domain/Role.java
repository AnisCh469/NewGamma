package tn.defense.gamma3.auth.domain;

public enum Role {
    ADMIN,         // Administrateur système
    DA_MANAGER,    // Gestionnaire de la Division Approvisionnement (valide les articles)
    UNIT_USER      // Utilisateur d'une unité militaire (ex: DMEN)
}
