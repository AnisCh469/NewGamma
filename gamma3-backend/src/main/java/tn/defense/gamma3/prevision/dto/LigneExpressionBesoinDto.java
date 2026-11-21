package tn.defense.gamma3.prevision.dto;

import lombok.Builder;
import tn.defense.gamma3.prevision.domain.DecisionCommission;
import tn.defense.gamma3.prevision.domain.ModeAcquisition;
import tn.defense.gamma3.prevision.domain.PrioriteBesoin;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record LigneExpressionBesoinDto(
        UUID id,
        UUID expressionBesoinId,
        UUID itemId,
        String itemNomenclature,
        String itemDesignation,
        Boolean nouveauBesoin,
        String designationHorsCatalogue,
        String referenceConstructeur,
        String equipementParent,
        String justificationTechnique,
        BigDecimal quantiteDemandee,
        PrioriteBesoin priorite,
        String prioriteLibelle,
        BigDecimal quantiteProposeeSEP,
        String avisSEP,
        BigDecimal quantiteRetenue,
        BigDecimal prixUnitaireEstime,
        ModeAcquisition modeAcquisitionRecommande,
        DecisionCommission decisionCommission,
        String decisionLibelle,
        String motifArbitrage,
        Boolean transfereAchat
) {
}
