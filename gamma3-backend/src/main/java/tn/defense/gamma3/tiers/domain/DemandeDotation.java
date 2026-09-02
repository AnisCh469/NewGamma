package tn.defense.gamma3.tiers.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.catalogue.domain.Item;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "demandes_dotation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeDotation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unite_id", nullable = false)
    private UniteUtilisatrice unite;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "quantite_type", precision = 12, scale = 2, nullable = false)
    private BigDecimal quantiteType; // Quantité dotation demandée

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "SOUMISE"; // SOUMISE, APPROUVEE, REJETEE

    @Column(name = "motif_refus", columnDefinition = "TEXT")
    private String motifRefus;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.statut == null) this.statut = "SOUMISE";
    }
}
