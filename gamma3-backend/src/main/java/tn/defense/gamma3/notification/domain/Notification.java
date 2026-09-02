package tn.defense.gamma3.notification.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false)
    private String type; // e.g. "DOTATION", "MATERIEL", "REFORME", "SYSTEM"

    @Column(name = "unite_code")
    private String uniteCode; // Target unit. If null, it is for admins/operators.

    @Column(name = "reference_id")
    private String referenceId; // Related resource ID (e.g., unit ID, demand ID)

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
