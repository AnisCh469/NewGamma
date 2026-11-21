package tn.defense.gamma3.commande.dto;

import lombok.Builder;
import tn.defense.gamma3.commande.domain.StatutAchat;
import tn.defense.gamma3.prevision.domain.ModeAcquisition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record DossierAchatDto(
        UUID id,
        String numeroDossier,
        Integer annee,
        String objet,
        ModeAcquisition modeAcquisition,
        String modeAcquisitionLibelle,
        Long fournisseurId,
        String fournisseurNom,
        StatutAchat statut,
        String statutLibelle,
        String referenceMarcheOuBon,
        BigDecimal montantEstimeTotal,
        BigDecimal montantReelTotal,
        LocalDateTime dateCreation,
        LocalDate dateNotification,
        LocalDate dateLivraisonPrevue,
        String observations,
        String creeParNom,
        List<LigneDossierAchatDto> lignes
) {
}
