package tn.defense.gamma3.catalogue.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO léger pour la liste des articles (sans documents).
 * Évite le problème N+1 causé par la sérialisation de la collection documents.
 */
public record ItemSummaryDto(
        UUID id,
        String classeCode,
        String sousClasseCode,
        String categorieCode,
        String serieCode,
        String itemCode,
        String nomenclature,
        String designation,
        BigDecimal prixUnitaire,
        BigDecimal stockSecurite,
        String uniteGestionCode,
        String photoUrl,
        String dangerClass,
        String typeConsommabilite,
        BigDecimal quantiteTotale,
        BigDecimal quantiteReservee,
        BigDecimal quantiteType,
        BigDecimal quantiteReelle
) {}

