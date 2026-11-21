package tn.defense.gamma3.commande.domain;

/**
 * Statut d'un dossier d'achat / commande (Procédure P02 du CCF).
 */
public enum StatutAchat {
    DOSSIER_CREE("Dossier Créé"),
    CONSULTATION_LANCEE("Consultation / Appel d'Offres Lancé"),
    OFFRES_DEPOUILLEES("Offres Dépouillées (Technique & Financier)"),
    COMMANDE_NOTIFIEE("Marché / Bon de Commande Notifié"),
    LIVRAISON_PARTIELLE("Livraison Partielle"),
    SOLDE("Soldé / Pris en Charge en Soute");

    private final String libelle;

    StatutAchat(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
