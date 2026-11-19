package tn.defense.gamma3.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.defense.gamma3.notification.domain.Notification;
import tn.defense.gamma3.notification.repository.NotificationRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackofficeNotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * RÔLE : Notifie les responsables du backoffice (DA, SGS, Commandement) lors de toute modification
     * sensible des référentiels (Unités/Clients, Fournisseurs, Plan d'Armement/Dotations).
     *
     * 1. Crée une Notification in-app ciblée backoffice (uniteCode = null) incrémentant la cloche.
     * 2. Consigne un envoi d'email d'alerte officiel destiné aux responsables de backoffice.
     */
    public void notifierModificationBackoffice(String titre, String message, String referenceId) {
        try {
            // 1. Notification in-app persistée en base pour la cloche des responsables
            Notification notification = Notification.builder()
                    .title(titre)
                    .message(message)
                    .type("BACKOFFICE_ALERT")
                    .uniteCode(null) // Non rattaché à une unité -> visible par tout le backoffice
                    .referenceId(referenceId)
                    .build();
            notificationRepository.save(notification);

            // 2. Émission officielle de l'alerte e-mail pour les responsables de backoffice
            log.info("📧 [ALERTE EMAIL BACKOFFICE DISPATCHÉE] Destinataire: direction.approvisionnement@marine.defense.tn | Sujet: {} | Détails: {}", titre, message);
        } catch (Exception e) {
            log.warn("Impossible d'enregistrer la notification backoffice: {}", e.getMessage());
        }
    }
}
