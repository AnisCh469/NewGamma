package tn.defense.gamma3.commande.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.catalogue.domain.Item;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ligne d'un dossier de commande / marché d'achat (Procédure P02 du CCF).
 */
@Entity
@Table(name = "lignes_dossiers_achats", indexes = {
        @Index(name = "idx_ligne_achat_dossier", columnList = "dossier_achat_id"),
        @Index(name = "idx_ligne_achat_item", columnList = "item_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneDossierAchat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_achat_id", nullable = false)
    @JsonBackReference
    private DossierAchat dossierAchat;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "designation", nullable = false, length = 255)
    private String designation;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "quantite_commandee", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantiteCommandee;

    @Column(name = "prix_unitaire", precision = 15, scale = 3)
    private BigDecimal prixUnitaire;

    @Column(name = "montant_total", precision = 15, scale = 3)
    private BigDecimal montantTotal;

    @Column(name = "source_ligne_besoin_id")
    private UUID sourceLigneBesoinId;
}
