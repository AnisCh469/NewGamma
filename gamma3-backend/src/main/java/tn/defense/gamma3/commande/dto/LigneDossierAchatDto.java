package tn.defense.gamma3.commande.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record LigneDossierAchatDto(
        UUID id,
        UUID dossierAchatId,
        UUID itemId,
        String itemNomenclature,
        String designation,
        String reference,
        BigDecimal quantiteCommandee,
        BigDecimal prixUnitaire,
        BigDecimal montantTotal,
        UUID sourceLigneBesoinId
) {
}
