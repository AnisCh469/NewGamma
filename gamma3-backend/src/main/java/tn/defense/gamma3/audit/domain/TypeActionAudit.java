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
    SAUVEGARDE_ECHEC
}
