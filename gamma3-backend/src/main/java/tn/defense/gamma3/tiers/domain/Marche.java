package tn.defense.gamma3.tiers.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "marches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Marche {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_marche", nullable = false, unique = true)
    private String numeroMarche; // Ex: M-2026-004

    @Column(nullable = false)
    private String designation;

    @Column(name = "montant_total_ht", nullable = false, precision = 19, scale = 4)
    private BigDecimal montantTotalHt;

    @Column(name = "montant_total_ttc", nullable = false, precision = 19, scale = 4)
    private BigDecimal montantTotalTtc;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutMarche statut = StatutMarche.ACTIF;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
