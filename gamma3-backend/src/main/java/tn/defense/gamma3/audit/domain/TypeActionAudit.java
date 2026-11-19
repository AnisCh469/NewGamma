package tn.defense.gamma3.audit.domain;

/**
 * Catégories d'événements consignés dans le journal d'audit ({@link JournalAudit}).
 * Volontairement une liste ouverte mais typée (plutôt qu'un texte libre) pour permettre de
 * filtrer/statistiquer le journal de façon fiable.
 */
public enum TypeActionAudit {
    CONNEXION,
    CONNEXION_ECHEC,
    DECONNEXION_MANUELLE,
    DECONNEXION_INACTIVITE,
    DECONNEXION_FERMETURE,
    PARAMETRE_MODIFIE,
    COMPTE_CREE,
    COMPTE_MODIFIE,
    COMPTE_BLOQUE,
    COMPTE_DEBLOQUE,
    MOT_DE_PASSE_REINITIALISE,
    /** Sauvegarde de la base de données déclenchée avec succès (voir module {@code sauvegarde}). */
    SAUVEGARDE_DECLENCHEE,
    /** Tentative de sauvegarde ayant échoué (pg_dump indisponible, conteneur arrêté, timeout…). */
    SAUVEGARDE_ECHEC,
    /** Ligne de Plan d'Armement créée/modifiée/supprimée (audit CCF 2026-09-11 — voir
     *  PlanArmementController, correctif de la faille de contrôle de rôle absent). */
    PLAN_ARMEMENT_CREE,
    PLAN_ARMEMENT_MODIFIE,
    PLAN_ARMEMENT_SUPPRIME,
    /** Unité Utilisatrice (Client) créée/modifiée/supprimée (Super Admin). */
    UNITE_CREEE,
    UNITE_MODIFIEE,
    UNITE_SUPPRIMEE,
    /** Fournisseur créé/modifié/suspendu/réactivé (Super Admin). */
    FOURNISSEUR_CREE,
    FOURNISSEUR_MODIFIE,
    FOURNISSEUR_SUSPENDU,
    FOURNISSEUR_REACTIVE,
    FOURNISSEUR_SUPPRIME,
    /** Un ADMIN/SUPER_ADMIN a autorisé une sortie physique alors que le stock résultant
     *  passait sous le seuil de sécurité de l'article (CCF RG16/RG17/RG68/RG102 — équivalent
     *  numérique de "l'ordre de l'EMAM/BL" requis pour distribuer au-delà de ce seuil). Voir
     *  DistributionService.confirmerSortiePhysique. */
    SEUIL_SECURITE_DEROGATION
}
