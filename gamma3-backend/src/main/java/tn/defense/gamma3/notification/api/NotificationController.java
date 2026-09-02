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
        if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.DA_MANAGER) {
            notifications = notificationRepository.findByUniteCodeIsNullOrderByCreatedAtDesc();
        } else {
            notifications = notificationRepository.findByUniteCodeOrderByCreatedAtDesc(currentUser.getMatricule());
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
        if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.DA_MANAGER) {
            count = notificationRepository.countByUniteCodeIsNullAndReadIsFalse();
        } else {
            count = notificationRepository.countByUniteCodeAndReadIsFalse(currentUser.getMatricule());
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
        if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.DA_MANAGER) {
            notifications = notificationRepository.findByUniteCodeIsNullOrderByCreatedAtDesc();
        } else {
            notifications = notificationRepository.findByUniteCodeOrderByCreatedAtDesc(currentUser.getMatricule());
        }

        for (Notification notif : notifications) {
            if (!notif.isRead()) {
                notif.setRead(true);
                notificationRepository.save(notif);
            }
        }

        return ResponseEntity.ok().build();
    }
}
