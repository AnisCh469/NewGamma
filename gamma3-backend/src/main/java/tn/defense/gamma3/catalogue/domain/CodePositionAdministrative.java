package tn.defense.gamma3.catalogue.domain;

/**
 * Code de position administrative du matériel — CCF, document
 * "4 INSTRUCTIONS DE CREATION MODIFICATION FT", §2-4-6.
 */
public enum CodePositionAdministrative {
    /** An — Matériel d'allocation. */
    AN,
    /** Dc — Matériel dotation de configuration. */
    DC,
    /** Da — Matériel dotation d'armement. */
    DA,
    /** Sb — Matériel stock bord. */
    SB,
    /** Va — Matériel volant d'ateliers. */
    VA,
    /** Ps — Matériel spécifique (dépôts/dossiers d'unités techniques). */
    PS,
    /** Px — Position non encore définie. */
    PX
}
