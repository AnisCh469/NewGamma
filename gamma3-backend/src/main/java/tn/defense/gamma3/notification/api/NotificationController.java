package tn.defense.gamma3.notification.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.defense.gamma3.auth.domain.User;
import tn.defense.gamma3.auth.domain.Role;
import tn.defense.gamma3.notification.domain.Notification;
import tn.defense.gamma3.notification.repository.NotificationRepository;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<Notification>> getNotifications(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User currentUser)) {
            return ResponseEntity.status(401).build();
        }

        List<Notification> notifications;
        if (currentUser.getRole() == Role.SUPER_ADMIN || currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.DA_MANAGER) {
            notifications = notificationRepository.findByUniteCodeIsNullOrderByCreatedAtDesc();
        } else {
            notifications = notificationRepository.findByUniteCodeOrderByCreatedAtDesc(resolveUniteCode(currentUser));
        }

        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User currentUser)) {
            return ResponseEntity.status(401).build();
        }

        long count;
        if (currentUser.getRole() == Role.SUPER_ADMIN || currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.DA_MANAGER) {
            count = notificationRepository.countByUniteCodeIsNullAndReadIsFalse();
        } else {
            count = notificationRepository.countByUniteCodeAndReadIsFalse(resolveUniteCode(currentUser));
        }

        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        return notificationRepository.findById(id)
                .map(notif -> {
                    notif.setRead(true);
                    notificationRepository.save(notif);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User currentUser)) {
            return ResponseEntity.status(401).build();
        }

        List<Notification> notifications;
        if (currentUser.getRole() == Role.SUPER_ADMIN || currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.DA_MANAGER) {
            notifications = notificationRepository.findByUniteCodeIsNullOrderByCreatedAtDesc();
        } else {
            notifications = notificationRepository.findByUniteCodeOrderByCreatedAtDesc(resolveUniteCode(currentUser));
        }

        for (Notification notif : notifications) {
            if (!notif.isRead()) {
                notif.setRead(true);
                notificationRepository.save(notif);
            }
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Code d'unité à utiliser pour filtrer les notifications d'un compte UNIT_USER.
     *
     * AVANT le 2026-09-04, le matricule d'un compte UNIT_USER était TOUJOURS strictement égal
     * au code de son unité (un seul compte par unité) — cette méthode utilisait donc
     * directement `currentUser.getMatricule()`. Depuis l'introduction du rattachement explicite
     * `User.unite` (chaque unité a désormais deux comptes, Gestionnaire ET Commandant, avec des
     * matricules libres — voir FonctionUnite), ce n'est plus garanti : on privilégie donc
     * `unite.getCode()` quand il est renseigné, avec repli sur le matricule pour les comptes
     * historiques pas encore migrés (voir DataSeeder, backfill de `User.unite`).
     */
    private String resolveUniteCode(User currentUser) {
        return currentUser.getUnite() != null ? currentUser.getUnite().getCode() : currentUser.getMatricule();
    }
}
