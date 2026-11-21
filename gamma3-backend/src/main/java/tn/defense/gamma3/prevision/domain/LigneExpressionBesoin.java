package tn.defense.gamma3.prevision.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.catalogue.domain.Item;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ligne d'un dossier de besoin (Article existant ou Nouveau Besoin F01 hors-catalogue).
 */
@Entity
@Table(name = "lignes_expression_besoin", indexes = {
        @Index(name = "idx_ligne_exp_item", columnList = "item_id"),
        @Index(name = "idx_ligne_exp_dossier", columnList = "expression_besoin_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneExpressionBesoin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expression_besoin_id", nullable = false)
    @JsonBackReference
    private ExpressionBesoin expressionBesoin;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id", nullable = true)
    private Item item;

    // --- Champs Nouveau Besoin hors-catalogue (Modèle F01/P01/DA/2011) ---
    @Column(name = "nouveau_besoin", nullable = false)
    @Builder.Default
    private boolean nouveauBesoin = false;

    @Column(name = "designation_hors_catalogue", length = 255)
    private String designationHorsCatalogue;

    @Column(name = "reference_constructeur", length = 100)
    private String referenceConstructeur;

    @Column(name = "equipement_parent", length = 150)
    private String equipementParent;

    @Column(name = "justification_technique", columnDefinition = "TEXT")
    private String justificationTechnique;

    // --- Expression initiale de l'unité ---
    @Column(name = "quantite_demandee", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantiteDemandee;

    @Enumerated(EnumType.STRING)
    @Column(name = "priorite", nullable = false, length = 20)
    @Builder.Default
    private PrioriteBesoin priorite = PrioriteBesoin.PRIORITE_2;

    // --- Étape Étude / Filtrage SEP (OP2 / OP4) ---
    @Column(name = "quantite_proposee_sep", precision = 12, scale = 2)
    private BigDecimal quantiteProposeeSEP;

    @Column(name = "avis_sep", columnDefinition = "TEXT")
    private String avisSEP;

    // --- Étape Arbitrage Commission DA (OP5) ---
    @Column(name = "quantite_retenue", precision = 12, scale = 2)
    private BigDecimal quantiteRetenue;

    @Column(name = "prix_unitaire_estime", precision = 15, scale = 3)
    private BigDecimal prixUnitaireEstime;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_acquisition_recommande", length = 30)
    private ModeAcquisition modeAcquisitionRecommande;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_commission", length = 20)
    @Builder.Default
    private DecisionCommission decisionCommission = DecisionCommission.EN_ATTENTE;

    @Column(name = "motif_arbitrage", columnDefinition = "TEXT")
    private String motifArbitrage;

    @Column(name = "transfere_achat", nullable = false)
    @Builder.Default
    private boolean transfereAchat = false;
}
