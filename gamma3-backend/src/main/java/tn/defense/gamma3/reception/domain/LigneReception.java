package tn.defense.gamma3.reception.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.catalogue.domain.Item;

import java.math.BigDecimal;

@Entity
@Table(name = "lignes_reception")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LigneReception {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "bpr_id", nullable = false)
    private BonProvisoireReception bpr;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "quantite_livree", precision = 12, scale = 2, nullable = false)
    private BigDecimal quantiteLivree;

    @Column(name = "prix_unitaire", precision = 12, scale = 3)
    private BigDecimal prixUnitaire;
}
