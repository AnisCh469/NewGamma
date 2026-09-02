package tn.defense.gamma3.reception.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pv_commission")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PvCommission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_pv", unique = true, nullable = false)
    private String numeroPv;

    @Column(name = "date_commission", nullable = false)
    private LocalDate dateCommission;

    @Column(columnDefinition = "TEXT")
    private String membres;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DecisionPv decision;

    @Column(columnDefinition = "TEXT")
    private String observations;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "bpr_id", unique = true, nullable = false)
    private BonProvisoireReception bpr;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
