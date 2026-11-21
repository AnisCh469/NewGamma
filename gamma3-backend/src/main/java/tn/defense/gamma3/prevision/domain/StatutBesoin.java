package tn.defense.gamma3.prevision.domain;

/**
 * Statut du dossier d'expression de besoin dans le cycle annuel P01.
 */
public enum StatutBesoin {
    BROUILLON("Brouillon"),
    SOUMIS("Soumis à la DA (SEP)"),
    ETUDIE_SEP("Étudié et filtré par la SEP"),
    ARBITRE("Arbitré en Commission DA"),
    VALIDE_PADI("Validé PADI / EMAM"),
    CLOTURE("Clôturé");

    private final String libelle;

    StatutBesoin(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
