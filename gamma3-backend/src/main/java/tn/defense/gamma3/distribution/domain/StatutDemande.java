package tn.defense.gamma3.distribution.domain;

/**
 * RÔLE : Cycle de vie complet d'une demande de matériel.
 *
 * FLUX NORMAL :
 *   SOUMIS → EN_COURS_ARBITRAGE → APPROUVE_TOTAL → LIVRE
 *
 * FLUX D'ESCALADE (stock local insuffisant) :
 *   SOUMIS → EN_COURS_ARBITRAGE → ESCALADE_SGS → APPROUVE_TOTAL → LIVRE
 *
 * FLUX DE REFUS :
 *   SOUMIS → EN_COURS_ARBITRAGE → REFUSE
 */
public enum StatutDemande {
    /** Demande créée et soumise par l'unité cliente. En attente de prise en charge. */
    SOUMIS,

    /** Prise en charge par le DA_MANAGER du magasin désigné. Arbitrage en cours. */
    EN_COURS_ARBITRAGE,

    /** Stock local du magasin désigné insuffisant.
     *  Demande remontée à l'ADMIN (SGS) pour validation d'un transfert inter-magasin. */
    ESCALADE_SGS,

    /** Approuvée partiellement (quantité accordée < quantité demandée). */
    APPROUVE_PARTIEL,

    /** Approuvée totalement. Bon de sortie généré, en attente de retrait physique. */
    APPROUVE_TOTAL,

    /** Refusée par le DA_MANAGER ou l'ADMIN. */
    REFUSE,

    /** Retrait physique confirmé. Matériel en service à bord de l'unité. */
    LIVRE
}
