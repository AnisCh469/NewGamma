package tn.defense.gamma3.catalogue.domain;

/**
 * Code Type de matériel — CCF, document "4 INSTRUCTIONS DE CREATION MODIFICATION FT",
 * tableau 2-4-2. Classe la nature fonctionnelle d'un article (matériel complet, sous-ensemble,
 * pièce de rechange spécifique/commune, outillage…), indépendamment de sa classification
 * (classe/sous-classe/nomenclature) et de son type de consommabilité.
 */
public enum CodeTypeMateriel {
    /** MC — Matériel complet formant une entité technique composée et complète. */
    MC,
    /** EQ — Équipement ou instrument assurant une fonction autonome, non un matériel complet. */
    EQ,
    /** BE — Bancs d'essai, de contrôle, appareils de mesure et de calibration. */
    BE,
    /** DT — Documents techniques. */
    DT,
    /** PS — Sous-ensemble de rechange, indissociable en gestion (nomenclature propre). */
    PS,
    /** PR — Pièce de rechange spécifique à un matériel donné (peculiar item). */
    PR,
    /** PC — Pièce de rechange commune à plusieurs matériels (common item). */
    PC,
    /** AC — Accessoire pour un matériel complet ou un équipement. */
    AC,
    /** OD — Outillage standard à usage commun. */
    OD,
    /** OS — Outillage spécifique à un matériel ou équipement donné. */
    OS,
    /** MP — Matière première. */
    MP,
    /** MS — Matière première dangereuse (acquisition soumise à autorisation). */
    MS
}
