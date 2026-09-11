package tn.defense.gamma3.catalogue.domain;

/**
 * Code de gestion — CCF, document "4 INSTRUCTIONS DE CREATION MODIFICATION FT", §2-4-3.
 * Contrainte de quantité affectée à un article pour limiter et contrôler ses commandes de
 * réapprovisionnement. À NE PAS confondre avec le code d'identification de l'article
 * (nomenclature) ni avec la référence fabricant.
 */
public enum CodeGestion {
    /** Clf — Commandes par lot fixe. */
    CLF,
    /** Clu — Commandes en nombre limité d'unités. */
    CLU,
    /** Ciu — Commandes en nombre illimité d'unités. */
    CIU,
    /** Cei — Commandes externes interdites (transactions restreintes entre magasins). */
    CEI,
    /** Csm — Commandes interdites si le stock minimum n'est pas atteint. */
    CSM,
    /** Clm — Commandes limitées, empêchant le dépassement du stock maximum. */
    CLM
}
