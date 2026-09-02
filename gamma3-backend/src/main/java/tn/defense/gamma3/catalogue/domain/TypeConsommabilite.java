package tn.defense.gamma3.catalogue.domain;

/**
 * Catégorie de consommabilité d'un article.
 *
 * CONSOMMABLE     : Article de consommation courante (peinture, huile, graisse, joints...).
 *                   Géré uniquement par quantité. Pas de cycle de vie individuel.
 *
 * NON_CONSOMMABLE : Équipement ou pièce nécessitant un suivi de cycle de vie
 *                   (appareil de mesure, pièce d'armement, pompe...).
 *                   Suit le cycle : NEUF → EN_SERVICE → MAINTENANCE → RÉFORME.
 */
public enum TypeConsommabilite {
    CONSOMMABLE,
    NON_CONSOMMABLE
}
