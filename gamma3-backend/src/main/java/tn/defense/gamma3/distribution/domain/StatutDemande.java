package tn.defense.gamma3.distribution.domain;

/**
 * RÔLE : Cycle de vie complet d'une demande de matériel.
 *
 * DOUBLE VÉRIFICATION AU SEIN DE L'UNITÉ (règle métier validée le 2026-09-04) :
 *   Chaque unité dispose de deux comptes distincts : un GESTIONNAIRE qui compose et soumet
 *   la demande, et un COMMANDANT (chef d'unité) qui la valide ou la refuse AVANT qu'elle
 *   n'atteigne le gestionnaire GAMMA3 (DA_MANAGER/ADMIN). Tant que le Commandant n'a pas
 *   validé, la demande est INVISIBLE de GAMMA3 (aucun routage de magasin, aucune réservation
 *   sur le Plan d'Armement, aucune notification au DA_MANAGER).
 *
 * FLUX NORMAL :
 *   EN_ATTENTE_VALIDATION_COMMANDANT → SOUMIS → EN_COURS_ARBITRAGE → APPROUVE_TOTAL → LIVRE
 *
 * FLUX DE REFUS PAR LE COMMANDANT (jamais transmis à GAMMA3) :
 *   EN_ATTENTE_VALIDATION_COMMANDANT → REFUSE_COMMANDANT
 *
 * FLUX D'ESCALADE (stock local insuffisant, après validation du Commandant) :
 *   SOUMIS → EN_COURS_ARBITRAGE → ESCALADE_SGS → APPROUVE_TOTAL → LIVRE
 *
 * FLUX DE REFUS PAR GAMMA3 (après validation du Commandant) :
 *   SOUMIS → EN_COURS_ARBITRAGE → REFUSE
 */
public enum StatutDemande {
    /** Créée par le Gestionnaire de l'unité. En attente de validation par le Commandant
     *  (chef d'unité) de la MÊME unité. Invisible du DA_MANAGER/ADMIN à ce stade. */
    EN_ATTENTE_VALIDATION_COMMANDANT,

    /** Refusée par le Commandant AVANT envoi à GAMMA3 (voir `motifRefus`). N'atteint jamais
     *  le DA_MANAGER ni l'ADMIN. Visible uniquement par les comptes de l'unité concernée. */
    REFUSE_COMMANDANT,

    /** Validée par le Commandant, transmise à GAMMA3. En attente de prise en charge. */
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

    /** Refusée par le DA_MANAGER ou l'ADMIN (après validation par le Commandant). */
    REFUSE,

    /** Retrait physique confirmé. Matériel en service à bord de l'unité. */
    LIVRE
}
