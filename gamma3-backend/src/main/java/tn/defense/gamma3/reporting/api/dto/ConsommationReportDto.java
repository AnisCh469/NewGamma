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
public class ConsommationReportDto {
    private String nomenclature;
    private String designation;
    private BigDecimal quantiteType;
    private BigDecimal quantiteReelle;
    private BigDecimal quantiteVirtuelle;
}
