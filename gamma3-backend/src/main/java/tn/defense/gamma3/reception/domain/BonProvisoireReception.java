package tn.defense.gamma3.reception.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.defense.gamma3.tiers.domain.CommandeFournisseur;
import tn.defense.gamma3.tiers.domain.Fournisseur;
import tn.defense.gamma3.stock.domain.Magasin;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bpr")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BonProvisoireReception {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_bpr", unique = true, nullable = false)
    private String numeroBpr;

    @Column(name = "date_reception", nullable = false)
    private LocalDate dateReception;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutBPR statut;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id")
    private CommandeFournisseur commande;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "magasin_id", nullable = false)
    private Magasin magasin;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (statut == null) statut = StatutBPR.ATTENTE_PV;
    }
}
