package tn.defense.gamma3.auth.domain;

/**
 * Fonction d'un compte UNIT_USER au sein de son unité (voir User.fonctionUnite).
 *
 * RÈGLE MÉTIER (validée par le client, 2026-09-04) : chaque unité navigante ou corps
 * dispose de DEUX comptes de connexion distincts, jamais un seul :
 *   - un GESTIONNAIRE qui compose et soumet les demandes de matériel,
 *   - un COMMANDANT (chef d'unité) qui valide ou refuse chaque demande AVANT qu'elle
 *     n'atteigne le gestionnaire GAMMA3 (DA_MANAGER/ADMIN) — une double vérification.
 *
 * N'a de sens que pour Role.UNIT_USER ; toujours null pour ADMIN/DA_MANAGER.
 */
public enum FonctionUnite {
    /** Compose et soumet les demandes de matériel de son unité. */
    GESTIONNAIRE,

    /** Valide ou refuse chaque demande soumise par le Gestionnaire de la même unité,
     *  avant transmission à GAMMA3 (DA_MANAGER/ADMIN). Ne crée pas de demande lui-même. */
    COMMANDANT
}
