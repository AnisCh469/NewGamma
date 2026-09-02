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
public class MouvementReportDto {
    private String dateMouvement;
    private String typeMouvement;
    private String magasinNom;
    private String nomenclature;
    private String designation;
    private BigDecimal quantite;
    private String referenceBon;
    private String motif;
}
