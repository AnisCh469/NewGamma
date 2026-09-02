package tn.defense.gamma3.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.defense.gamma3.stock.domain.TypeMouvement;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MouvementDto {
    private java.util.UUID itemId;
    private Long magasinId;
    private TypeMouvement typeMouvement;
    private BigDecimal quantite;
    private String referenceBon;
    private String motif;
    private String emplacement; // Optionnel, pour spécifier l'emplacement lors de l'entrée
    private String rayon;       // Optionnel, pour spécifier le rayon lors de l'entrée
}

