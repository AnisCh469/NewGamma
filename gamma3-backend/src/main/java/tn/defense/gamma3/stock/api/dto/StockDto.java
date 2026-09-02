package tn.defense.gamma3.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDto {
    private Long id;
    private Long magasinId;
    private String magasinCode;
    private String magasinNom;
    private BigDecimal quantite;
    private String emplacement;
    private String rayon;
}

