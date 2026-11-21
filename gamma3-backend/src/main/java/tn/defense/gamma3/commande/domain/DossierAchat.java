package tn.defense.gamma3.commande.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.prevision.domain.ModeAcquisition;
import tn.defense.gamma3.tiers.domain.Fournisseur;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dossier de Commande / Marché d'Achat (Procédure P02 du CCF).
 */
@Entity
@Table(name = "dossiers_achats", indexes = {
        @Index(name = "idx_dossier_achat_numero", columnList = "numero_dossier"),
        @Index(name = "idx_dossier_achat_statut", columnList = "statut"),
        @Index(name = "idx_dossier_achat_annee", columnList = "annee")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DossierAchat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "numero_dossier", nullable = false, unique = true, length = 50)
    private String numeroDossier;

    @Column(name = "annee", nullable = false)
    private Integer annee;

    @Column(name = "objet", nullable = false, length = 255)
    private String objet;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_acquisition", nullable = false, length = 30)
    private ModeAcquisition modeAcquisition;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fournisseur_id")
    private Fournisseur fournisseur;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    @Builder.Default
    private StatutAchat statut = StatutAchat.DOSSIER_CREE;

    @Column(name = "reference_marche_ou_bon", length = 100)
    private String referenceMarcheOuBon;

    @Column(name = "montant_estime_total", precision = 15, scale = 3)
    private BigDecimal montantEstimeTotal;

    @Column(name = "montant_reel_total", precision = 15, scale = 3)
    private BigDecimal montantReelTotal;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_notification")
    private LocalDate dateNotification;

    @Column(name = "date_livraison_prevue")
    private LocalDate dateLivraisonPrevue;

    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par_id")
    private User creePar;

    @OneToMany(mappedBy = "dossierAchat", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<LigneDossierAchat> lignes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }
}
