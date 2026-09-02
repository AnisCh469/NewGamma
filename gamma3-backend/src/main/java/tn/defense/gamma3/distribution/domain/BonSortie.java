package tn.defense.gamma3.distribution.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bons_sortie")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BonSortie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_bs", unique = true, nullable = false)
    private String numeroBs;

    @Column(name = "date_sortie", nullable = false)
    private LocalDate dateSortie;

    private String transporteur;

    @Column(name = "vehicule_matricule")
    private String vehiculeMatricule;

    @Column(nullable = false, length = 20)
    private String statut; // PREPARE, LIVRE

    @Column(name = "magasin_id")
    private Long magasinId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (statut == null) statut = "PREPARE";
    }
}
