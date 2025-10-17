package com.prenotazioni.repository;

import com.prenotazioni.model.Prenotazione;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface IPrenotazioneRepository extends JpaRepository<Prenotazione, Long> {
    
    // Trova prenotazioni che si sovrappongono con un periodo dato
    @Query("SELECT p FROM Prenotazione p WHERE p.aula.id = :aulaId " +
           "AND p.stato != 'annullata' " +
           "AND ((p.inizio <= :inizio AND p.fine > :inizio) " +
           "OR (p.inizio < :fine AND p.fine >= :fine) " +
           "OR (p.inizio >= :inizio AND p.fine <= :fine))")
    List<Prenotazione> findConflittingReservations(@Param("aulaId") Long aulaId, 
                                                   @Param("inizio") LocalDateTime inizio, 
                                                   @Param("fine") LocalDateTime fine);

    // Trova prenotazioni che si sovrappongono con un periodo dato escludendo una prenotazione specifica
    @Query("SELECT p FROM Prenotazione p WHERE p.aula.id = :aulaId " +
           "AND p.stato != 'annullata' " +
           "AND p.id != :prenotazioneIdEsclusa " +
           "AND ((p.inizio <= :inizio AND p.fine > :inizio) " +
           "OR (p.inizio < :fine AND p.fine >= :fine) " +
           "OR (p.inizio >= :inizio AND p.fine <= :fine))")
    List<Prenotazione> findConflittingReservationsExcluding(@Param("aulaId") Long aulaId, 
                                                           @Param("inizio") LocalDateTime inizio, 
                                                           @Param("fine") LocalDateTime fine,
                                                           @Param("prenotazioneIdEsclusa") Long prenotazioneIdEsclusa);
    
    // Trova prenotazioni di un'aula in un periodo specifico
    @Query("SELECT p FROM Prenotazione p WHERE p.aula.id = :aulaId " +
           "AND p.stato != 'annullata' " +
           "AND p.inizio <= :fine AND p.fine >= :inizio " +
           "ORDER BY p.inizio ASC")
    List<Prenotazione> findByAulaAndPeriod(@Param("aulaId") Long aulaId,
                                          @Param("inizio") LocalDateTime inizio,
                                          @Param("fine") LocalDateTime fine);
    
    // Trova prenotazioni attive in un momento specifico
    @Query("SELECT p FROM Prenotazione p WHERE p.aula.id = :aulaId " +
           "AND p.stato != 'annullata' " +
           "AND p.inizio <= :momento AND p.fine > :momento " +
           "ORDER BY p.stato DESC") // MANUTENZIONE, BLOCCATA, PRENOTATA
    List<Prenotazione> findActiveReservations(@Param("aulaId") Long aulaId,
                                             @Param("momento") LocalDateTime momento);
    
    // Trova prenotazioni per utente
    @Query("SELECT p FROM Prenotazione p WHERE p.utente.id = :utenteId " +
           "ORDER BY p.inizio DESC")
    List<Prenotazione> findByUtenteId(@Param("utenteId") Long utenteId);
    
    // Trova aule libere in un periodo
    @Query("SELECT a.id FROM Aula a WHERE a.id NOT IN " +
           "(SELECT DISTINCT p.aula.id FROM Prenotazione p WHERE p.stato != 'annullata' " +
           "AND ((p.inizio <= :inizio AND p.fine > :inizio) " +
           "OR (p.inizio < :fine AND p.fine >= :fine) " +
           "OR (p.inizio >= :inizio AND p.fine <= :fine)))")
    List<Long> findAuleLibere(@Param("inizio") LocalDateTime inizio, 
                              @Param("fine") LocalDateTime fine);
    
    // Trova prenotazioni per stato
    List<Prenotazione> findByStato(String stato);
    
    // Trova prenotazioni future
    @Query("SELECT p FROM Prenotazione p WHERE p.inizio > :ora AND p.stato != 'annullata' " +
           "ORDER BY p.inizio ASC")
    List<Prenotazione> findPrenotazioniFuture(@Param("ora") LocalDateTime ora);
    
    // Vista completa prenotazioni per una specifica aula
    @Query("SELECT new map(" +
           "p.id as prenotazioneId, " +
           "p.inizio as inizio, " + 
           "p.fine as fine, " +
           "p.stato as stato, " +
           "p.descrizione as notePrenotazione, " +
           "p.dataCreazione as dataCreazione, " +
           "a.id as aulaId, " +
           "a.nome as aulaNome, " +
           "a.capienza as aulaCapienza, " +
           "a.piano as aulaPiano, " +
           "u.id as utenteId, " +
           "u.username as username, " +
           "u.nome as utenteNome, " +
           "u.email as email, " +
           "u.ruolo as ruolo, " +
           "u.dataRegistrazione as utenteRegistrato, " +
           "u.ultimoAccesso as ultimoAccesso, " +
           "c.id as corsoId, " +
           "c.nome as corsoNome, " +
           "c.docente as docente, " +
           "CASE WHEN p.inizio > CURRENT_TIMESTAMP THEN 'FUTURA' " +
           "     WHEN p.fine < CURRENT_TIMESTAMP THEN 'PASSATA' " +
           "     ELSE 'IN_CORSO' END as statoTemporale) " +
           "FROM Prenotazione p " +
           "JOIN p.aula a " +
           "JOIN p.utente u " +
           "LEFT JOIN p.corso c " +
           "WHERE a.id = :aulaId " +
           "ORDER BY p.inizio DESC")
    List<Map<String, Object>> findCompleteDetailsByAulaId(@Param("aulaId") Long aulaId);
    
    // Vista completa di tutte le prenotazioni
    @Query("SELECT new map(" +
           "p.id as prenotazioneId, " +
           "p.inizio as inizio, " + 
           "p.fine as fine, " +
           "p.stato as stato, " +
           "p.descrizione as notePrenotazione, " +
           "p.dataCreazione as dataCreazione, " +
           "a.id as aulaId, " +
           "a.nome as aulaNome, " +
           "a.capienza as aulaCapienza, " +
           "a.piano as aulaPiano, " +
           "u.id as utenteId, " +
           "u.username as username, " +
           "u.nome as utenteNome, " +
           "u.email as email, " +
           "u.ruolo as ruolo, " +
           "u.dataRegistrazione as utenteRegistrato, " +
           "u.ultimoAccesso as ultimoAccesso, " +
           "c.id as corsoId, " +
           "c.nome as corsoNome, " +
           "c.docente as docente, " +
           "CASE WHEN p.inizio > CURRENT_TIMESTAMP THEN 'FUTURA' " +
           "     WHEN p.fine < CURRENT_TIMESTAMP THEN 'PASSATA' " +
           "     ELSE 'IN_CORSO' END as statoTemporale) " +
           "FROM Prenotazione p " +
           "JOIN p.aula a " +
           "JOIN p.utente u " +
           "LEFT JOIN p.corso c " +
           "ORDER BY p.inizio DESC")
    List<Map<String, Object>> findAllCompleteDetails();
    
    // Dettagli completi per una singola prenotazione
    @Query("SELECT new map(" +
           "p.id as prenotazioneId, " +
           "p.inizio as inizio, " + 
           "p.fine as fine, " +
           "p.stato as stato, " +
           "p.descrizione as notePrenotazione, " +
           "p.dataCreazione as dataCreazione, " +
           "a.id as aulaId, " +
           "a.nome as aulaNome, " +
           "a.capienza as aulaCapienza, " +
           "a.piano as aulaPiano, " +
           "u.id as utenteId, " +
           "u.username as username, " +
           "u.nome as utenteNome, " +
           "u.email as email, " +
           "u.ruolo as ruolo, " +
           "u.dataRegistrazione as utenteRegistrato, " +
           "u.ultimoAccesso as ultimoAccesso, " +
           "c.id as corsoId, " +
           "c.nome as corsoNome, " +
           "c.docente as docente, " +
           "CASE WHEN p.inizio > CURRENT_TIMESTAMP THEN 'FUTURA' " +
           "     WHEN p.fine < CURRENT_TIMESTAMP THEN 'PASSATA' " +
           "     ELSE 'IN_CORSO' END as statoTemporale) " +
           "FROM Prenotazione p " +
           "JOIN p.aula a " +
           "JOIN p.utente u " +
           "LEFT JOIN p.corso c " +
           "WHERE p.id = :prenotazioneId")
    List<Map<String, Object>> findCompleteDetailsByPrenotazioneId(@Param("prenotazioneId") Long prenotazioneId);
    
    // Trova tutte le prenotazioni per una specifica aula
    @Query("SELECT p FROM Prenotazione p WHERE p.aula.id = :aulaId ORDER BY p.inizio ASC")
    List<Prenotazione> findByAulaId(@Param("aulaId") Long aulaId);
}
