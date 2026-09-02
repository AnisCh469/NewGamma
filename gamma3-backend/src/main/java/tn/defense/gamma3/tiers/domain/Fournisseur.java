package tn.defense.gamma3.tiers.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fournisseurs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fournisseur {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // Code interne (ex: F001)

    @Column(nullable = false)
    private String nom; // Raison sociale

    private String matriculeFiscal;
    
    private String adresse;
    
    private String contactNom;
    
    private String telephone;
    
    private String email;

    private String fax;

    private Integer note; // Note sur 10

    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutFournisseur statut = StatutFournisseur.PROSPECT;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
