package com.prenotazioni.booking.service;

import com.prenotazioni.booking.dto.BookingDetailDto;
import com.prenotazioni.booking.model.Room;
import com.prenotazioni.booking.model.Course;
import com.prenotazioni.exception.BookingConflictException;
import com.prenotazioni.exception.DomainConflictException;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.booking.model.Booking;
import com.prenotazioni.booking.model.BookingOwner;
import com.prenotazioni.booking.model.RoomStatus;
import com.prenotazioni.booking.model.BookingStatus;
import com.prenotazioni.booking.repository.RoomRepository;
import com.prenotazioni.booking.repository.CourseRepository;
import com.prenotazioni.booking.repository.BookingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BookingService {
    
    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);
    
    private final BookingRepository bookingRepository;
    
    private final RoomRepository roomRepository;
    
    private final CourseRepository courseRepository;
    

    BookingService(BookingRepository bookingRepository, RoomRepository roomRepository, CourseRepository courseRepository) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.courseRepository = courseRepository;
    }

    // Prenota un'aula per una lezione
    @Transactional
    public Booking bookRoom(Long roomId, Long courseId, BookingOwner proprietario, LocalDateTime startTime, LocalDateTime endTime, String description) {
        logger.debug("INIZIO METODO prenotaAula");
        logger.debug("Richiesta prenotazione aula - AulaId: {}, CorsoId: {}, UtenteId: {}, Periodo: {} - {}", roomId, courseId, proprietario.getId(), startTime, endTime);
        
        // Verifica disponibilità
        if (!isRoomAvailable(roomId, startTime, endTime)) {
            logger.warn("Aula ID {} non disponibile per il periodo {} - {}", roomId, startTime, endTime);
            throw new BookingConflictException("BOOKING_CONFLICT",
                    "Aula " + roomId + " busy from " + startTime + " to " + endTime,
                    "L'aula non e' disponibile nel periodo richiesto.");
        }
        
        Optional<Room> room = roomRepository.findById(roomId);

        if (room.isEmpty()) {
            // 404 e non piu' 409: aula inesistente e aula occupata erano entrambe un null,
            // e il controller le presentava tutte come conflitto. Sono cose diverse.
            throw ResourceNotFoundException.perId("Aula", "ROOM_NOT_FOUND", roomId);
        }
        // Non si verifica piu' che l'utente esista: questo servizio non ha piu' la tabella
        // utenti. A garantirlo e' il token, che auth-service ha firmato al login. La finestra
        // di un utente cancellato con un token ancora valido e' limitata dalla scadenza.
        
        // Corso opzionale - può essere null per prenotazioni libere
        Optional<Course> course = Optional.empty();
        if (courseId != null) {
            course = courseRepository.findById(courseId);
            if (course.isEmpty()) {
                // Il corso e' facoltativo, ma se indicato deve esistere: passarne uno
                // inesistente e' un errore del chiamante, non una prenotazione libera.
                throw ResourceNotFoundException.perId("Corso", "COURSE_NOT_FOUND", courseId);
            }
        }

        logger.debug("Creazione prenotazione per aula - AulaId: {}, CorsoId: {}, UtenteId: {}, Periodo: {} - {}", roomId, courseId, proprietario.getId(), startTime, endTime);
        Booking booking = new Booking();
        booking.setRoom(room.get());
        booking.setCourse(course.orElse(null)); // Può essere null
        booking.setUser(proprietario);
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setDescription(description);
        booking.setCreatedAt(LocalDateTime.now());
        
        Booking savedPrenotazione = bookingRepository.save(booking);
        
        // Aggiorna lo stato dell'aula se la prenotazione è attiva ADESSO
        updateRoomStatus(roomId);
        
        logger.info("Prenotazione creata - id={} aula='{}' utenteId={} periodo={} - {}", savedPrenotazione.getId(), room.get().getName(), proprietario.getId(), startTime, endTime);
        logger.debug("FINE METODO prenotaAula");
        return savedPrenotazione;
    }
    
    // Blocca un'aula (solo admin)
    @Transactional
    public Booking blockRoom(Long roomId, BookingOwner admin, LocalDateTime startTime, LocalDateTime endTime, String motivo) {
        logger.debug("INIZIO METODO bloccaAula");
        logger.debug("Richiesta blocco aula - AulaId: {}, AdminId: {}, Periodo: {} - {}", roomId, admin.getId(), startTime, endTime);
        
        // Verifica disponibilità
        if (!isRoomAvailable(roomId, startTime, endTime)) {
            logger.warn("Aula ID {} non disponibile per il periodo {} - {}", roomId, startTime, endTime);
            throw new BookingConflictException("BLOCK_CONFLICT",
                    "Aula " + roomId + " busy from " + startTime + " to " + endTime,
                    "L'aula non e' disponibile nel periodo richiesto.");
        }
        
        Optional<Room> room = roomRepository.findById(roomId);

        // Il ruolo non si rilegge dal database: arriva dal token, e il controller
        // e' gia' annotato @PreAuthorize("hasRole('ADMIN')").
        if (room.isEmpty()) {
            throw ResourceNotFoundException.perId("Aula", "ROOM_NOT_FOUND", roomId);
        }
        
        logger.debug("Blocco aula - AulaId: {}, AdminId: {}, Periodo: {} - {}", roomId, admin.getId(), startTime, endTime);
        Booking blocco = new Booking();
        blocco.setRoom(room.get());
        blocco.setCourse(null); // Nessun corso per i blocchi
        blocco.setUser(admin);
        blocco.setStartTime(startTime);
        blocco.setEndTime(endTime);
        blocco.setStatus(BookingStatus.BLOCKED);
        blocco.setDescription(motivo);
        blocco.setCreatedAt(LocalDateTime.now());
        
        logger.info("Blocco aula creato - id={} aula='{}' adminId={} periodo={} - {}", blocco.getId(), room.get().getName(), admin.getId(), startTime, endTime);
        logger.debug("FINE METODO bloccaAula");
        return bookingRepository.save(blocco);
    }
    
    // Verifica se un'aula è disponibile in un determinato periodo
    public boolean isRoomAvailable(Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        logger.debug("INIZIO METODO isAulaDisponibile");
        logger.debug("Verifica disponibilità aula - AulaId: {}, Periodo: {} - {}", roomId, startTime, endTime);
        List<Booking> conflitti = bookingRepository.findConflictingBookings(roomId, startTime, endTime);
        boolean disponibile = conflitti.isEmpty();
        logger.debug("Risultato verifica disponibilità aula - AulaId: {}, Periodo: {} - {}", roomId, startTime, endTime, disponibile);
        return disponibile;
    }
    
    // Ottiene lo stato attuale di un'aula
    public String getRoomStatus(Long roomId, LocalDateTime moment) {
        logger.debug("INIZIO METODO getStatoAula");
        logger.debug("Verifica stato aula - AulaId: {}, Momento: {}", roomId, moment);
        List<Booking> activeBookings = bookingRepository.findActiveBookings(roomId, moment);
            
        if (activeBookings.isEmpty()) {
            logger.debug("Stato aula - AulaId: {}, Momento: {} - FREE", roomId, moment);
            return "FREE";
        }
        
        // Priorità: MAINTENANCE > BLOCKED > BOOKED
        for (Booking p : activeBookings) {
            if (p.getStatus() == BookingStatus.MAINTENANCE) {
                logger.debug("Stato aula - AulaId: {}, Momento: {} - MAINTENANCE", roomId, moment);
                return "MAINTENANCE";
            }
        }
        
        for (Booking p : activeBookings) {
            if (p.getStatus() == BookingStatus.BLOCKED) {
                logger.debug("Stato aula - AulaId: {}, Momento: {} - BLOCKED", roomId, moment);
                return "BLOCKED";
            }
        }
        
        logger.debug("Stato aula - AulaId: {}, Momento: {} - BOOKED", roomId, moment);
        logger.debug("FINE METODO getStatoAula");
        return "BOOKED";
    }
    
    // Aggiorna lo stato dell'aula in base alle prenotazioni attive
    private void updateRoomStatus(Long roomId) {
        logger.debug("INIZIO METODO aggiornaStatoAula - AulaId: {}", roomId);
        
        Optional<Room> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            logger.warn("Aula non trovata per aggiornamento stato - AulaId: {}", roomId);
            return;
        }
        
        Room room = roomOpt.get();
        LocalDateTime now = LocalDateTime.now();
        
        // Ottieni prenotazioni attive in questo momento
        List<Booking> activeBookings = bookingRepository.findActiveBookings(roomId, now);
        
        RoomStatus nuovoStato;
        if (activeBookings.isEmpty()) {
            nuovoStato = RoomStatus.FREE;
        } else {
            // Controlla se c'è una prenotazione di manutenzione o bloccata
            boolean hasManutenzione = activeBookings.stream()
                .anyMatch(p -> p.getStatus() == BookingStatus.MAINTENANCE);
            boolean hasBloccata = activeBookings.stream()
                .anyMatch(p -> p.getStatus() == BookingStatus.BLOCKED);
            
            if (hasManutenzione) {
                nuovoStato = RoomStatus.MAINTENANCE;
            } else if (hasBloccata) {
                nuovoStato = RoomStatus.BLOCKED;
            } else {
                nuovoStato = RoomStatus.BUSY;
            }
        }
        
        // Aggiorna solo se lo stato è cambiato
        if (nuovoStato != room.getStatus()) {
            logger.debug("Aggiornamento stato aula {} da '{}' a '{}'", roomId, room.getStatus(), nuovoStato);
            room.setStatus(nuovoStato);
            roomRepository.save(room);
        } else {
            logger.debug("Stato aula {} rimane invariato: '{}'", roomId, room.getStatus());
        }
        
        logger.debug("FINE METODO aggiornaStatoAula");
    }
    
    // Annulla una prenotazione
    @Transactional
    public boolean cancelBooking(Long bookingId, Long userId, boolean isAdmin) {
        logger.debug("INIZIO METODO annullaPrenotazione");
        logger.debug("Richiesta annullamento prenotazione - PrenotazioneId: {}, UtenteId: {}", bookingId, userId);
        Optional<Booking> booking = bookingRepository.findById(bookingId);
        
        if (booking.isEmpty()) {
            throw ResourceNotFoundException.perId("Prenotazione", "PRENOTAZIONE_NOT_FOUND", bookingId);
        }
        
        logger.debug("Verifica permessi annullamento prenotazione - PrenotazioneId: {}, UtenteId: {}", bookingId, userId);
        Booking p = booking.get();
        
        // Solo il creatore o un admin può annullare

        logger.debug("Verifica permessi annullamento prenotazione - PrenotazioneId: {}, UtenteId: {}", bookingId, userId);
        boolean isCreatore = p.getUser().getId().equals(userId);
                
        if (!isCreatore && !isAdmin) {
            // AccessDeniedException e non un booleano: il gestore globale la traduce gia'
            // in 403. Prima il controller doveva RIFARE questo stesso controllo per capire
            // se il false significasse "non autorizzato" o qualcos'altro.
            throw new org.springframework.security.access.AccessDeniedException(
                    "Puoi annullare solo le tue prenotazioni.");
        }

        // Solo una prenotazione attiva puo' essere annullata da questo endpoint. Senza questo
        // controllo un secondo annullamento riusciva e rispondeva "annullata con successo"
        // pur non cambiando nulla; i blocchi e le manutenzioni, che non sono "booked",
        // si annullano dall'endpoint admin (annullaPrenotazioneAsAdmin, volutamente permissivo
        // sullo stato). La regola e' sullo stato, non sul ruolo: vale anche per gli admin.
        if (!p.getStatus().isActive()) {
            // 409: la prenotazione esiste ed e' visibile, ma il suo stato non ammette
            // l'annullamento. Non e' "non trovata" e non e' "non autorizzato".
            throw new DomainConflictException("INVALID_STATE",
                    "Prenotazione " + bookingId + " in state " + p.getStatus().getValue(),
                    "Questa prenotazione non puo' essere annullata nello stato attuale.");
        }

        p.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(p);
        
        // Aggiorna lo stato dell'aula
        updateRoomStatus(p.getRoom().getId());
        
        logger.info("Prenotazione ID {} annullata con successo da Utente ID {}", bookingId, userId);
        logger.debug("FINE METODO annullaPrenotazione");
        return true;
    }
    
    // Lista tutte le prenotazioni per gestione admin
    public List<Booking> getAllBookings() {
        logger.debug("INIZIO METODO getAllPrenotazioni");
        logger.debug("Recupero tutte le prenotazioni dal database");
        List<Booking> bookings = bookingRepository.findAll();
        logger.debug("Recuperate {} prenotazioni totali", bookings.size());
        logger.debug("FINE METODO getAllPrenotazioni");
        return bookings;
    }
    
    // Lista prenotazioni per utente
    public List<Booking> getUserBookings(Long userId) {
        logger.debug("INIZIO METODO getPrenotazioniUtente");
        logger.debug("Recupero prenotazioni per utente - UtenteId: {}", userId);
        List<Booking> bookings = bookingRepository.findByUserId(userId);
        logger.debug("Recuperate {} prenotazioni per utente ID {}", bookings.size(), userId);
        logger.debug("FINE METODO getPrenotazioniUtente");
        return bookings;
    }
    
    // Ottieni dettagli completi per una specifica aula
    public List<BookingDetailDto> getRoomCompleteDetails(Long roomId) {
        logger.debug("INIZIO METODO getRoomCompleteDetails");
        logger.debug("Recupero dettagli completi per aula - AulaId: {}", roomId);
        logger.debug("FINE METODO getRoomCompleteDetails");
        return bookingRepository.findCompleteDetailsByRoomId(roomId);
    }
    
    // Ottieni dettagli completi di tutte le prenotazioni
    public List<BookingDetailDto> getAllCompleteDetails() {
        logger.debug("INIZIO METODO getAllCompleteDetails");
        logger.debug("Recupero dettagli completi per tutte le prenotazioni");
        logger.debug("FINE METODO getAllCompleteDetails");
        return bookingRepository.findAllCompleteDetails();
    }
    
    // Ottieni una singola prenotazione per ID
    public Booking getBookingById(Long id) {
        logger.debug("INIZIO METODO getPrenotazioneById");
        logger.debug("Recupero prenotazione per ID - PrenotazioneId: {}", id);
        Optional<Booking> booking = bookingRepository.findById(id);
        logger.debug("FINE METODO getPrenotazioneById");
        return booking.orElse(null);
    }
    
    // Ottieni dettagli completi per una singola prenotazione
    public List<BookingDetailDto> getBookingCompleteDetails(Long bookingId) {
        logger.debug("INIZIO METODO getPrenotazioneCompleteDetails");
        logger.debug("Recupero dettagli completi per prenotazione - PrenotazioneId: {}", bookingId);
        logger.debug("FINE METODO getPrenotazioneCompleteDetails");
        return bookingRepository.findCompleteDetailsByBookingId(bookingId);
    }
    
    // Lista prenotazioni per stato
    public List<Booking> getBookingsByStatus(String status) {
        logger.debug("INIZIO METODO getPrenotazioniByStato");
        logger.debug("Recupero prenotazioni per stato - Stato: {}", status);
        logger.debug("FINE METODO getPrenotazioniByStato");
        return bookingRepository.findByStatus(BookingStatus.from(status));
    }
    
    // Lista prenotazioni future
    public List<Booking> getFutureBookings() {
        logger.debug("INIZIO METODO getPrenotazioniFuture");
        logger.debug("Recupero prenotazioni future a partire da ora");
        logger.debug("FINE METODO getPrenotazioniFuture");
        return bookingRepository.findFutureBookings(LocalDateTime.now());
    }
    
    // Metodo admin per annullare qualsiasi prenotazione
    @Transactional
    public boolean cancelBookingAsAdmin(Long bookingId, Long adminId, String motivo) {
        logger.debug("INIZIO METODO annullaPrenotazioneAsAdmin");
        logger.debug("Richiesta annullamento prenotazione da admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", bookingId, adminId, motivo);
        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            throw ResourceNotFoundException.perId("Prenotazione", "PRENOTAZIONE_NOT_FOUND", bookingId);
        }
        
        logger.debug("Richiesta annullamento prenotazione da admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", bookingId, adminId, motivo);
        Booking booking = bookingOpt.get();
        
        // Il ruolo admin e' gia' stato verificato dal filtro JWT e da @PreAuthorize:
        // rileggerlo qui richiederebbe una chiamata ad auth-service a ogni cancellazione.
        
        logger.debug("Annullamento prenotazione da parte dell'admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", bookingId, adminId, motivo);
        // Gli admin possono eliminare qualsiasi prenotazione, indipendentemente dallo stato
        booking.setStatus(BookingStatus.CANCELLED);
        
        logger.debug("Aggiornamento descrizione prenotazione per indicare azione admin - PrenotazioneId: {}, AdminId: {}, Motivo: {}", bookingId, adminId, motivo);
        // Aggiorna la descrizione per indicare l'azione admin
        String descrizioneOriginale = booking.getDescription() != null ? booking.getDescription() : "";
        String nuovaDescrizione = descrizioneOriginale + 
            (descrizioneOriginale.isEmpty() ? "" : " | ") +
            "CANCELLED DALL'AMMINISTRATORE: " + motivo;
        booking.setDescription(nuovaDescrizione);

        logger.debug("Salvataggio prenotazione aggiornata - PrenotazioneId: {}", bookingId);
        bookingRepository.save(booking);
        
        // Aggiorna lo stato dell'aula
        updateRoomStatus(booking.getRoom().getId());
        
        logger.debug("FINE METODO annullaPrenotazioneAsAdmin");
        return true;
    }

    // Aggiorna una prenotazione esistente
    @Transactional
    public Booking updateBooking(Long bookingId, Long roomId, Long courseId, Long userId, boolean isAdmin, LocalDateTime startTime, LocalDateTime endTime, String description) {
        logger.debug("INIZIO METODO updatePrenotazione");
        logger.debug("Richiesta aggiornamento prenotazione - PrenotazioneId: {}, AulaId: {}, CorsoId: {}, UtenteId: {}, Periodo: {} - {}", bookingId, roomId, courseId, userId, startTime, endTime);
        
        // Trova la prenotazione esistente
        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            throw ResourceNotFoundException.perId("Prenotazione", "PRENOTAZIONE_NOT_FOUND", bookingId);
        }
        
        Booking booking = bookingOpt.get();
        
        // Verifica autorizzazione - solo il creatore o un admin può modificare
        boolean isCreatore = booking.getUser().getId().equals(userId);
                
        if (!isCreatore && !isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Puoi modificare solo le tue prenotazioni.");
        }
        
        // Verifica che l'aula esista
        Optional<Room> room = roomRepository.findById(roomId);
        if (room.isEmpty()) {
            // 404 e non piu' 409: aula inesistente e aula occupata erano entrambe un null,
            // e il controller le presentava tutte come conflitto. Sono cose diverse.
            throw ResourceNotFoundException.perId("Aula", "ROOM_NOT_FOUND", roomId);
        }
        
        // Verifica disponibilità aula per il nuovo periodo (escludendo questa prenotazione)
        if (!isRoomAvailableExcluding(roomId, startTime, endTime, bookingId)) {
            throw new BookingConflictException("UPDATE_CONFLICT",
                    "Aula " + roomId + " busy from " + startTime + " to " + endTime,
                    "L'aula non e' disponibile nel nuovo periodo richiesto.");
        }
        
        // Corso opzionale
        Optional<Course> course = Optional.empty();
        if (courseId != null) {
            course = courseRepository.findById(courseId);
            if (course.isEmpty()) {
                // Il corso e' facoltativo, ma se indicato deve esistere: passarne uno
                // inesistente e' un errore del chiamante, non una prenotazione libera.
                throw ResourceNotFoundException.perId("Corso", "COURSE_NOT_FOUND", courseId);
            }
        }
        
        // Aggiorna i campi
        logger.debug("Aggiornamento campi prenotazione - PrenotazioneId: {}", bookingId);
        booking.setRoom(room.get());
        booking.setCourse(course.orElse(null));
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setDescription(description);
        
        Booking savedPrenotazione = bookingRepository.save(booking);
        logger.info("Prenotazione aggiornata - id={} aula='{}' utenteId={} periodo={} - {}", savedPrenotazione.getId(), room.get().getName(), userId, startTime, endTime);
        logger.debug("FINE METODO updatePrenotazione");
        return savedPrenotazione;
    }
    
    // Verifica disponibilità aula escludendo una prenotazione specifica
    private boolean isRoomAvailableExcluding(Long roomId, LocalDateTime startTime, LocalDateTime endTime, Long excludedBookingId) {
        logger.debug("Verifica disponibilità aula escludendo prenotazione - AulaId: {}, Periodo: {} - {}, Esclusa: {}", roomId, startTime, endTime, excludedBookingId);
        List<Booking> conflitti = bookingRepository.findConflictingBookingsExcluding(roomId, startTime, endTime, excludedBookingId);
        boolean disponibile = conflitti.isEmpty();
        logger.debug("Risultato verifica disponibilità aula (esclusa prenotazione {}) - AulaId: {}, Disponibile: {}", excludedBookingId, roomId, disponibile);
        return disponibile;
    }
}
