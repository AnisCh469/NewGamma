package tn.defense.gamma3.tiers.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.catalogue.domain.Item;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "plans_armement", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"unite_id", "item_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanArmement {

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
    @Builder.Default
    private BigDecimal quantiteType = BigDecimal.ZERO; // Dotation Plan d'Armement

    @Column(name = "quantite_reelle", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal quantiteReelle = BigDecimal.ZERO; // Actuellement en service à bord

    @Column(name = "quantite_virtuelle", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal quantiteVirtuelle = BigDecimal.ZERO;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
