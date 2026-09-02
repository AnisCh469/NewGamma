package tn.defense.gamma3.stock.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.defense.gamma3.catalogue.domain.Item;

import java.math.BigDecimal;

@Entity
@Table(name = "stocks", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"item_id", "magasin_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(optional = false)
    @JoinColumn(name = "magasin_id", nullable = false)
    private Magasin magasin;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantite;

    @Column(length = 50)
    private String emplacement; // ex: Allée A, Étagère 3

    @Column(length = 50)
    private String rayon; // ex: Rayon A-3
}

