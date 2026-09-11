package tn.defense.gamma3.catalogue.domain;

/**
 * Code de soutien du matériel — CCF, document "4 INSTRUCTIONS DE CREATION MODIFICATION FT",
 * §2-4-5. Pilote une règle de gestion directe (Procédure P01, RG8) : le seuil d'alerte (Sa)
 * d'un article en soutien ARRETE ou SUSPENDU est forcé à 0 ("stock mort").
 */
public enum CodeSoutien {
    /** S10 — Soutien normé : toutes les conditions de soutien sont réunies (documentation,
     *  sources d'acquisition, parc suffisant, crédit annuel dédié). */
    S10,
    /** S20 — Soutien restreint : conditions normées réunies sauf le crédit annuel ou le
     *  nombre de matériels. */
    S20,
    /** S30 — Soutien spécial : maintien en condition sous-traité à l'industrie civile. */
    S30,
    /** S40 — Soutien suspendu : crédit normalement nécessaire suspendu (position transitoire). */
    S40,
    /** S50 — Soutien arrêté : matériel en voie d'élimination. */
    S50
}
