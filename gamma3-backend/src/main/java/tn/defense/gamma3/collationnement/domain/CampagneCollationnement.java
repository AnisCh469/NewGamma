package tn.defense.gamma3.collationnement.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * RÔLE : Procédure P07 du CCF (Comptabilité / Collationnement annuel) — version allégée, voir
 * docs/PROPOSITION_PREVISIONS_ET_COLLATIONNEMENT.md §3.3 (proposition validée par l'utilisateur,
 * 2026-09-21). Une campagne compare la dotation théorique du Plan d'Armement (ce que le système
 * pense qu'une unité détient) au constat physique relevé par une commission qui se déplace —
 * exactement le principe de la Procédure P07 (RG83-RG90), sans reconstruire les formulaires
 * papier spécifiques (C5/C5 bis pour un déficit, M16 pour un excédent — voir §3.3 point 4, hors
 * périmètre sauf demande explicite) : un simple champ "observations" par ligne suffit pour
 * l'instant.
 *
 * Pas de relation JPA vers un magasin ou un article directement ici : le détail ligne par ligne
 * vit dans {@link LigneCollationnement}, un snapshot du Plan d'Armement au moment du lancement
 * de la campagne (le Plan d'Armement continue d'évoluer après coup, la campagne doit rester une
 * photographie figée de ce qui a été effectivement vérifié).
 */
@Entity
@Table(name = "campagnes_collationnement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampagneCollationnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unite_id", nullable = false)
    private UniteUtilisatrice unite;

    @Column(name = "date_planifiee", nullable = false)
    private LocalDate datePlanifiee;

    /** PLANIFIEE (lignes générées, comptage pas encore saisi) → EN_COURS (comptage partiel ou
     *  complet saisi) → TERMINEE (clôturée, conclusion et PV disponibles). */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String statut = "PLANIFIEE";

    @Column(name = "membres_commission", columnDefinition = "TEXT")
    private String membresCommission;

    @Column(name = "date_cloture")
    private LocalDate dateCloture;

    /** CONFORME (aucun écart constaté) ou ECART_CONSTATE (au moins une ligne en écart) — voir
     *  CCF RG88-RG90. Renseignée uniquement à la clôture. */
    @Column(name = "conclusion_generale", length = 30)
    private String conclusionGenerale;

    @Column(name = "observations_generales", columnDefinition = "TEXT")
    private String observationsGenerales;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (statut == null) statut = "PLANIFIEE";
    }
}
