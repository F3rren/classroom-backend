package com.prenotazioni.notification.repository;

import com.prenotazioni.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

// NOTA: le @Query sono stringhe, non le verifica il compilatore. Passando da
// @ManyToOne Utente a una colonna utenteId, il codice compilava ancora ma le tre
// query qui sotto riferivano un attributo inesistente, e il contesto Spring non
// partiva piu'. E' il tipo di rottura che solo l'esecuzione dei test rivela.
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    /**
     * Trova tutte le notifiche di un utente ordinate per data creazione (più recenti prima)
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    /**
     * Trova solo le notifiche non lette di un utente
     */
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);
    
    /**
     * Conta le notifiche non lette di un utente
     */
    Long countByUserIdAndReadFalse(Long userId);
    
    /**
     * Elimina tutte le notifiche lette di un utente
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId AND n.read = true")
    void deleteByUserIdAndReadTrue(@Param("userId") Long userId);
    
    /**
     * Elimina tutte le notifiche di un utente (per eliminazione utente)
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
    
    /**
     * Segna tutte le notifiche di un utente come lette
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    void segnaTutteComeLette(@Param("userId") Long userId);
}