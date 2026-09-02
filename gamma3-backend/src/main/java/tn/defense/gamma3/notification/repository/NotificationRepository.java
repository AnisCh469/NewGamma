package tn.defense.gamma3.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.defense.gamma3.notification.domain.Notification;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    // For clients: find target unit notifications or global notifications
    List<Notification> findByUniteCodeOrderByCreatedAtDesc(String uniteCode);
    
    // For admins/operators: find admin notifications
    List<Notification> findByUniteCodeIsNullOrderByCreatedAtDesc();

    // Count unread for clients
    long countByUniteCodeAndReadIsFalse(String uniteCode);

    // Count unread for admins
    long countByUniteCodeIsNullAndReadIsFalse();
}
