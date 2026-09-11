package tn.defense.gamma3.piecejointe.domain;

/**
 * Nature d'un document signé téléversé dans GAMMA 3 (voir {@link DocumentSigne}).
 * Correspond aux documents officiels générés par le système (voir
 * {@code tn.defense.gamma3.document}) : on imprime le PDF, on le fait signer/cacheter,
 * puis on téléverse le scan comme pièce justificative rattachée à l'enregistrement d'origine.
 */
public enum TypeDocumentSigne {
    BON_SORTIE,        // référence = id de la DemandeMateriel
    BON_ENTREE,        // référence = id du BonProvisoireReception
    PV_REFORME,        // référence = id du DossierReforme
    BON_COMMANDE,      // référence = id de la CommandeFournisseur
    FICHE_ARTICLE,     // référence = nomenclature (ou UUID) de l'article
    FICHE_FOURNISSEUR, // référence = id du Fournisseur
    FICHE_UNITE,       // référence = id de l'UniteUtilisatrice
    AUTRE
}
