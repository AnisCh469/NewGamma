package tn.defense.gamma3.auth.domain;

public enum Role {
    /**
     * Administrateur SYSTÈME (proposition 2026-09-11, docs/PROPOSITION_GOUVERNANCE_ROLES.md) :
     * gestion des comptes/rôles, paramètres système, sauvegarde, migrations, journal d'audit.
     * Hiérarchiquement au-dessus de ADMIN : peut aussi tout ce que ADMIN peut, mais ADMIN ne
     * peut PAS ce que SUPER_ADMIN peut (séparation « ce qui gouverne le système » vs
     * « ce qui gouverne la logistique quotidienne » — voir .antigravityrules, séparation des
     * privilèges).
     */
    SUPER_ADMIN,
    ADMIN,         // Administrateur logistique (SGS) : arbitrage des demandes, supervision des magasins
    DA_MANAGER,    // Gestionnaire de la Division Approvisionnement (valide les articles)
    UNIT_USER      // Utilisateur d'une unité militaire (ex: DMEN)
}
