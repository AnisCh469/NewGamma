package tn.defense.gamma3.catalogue.domain;

/**
 * Code mode d'approvisionnement — CCF, document "4 INSTRUCTIONS DE CREATION MODIFICATION FT",
 * §2-4-8.
 */
public enum CodeModeApprovisionnement {
    /** GRC — De gré à gré / marché cadre. */
    GRC,
    /** ADO — Appel d'offre. */
    ADO,
    /** BDC — Bon de commande. */
    BDC,
    /** ADR — Achat direct par voie de régie. */
    ADR,
    /** MAX — Mode non encore défini. */
    MAX,
    /** LCE — Lettre de commande à l'étranger. */
    LCE
}
