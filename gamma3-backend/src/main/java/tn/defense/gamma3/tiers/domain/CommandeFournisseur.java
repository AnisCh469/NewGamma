package tn.defense.gamma3.tiers.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "commandes_fournisseurs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommandeFournisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_commande", nullable = false, unique = true)
    private String numeroCommande; // Ex: CMD-2026-104

    @Column(name = "date_commande", nullable = false)
    private LocalDate dateCommande;

    @Column(name = "montant_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal montantTotal;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    @ManyToOne
    @JoinColumn(name = "marche_id")
    private Marche marche; // Optionnel : peut être passé hors marché

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutCommandeFournisseur statut = StatutCommandeFournisseur.EN_ATTENTE;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
