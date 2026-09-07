package com.prenotazioni.notification.repository;

import com.prenotazioni.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

// NOTE: @Query holds strings, and the compiler does not check them. When the
// @ManyToOne User became a plain userId column, the code still compiled but the three
// queries below referred to an attribute that no longer existed, and the Spring context
// stopped starting. It is the kind of break only running the tests reveals.
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    /**
     * Every notification of a user, newest first.
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    /**
     * Only the unread notifications of a user.
     */
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);
    
    /**
     * Counts the unread notifications of a user.
     */
    Long countByUserIdAndReadFalse(Long userId);
    
    /**
     * Deletes every read notification of a user.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId AND n.read = true")
    void deleteByUserIdAndReadTrue(@Param("userId") Long userId);
    
    /**
     * Deletes every notification of a user, for when the user itself is deleted.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
    
    /**
     * Marks every notification of a user as read.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    void markAllAsRead(@Param("userId") Long userId);
}