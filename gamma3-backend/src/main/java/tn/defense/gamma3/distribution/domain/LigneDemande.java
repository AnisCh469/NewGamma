package tn.defense.gamma3.distribution.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.catalogue.domain.Item;
import java.math.BigDecimal;

@Entity
@Table(name = "lignes_demande")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LigneDemande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "quantite_demandee", precision = 12, scale = 2, nullable = false)
    private BigDecimal quantiteDemandee;

    @Column(name = "quantite_accordee", precision = 12, scale = 2)
    private BigDecimal quantiteAccordee;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_id", nullable = false)
    @JsonIgnore
    private DemandeMateriel demande;
}
