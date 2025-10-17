package com.prenotazioni.repository;

import com.prenotazioni.model.Notifica;
import org.springframework.data.jpa.repository.JpaRepository;
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
    void deleteByUtenteIdAndLettaTrue(Long utenteId);
}