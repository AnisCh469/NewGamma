package tn.defense.gamma3.prevision.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Une ligne de l'écran « Prévisions » (Procédure P01 du CCF) — un article dont le stock
 * potentiel est descendu au niveau ou en dessous de son seuil d'alerte calculé (Sa).
 *
 * @param stockActuel           stock réel total, tous magasins confondus
 * @param quantiteReservee      quantités déjà réservées par des demandes en cours (CCF RG10)
 * @param stockPotentiel        stockActuel − quantiteReservee − stockSecurite (CCF RG9,
 *                              simplifié : la composante "quantité commandée" n'est pas incluse
 *                              — GAMMA 3 ne suit pas encore les commandes en cours, voir
 *                              Procédure P02, hors périmètre actuel)
 * @param consommationMoyenneAnnuelle Cmoy3 — moyenne des sorties des 3 dernières années
 * @param parc                  somme de la dotation réellement en service, toutes unités
 *                              confondues (utilisée seulement pour un article NON_CONSOMMABLE)
 * @param delaiApprovisionnementAnnees n — CCF RG7 (1 ou 2 ans selon le mode d'acquisition)
 * @param seuilAlerte           Sa — le point de commande calculé (CCF RG5/RG6)
 * @param seuilSecuriteCalcule  Ss calculé par la formule CCF (Sa/3) — seulement pour un article
 *                              Critique (CCF RG13) ; {@code null} sinon
 * @param seuilMinimalCalcule   Smin calculé (Sa − Ss) — seulement pour un article Critique
 * @param stockSecuriteActuel   la valeur de {@code Item.stockSecurite} réellement utilisée
 *                              aujourd'hui pour bloquer une distribution — affichée à côté de
 *                              {@code seuilSecuriteCalcule} pour comparaison, jamais remplacée
 *                              automatiquement (voir docs/PROPOSITION_PREVISIONS_ET_COLLATIONNEMENT.md §6.1)
 */
public record PrevisionDto(
        UUID itemId,
        String nomenclature,
        String designation,
        String typeConsommabilite,
        BigDecimal stockActuel,
        BigDecimal quantiteReservee,
        BigDecimal stockPotentiel,
        BigDecimal consommationMoyenneAnnuelle,
        BigDecimal parc,
        int delaiApprovisionnementAnnees,
        BigDecimal seuilAlerte,
        BigDecimal seuilSecuriteCalcule,
        BigDecimal seuilMinimalCalcule,
        BigDecimal stockSecuriteActuel
) {}
