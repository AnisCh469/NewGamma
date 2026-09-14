package tn.defense.gamma3.auth.domain;

/**
 * Fonction facultative d'un compte ADMIN au sein des sections de la Division
 * Approvisionnement (voir User.fonctionDA) — Phase A de
 * docs/PROPOSITION_ROLES_SECTIONS_DA.md, symétrique à {@link FonctionUnite} pour les comptes
 * UNIT_USER.
 *
 * CONTEXTE (2026-09-14) : le CCF distingue 10 groupes d'utilisateurs officiels (tableau
 * "Groupe d'utilisateur / désignation"), dont 7 sections internes à la DA que GAMMA 3 fusionne
 * aujourd'hui dans le seul rôle ADMIN. Cette Phase A pose les fondations (enum + champ) SANS
 * modifier le moindre contrôle d'accès — un compte ADMIN garde tous ses pouvoirs actuels, que
 * cette fonction soit renseignée ou non. Voir docs/AUDIT_CONFORMITE_CCF_2026-09-11.md §7 et
 * docs/PROPOSITION_ROLES_SECTIONS_DA.md pour l'analyse complète et le plan de déploiement en
 * 3 phases.
 *
 * N'a de sens que pour Role.ADMIN ; toujours null pour SUPER_ADMIN (superset volontairement
 * non catégorisé — voir §1.2 du document), DA_MANAGER (déjà l'équivalent du groupe MAG/SM) et
 * UNIT_USER (voir FonctionUnite).
 *
 * MAG/SM n'a pas d'entrée ici : ce groupe correspond déjà à Role.DA_MANAGER, un rôle à part
 * entière. CR (Chef de Rayon) et MCE (Magasinier Chargé Étagère), sous-niveaux internes à MAG,
 * ne sont volontairement PAS modélisés — voir §1.3 du document : répliquer la chaîne de
 * signatures papier interne à une section serait de l'over-engineering pour un logiciel.
 */
public enum FonctionDA {
    /** Gestionnaire (chef SGS / CSGS dans le texte du CCF) — supervision transverse de la
     *  gestion des stocks. Valeur explicite assignable, équivalente en pratique à {@code null}
     *  (aucune restriction), conservée pour la traçabilité du Journal d'Audit. */
    GEST,

    /** Aire de Distribution (chef ADEST / CAD) — remise physique du matériel au bénéficiaire,
     *  gestion des annulations de livraison non retirée. */
    ADEST,

    /** Section Comptabilité (SC / CSC) — comptabilité matière, sortie des comptes du matériel
     *  réformé, collationnement annuel (Procédure P07). */
    SC,

    /** Section Commande et Suivi (SCS / CSCS) — cycle de vie administratif d'une commande
     *  (Procédure P02, hors périmètre GAMMA 3 actuel — voir l'audit CCF). */
    SCS,

    /** Section Étude et Prévision (SEP / CSEP) — prévisions de réapprovisionnement
     *  (Procédure P01), évaluation technique des offres et nomenclatures (P02). */
    SEP,

    /** Section Réforme et Remise (acronyme officiel SRR dans le texte des procédures, "SeRef"
     *  dans le tableau des groupes) — réforme/déclassement du matériel (P06) ET
     *  remise/reversement du matériel (P09). */
    SRR,

    /** Section Réception (acronyme officiel SRE dans le texte des procédures, "SR" dans le
     *  tableau des groupes) — réception et prise en charge du matériel (P03). */
    SRE
}
