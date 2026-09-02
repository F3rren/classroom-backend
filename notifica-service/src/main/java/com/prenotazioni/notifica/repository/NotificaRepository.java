package com.prenotazioni.notifica.repository;

import com.prenotazioni.notifica.model.Notifica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

// NOTA: le @Query sono stringhe, non le verifica il compilatore. Passando da
// @ManyToOne Utente a una colonna utenteId, il codice compilava ancora ma le tre
// query qui sotto riferivano un attributo inesistente, e il contesto Spring non
// partiva piu'. E' il tipo di rottura che solo l'esecuzione dei test rivela.
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
     * Elimina tutte le notifiche lette di un utente
     */
    @Modifying
    @Query("DELETE FROM Notifica n WHERE n.utenteId = :utenteId AND n.letta = true")
    void deleteByUtenteIdAndLettaTrue(@Param("utenteId") Long utenteId);
    
    /**
     * Elimina tutte le notifiche di un utente (per eliminazione utente)
     */
    @Modifying
    @Query("DELETE FROM Notifica n WHERE n.utenteId = :utenteId")
    void deleteByUtenteId(@Param("utenteId") Long utenteId);
    
    /**
     * Segna tutte le notifiche di un utente come lette
     */
    @Modifying
    @Query("UPDATE Notifica n SET n.letta = true WHERE n.utenteId = :utenteId AND n.letta = false")
    void segnaTutteComeLette(@Param("utenteId") Long utenteId);
}