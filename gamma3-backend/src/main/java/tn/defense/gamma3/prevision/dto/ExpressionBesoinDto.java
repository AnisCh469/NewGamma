package tn.defense.gamma3.prevision.dto;

import lombok.Builder;
import tn.defense.gamma3.prevision.domain.DecisionCommission;
import tn.defense.gamma3.prevision.domain.ModeAcquisition;
import tn.defense.gamma3.prevision.domain.PrioriteBesoin;
import tn.defense.gamma3.prevision.domain.StatutBesoin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record ExpressionBesoinDto(
        UUID id,
        String numeroDossier,
        Integer anneeBudgetaire,
        Long uniteId,
        String uniteCode,
        String uniteNom,
        String objet,
        String commentaires,
        StatutBesoin statut,
        String statutLibelle,
        LocalDateTime dateCreation,
        LocalDateTime dateSoumission,
        LocalDateTime dateArbitrage,
        String creeParNom,
        String arbitreParNom,
        List<LigneExpressionBesoinDto> lignes
) {
}
