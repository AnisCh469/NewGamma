package tn.defense.gamma3.catalogue.domain;

/**
 * Code origine du matériel — CCF, document "4 INSTRUCTIONS DE CREATION MODIFICATION FT",
 * §2-4-9.
 */
public enum CodeOrigineMateriel {
    /** AFn — Acquisition auprès du marché local. */
    AFN,
    /** AFe — Acquisition auprès du fournisseur d'origine. */
    AFE,
    /** FMs — Acquisition auprès de l'armée américaine (Foreign Military Sales). */
    FMS,
    /** DOc — Don / convention. */
    DOC,
    /** CL1 — Confection ou fabrication dans les ateliers de l'Armée de Mer. */
    CL1,
    /** CL2 — Confection ou fabrication auprès d'autres directions du MDN. */
    CL2,
    /** CL3 — Confection ou fabrication auprès de fournisseurs du marché local. */
    CL3,
    /** CL4 — Confection ou fabrication auprès de fournisseurs à l'étranger. */
    CL4,
    /** PR1 — Prêt à titre provisoire. */
    PR1,
    /** AD1 — Dislocation. */
    AD1,
    /** AD2 — Montage ou assemblage. */
    AD2,
    /** AX1 — Origine du matériel indéfinie. */
    AX1
}
