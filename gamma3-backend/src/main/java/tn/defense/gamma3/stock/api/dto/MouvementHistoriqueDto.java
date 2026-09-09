package tn.defense.gamma3.stock.api.dto;

import java.math.BigDecimal;

/**
 * Une ligne de l'historique des mouvements de stock d'un article (toutes soutes confondues).
 * Vue en lecture seule : réceptions (ENTREE), sorties vers les unités (SORTIE), transferts
 * inter-magasin, réformes (REFORME), ajustements d'inventaire (INVENTAIRE).
 *
 * @param date        horodatage du mouvement (ISO)
 * @param type        ENTREE | SORTIE | INVENTAIRE | REFORME
 * @param sens        "+" si le mouvement augmente le stock, "-" s'il le diminue, "=" pour un inventaire
 * @param quantite    quantité déplacée (toujours positive)
 * @param magasinCode code de la soute concernée
 * @param magasinNom  nom de la soute concernée
 * @param reference   numéro du bon à l'origine du mouvement (BE-…, BS-…, TRANSFERT-…, DR-…)
 * @param motif       libellé libre saisi lors du mouvement
 * @param agent       matricule / nom de l'agent qui a enregistré le mouvement
 */
public record MouvementHistoriqueDto(
        String date,
        String type,
        String sens,
        BigDecimal quantite,
        String magasinCode,
        String magasinNom,
        String reference,
        String motif,
        String agent
) {}
