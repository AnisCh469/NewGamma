package tn.defense.gamma3.prevision.domain;

/**
 * Priorité d'un besoin exprimé (Procédure P01 du CCF).
 */
public enum PrioriteBesoin {
    PRIORITE_1("Priorité 1 : Immédiat / Opérationnel"),
    PRIORITE_2("Priorité 2 : Maintenance programmée"),
    PRIORITE_3("Priorité 3 : Confort / Amélioration");

    private final String libelle;

    PrioriteBesoin(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
