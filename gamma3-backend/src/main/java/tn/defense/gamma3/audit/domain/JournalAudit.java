package tn.defense.gamma3.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Une ligne du journal d'audit : QUI a fait QUOI, QUAND, depuis QUELLE adresse.
 *
 * Volontairement PAS de relation JPA vers {@code User} : le journal ne doit JAMAIS dépendre
 * d'une ligne mutable/potentiellement supprimable — l'acteur est capturé par sa valeur
 * (matricule + nom) au moment de l'action, comme un vrai registre papier. Un journal d'audit
 * qui pourrait perdre ses entrées si un compte est un jour supprimé n'en serait plus un.
 *
 * Ce journal n'a AUCUN endpoint de modification ou de suppression exposé (voir
 * {@code JournalAuditController}) — uniquement lecture (ADMIN) et écriture interne via
 * {@code JournalAuditService.enregistrer(...)}.
 */
@Entity
@Table(name = "journal_audit", indexes = {
    @Index(name = "idx_journal_audit_date", columnList = "date_evenement DESC"),
    @Index(name = "idx_journal_audit_acteur", columnList = "acteur_matricule")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_evenement", nullable = false, updatable = false)
    private LocalDateTime dateEvenement;

    @Column(name = "acteur_matricule", nullable = false, length = 50)
    private String acteurMatricule;

    @Column(name = "acteur_nom", length = 100)
    private String acteurNom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TypeActionAudit action;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "adresse_ip", length = 64)
    private String adresseIp;

    @PrePersist
    void onCreate() {
        this.dateEvenement = LocalDateTime.now();
    }
}
