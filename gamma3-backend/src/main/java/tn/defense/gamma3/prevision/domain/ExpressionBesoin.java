package tn.defense.gamma3.prevision.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dossier d'Expression de Besoin Annuel (Procédure P01 du CCF).
 */
@Entity
@Table(name = "expressions_besoins", indexes = {
        @Index(name = "idx_exp_besoin_annee", columnList = "annee_budgetaire"),
        @Index(name = "idx_exp_besoin_statut", columnList = "statut"),
        @Index(name = "idx_exp_besoin_unite", columnList = "unite_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpressionBesoin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "numero_dossier", nullable = false, unique = true, length = 50)
    private String numeroDossier;

    @Column(name = "annee_budgetaire", nullable = false)
    private Integer anneeBudgetaire;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unite_id", nullable = false)
    private UniteUtilisatrice unite;

    @Column(name = "objet", length = 255)
    private String objet;

    @Column(name = "commentaires", columnDefinition = "TEXT")
    private String commentaires;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    @Builder.Default
    private StatutBesoin statut = StatutBesoin.BROUILLON;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par_id")
    private User creePar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arbitre_par_id")
    private User arbitrePar;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_soumission")
    private LocalDateTime dateSoumission;

    @Column(name = "date_arbitrage")
    private LocalDateTime dateArbitrage;

    @OneToMany(mappedBy = "expressionBesoin", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<LigneExpressionBesoin> lignes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }
}
