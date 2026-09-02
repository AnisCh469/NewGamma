package tn.defense.gamma3.reporting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventaireReportDto {
    private String nomenclature;
    private String designation;
    private String magasinCode;
    private String magasinNom;
    private BigDecimal quantite;
    private BigDecimal prixUnitaire;
    private BigDecimal valeurTotale;
}
