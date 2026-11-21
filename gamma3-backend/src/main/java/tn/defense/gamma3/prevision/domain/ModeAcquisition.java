package tn.defense.gamma3.prevision.domain;

/**
 * Mode d'acquisition recommandé par la commission (Procédure P01 OP5 & P02).
 */
public enum ModeAcquisition {
    MARCHE_CADRE("Marché Cadre"),
    APPEL_OFFRES("Appel d'Offres"),
    BON_COMMANDE("Bon de Commande"),
    REGIE("Achat Direct par Régie"),
    FMS("FMS");

    private final String libelle;

    ModeAcquisition(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
