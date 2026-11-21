package tn.defense.gamma3.prevision.dto;

import lombok.Builder;
import tn.defense.gamma3.prevision.domain.PrioriteBesoin;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record SyntheseConsolideeDto(
        UUID itemId,
        String nomenclature,
        String designation,
        boolean nouveauBesoin,
        String referenceConstructeur,
        BigDecimal totalQuantiteDemandee,
        long nombreUnitesDemandeuses,
        BigDecimal stockActuel,
        BigDecimal quantiteReservee,
        BigDecimal stockPotentiel,
        BigDecimal consommationMoyenneAnnuelle,
        BigDecimal seuilAlerte,
        BigDecimal deficitCalcule,
        BigDecimal quantiteRecommandeeAchat,
        PrioriteBesoin prioriteLaPlusHaute
) {
}
