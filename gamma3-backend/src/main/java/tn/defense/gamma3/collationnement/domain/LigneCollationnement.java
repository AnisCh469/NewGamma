package tn.defense.gamma3.collationnement.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.catalogue.domain.Item;

import java.math.BigDecimal;

/**
 * Une ligne de campagne de collationnement : un article du Plan d'Armement de l'unité, avec sa
 * dotation théorique figée au lancement de la campagne (`quantiteTheorique`, snapshot — voir
 * javadoc de {@link CampagneCollationnement}) et le constat physique relevé par la commission
 * (`quantitePhysiqueConstatee`, {@code null} tant que non comptée).
 */
@Entity
@Table(name = "lignes_collationnement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LigneCollationnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campagne_id", nullable = false)
    @JsonIgnore
    private CampagneCollationnement campagne;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** Dotation théorique selon le système (Plan d'Armement) au lancement de la campagne. */
    @Column(name = "quantite_theorique", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantiteTheorique;

    /** Constat physique de la commission — {@code null} tant que non encore compté. */
    @Column(name = "quantite_physique_constatee", precision = 12, scale = 2)
    private BigDecimal quantitePhysiqueConstatee;

    @Column(columnDefinition = "TEXT")
    private String observations;

    /** Écart = physique − théorique (positif = excédent/M16, négatif = déficit/C5). Calculé à
     *  la volée, jamais stocké — dérive toujours de {@code quantitePhysiqueConstatee}. */
    @Transient
    public BigDecimal getEcart() {
        return quantitePhysiqueConstatee == null ? null : quantitePhysiqueConstatee.subtract(quantiteTheorique);
    }
}
