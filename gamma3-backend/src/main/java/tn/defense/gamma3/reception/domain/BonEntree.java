package tn.defense.gamma3.reception.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bons_entree")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BonEntree {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_be", unique = true, nullable = false)
    private String numeroBe;

    @Column(name = "date_entree", nullable = false)
    private LocalDate dateEntree;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pv_id", unique = true, nullable = false)
    private PvCommission pvCommission;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
