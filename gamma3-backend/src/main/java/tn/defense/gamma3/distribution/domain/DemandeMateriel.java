package tn.defense.gamma3.distribution.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.stock.domain.Magasin;
import tn.defense.gamma3.tiers.domain.UniteUtilisatrice;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "demandes_materiel")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandeMateriel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_demande", unique = true, nullable = false)
    private String numeroDemande;

    @Column(name = "date_demande", nullable = false)
    private LocalDate dateDemande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDemande statut;

    @Column(name = "motif_refus", columnDefinition = "TEXT")
    private String motifRefus;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unite_id", nullable = false)
    private UniteUtilisatrice unite;

    // ─── Routage Multi-Magasin ─────────────────────────────────────────────────────
    // RÔLE : Magasin désigné par l'algorithme de routage géographique à la création.
    //        Seul le DA_MANAGER de ce magasin voit et peut arbitrer cette demande.
    //        L'ADMIN (magasin=null) voit toutes les demandes.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "magasin_designe_id", nullable = true)
    private Magasin magasinDesigne;

    /** Motif de l'escalade vers SGS (renseigné par le DA_MANAGER lors d'une escalade). */
    @Column(name = "motif_escalade", columnDefinition = "TEXT")
    private String motifEscalade;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bon_sortie_id")
    private BonSortie bonSortie;

    @OneToMany(mappedBy = "demande", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LigneDemande> lignes = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (statut == null) statut = StatutDemande.SOUMIS;
    }
}
