package tn.defense.gamma3.reforme.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dossiers_reforme")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DossierReforme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_dossier", unique = true, nullable = false, length = 30)
    private String numeroDossier;

    @Column(name = "date_demande", nullable = false)
    private LocalDate dateDemande;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unite_id", nullable = false)
    private UniteUtilisatrice unite;

    @Column(nullable = false, length = 20)
    private String statut; // BROUILLON, SOUMIS, COMMISSION, TRAITE, REJETE

    @Column(columnDefinition = "TEXT")
    private String motif;

    // Métadonnées Commission de Réforme
    @Column(name = "decision_commission", length = 30)
    private String decisionCommission; // REFORME_COMPLETE, DECLASSEMENT, REPARATION, REJETE

    @Column(name = "date_commission")
    private LocalDate dateCommission;

    @Column(name = "membres_commission", columnDefinition = "TEXT")
    private String membresCommission;

    @Column(name = "observations_commission", columnDefinition = "TEXT")
    private String observationsCommission;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (statut == null) statut = "BROUILLON";
    }
}
