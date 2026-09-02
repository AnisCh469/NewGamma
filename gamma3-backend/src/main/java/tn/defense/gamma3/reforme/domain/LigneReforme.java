package tn.defense.gamma3.reforme.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.catalogue.domain.Item;

import java.math.BigDecimal;

@Entity
@Table(name = "lignes_reforme")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LigneReforme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_reforme_id", nullable = false)
    private DossierReforme dossierReforme;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantite;

    @Column(columnDefinition = "TEXT")
    private String observations;
}
