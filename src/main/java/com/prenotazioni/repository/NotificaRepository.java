package com.prenotazioni.repository;

import com.prenotazioni.model.Notifica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificaRepository extends JpaRepository<Notifica, Long> {
    
    /**
     * Trova tutte le notifiche di un utente ordinate per data creazione (più recenti prima)
     */
    List<Notifica> findByUtenteIdOrderByDataCreazioneDesc(Long utenteId);
    
    /**
     * Trova solo le notifiche non lette di un utente
     */
    List<Notifica> findByUtenteIdAndLettaFalseOrderByDataCreazioneDesc(Long utenteId);
    
    /**
     * Conta le notifiche non lette di un utente
     */
    Long countByUtenteIdAndLettaFalse(Long utenteId);
    
    /**
     * Trova le notifiche per tipo (es: "CANCELLAZIONE_ADMIN")
     */
    List<Notifica> findByUtenteIdAndTipoOrderByDataCreazioneDesc(Long utenteId, String tipo);
    
    /**
     * Elimina notifiche vecchie (per cleanup)
     */
    void deleteByDataCreazioneBefore(java.time.LocalDateTime dataLimite);
    
    /**
     * Elimina tutte le notifiche lette di un utente
     */
    @Modifying
    @Query("DELETE FROM Notifica n WHERE n.utente.id = :utenteId AND n.letta = true")
    void deleteByUtenteIdAndLettaTrue(@Param("utenteId") Long utenteId);
    
    /**
     * Elimina tutte le notifiche di un utente (per eliminazione utente)
     */
    @Modifying
    @Query("DELETE FROM Notifica n WHERE n.utente.id = :utenteId")
    void deleteByUtenteId(@Param("utenteId") Long utenteId);
    
    /**
     * Segna tutte le notifiche di un utente come lette
     */
    @Modifying
    @Query("UPDATE Notifica n SET n.letta = true WHERE n.utente.id = :utenteId AND n.letta = false")
    void segnaTutteComeLette(@Param("utenteId") Long utenteId);
}