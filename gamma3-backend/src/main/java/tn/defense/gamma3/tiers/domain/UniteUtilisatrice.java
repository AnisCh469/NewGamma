package tn.defense.gamma3.tiers.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "unites_utilisatrices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UniteUtilisatrice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // Ex: DMEN, DRC, DEN...

    @Column(nullable = false)
    private String nom; // Nom complet de l'unité

    private String baseNavale; // Ex: Bizerte, Kélibia, Sfax

    private String logoUrl;

    @Transient
    @JsonProperty("planCount")
    private Long planCount = 0L;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
