package tn.defense.gamma3.collationnement.dto;

import java.math.BigDecimal;

/** Une ligne de comptage physique saisie par la commission (voir CollationnementController). */
public record LigneComptageDto(Long ligneId, BigDecimal quantitePhysiqueConstatee, String observations) {
}
