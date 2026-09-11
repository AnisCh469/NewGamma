package tn.defense.gamma3.catalogue.domain;

/**
 * Statut Critique / Non-Critique (Cr/NCr) — CCF, document "4 INSTRUCTIONS DE CREATION
 * MODIFICATION FT", modèle de fiche signalétique (§2-3), champ "Statut : Cr/NCr".
 *
 * Correspond au statut "Essentiel" (Es) décrit dans le document "3 RECEUIL DES PROCEDURES ET
 * REGLES DE GESTION" (Procédure P01, RG13-RG15, RG20) : "tout matériel dont le manque peut
 * causer l'indisponibilité opérationnelle de l'unité, ou compromettre sa sécurité ou la
 * sécurité du personnel". Un article CRITIQUE se voit calculer un seuil de stock minimal
 * (Smin) et un seuil de sécurité (Ss) — voir la proposition pour la Procédure P01, non encore
 * implémentée (docs/AUDIT_CONFORMITE_CCF_2026-09-11.md). RG20 : une division technique peut
 * attribuer ce statut à un article mais ne peut pas le décliner seule.
 */
public enum Criticite {
    CRITIQUE,
    NON_CRITIQUE
}
