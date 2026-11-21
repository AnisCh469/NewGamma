package tn.defense.gamma3.prevision.domain;

/**
 * Décision de la Commission d'arbitrage DA (Procédure P01 OP5).
 */
public enum DecisionCommission {
    EN_ATTENTE("En attente d'arbitrage"),
    ACCORDE("Accordé"),
    REJETE("Rejeté"),
    AJOURNE("Ajourné (Reporté)");

    private final String libelle;

    DecisionCommission(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
