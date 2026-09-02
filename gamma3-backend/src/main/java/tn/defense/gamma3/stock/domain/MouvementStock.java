package tn.defense.gamma3.stock.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.auth.domain.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mouvements_stock")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MouvementStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String referenceBon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeMouvement typeMouvement;

    @ManyToOne(optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(optional = false)
    @JoinColumn(name = "magasin_id", nullable = false)
    private Magasin magasin;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantite;

    @Column(nullable = false)
    private LocalDateTime dateMouvement;

    @Column(length = 255)
    private String motif;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cree_par", nullable = false)
    private User creePar;
}
