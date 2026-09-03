package com.prenotazioni.prenotazione.service;

import com.prenotazioni.exception.DomainConflictException;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.prenotazione.model.Aula;
import com.prenotazioni.prenotazione.model.DisponibilitaAula;
import com.prenotazioni.prenotazione.model.Prenotazione;
import com.prenotazioni.model.Ruolo;
import com.prenotazioni.prenotazione.model.StatoPrenotazione;
import com.prenotazioni.prenotazione.repository.IAulaRepository;
import com.prenotazioni.prenotazione.repository.IPrenotazioneRepository;
import com.prenotazioni.prenotazione.dto.AulaRequest;
import com.prenotazioni.prenotazione.dto.RoomDetailsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class AulaService {

    private static final Logger logger = LoggerFactory.getLogger(AulaService.class);

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FORMATO_ORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Con quanto anticipo una prenotazione futura fa gia' risultare l'aula "prenotata". */
    private static final int ORE_DI_PREAVVISO = 2;

    private static final String SCOPO_PREDEFINITO = "Lezione";
    private static final String MOTIVO_BLOCCO_PREDEFINITO = "Aula bloccata";
    /** Chi risulta autore di un blocco: i blocchi sono per definizione interventi admin. */
    private static final String BLOCCATA_DA = Ruolo.ADMIN.getValore();

    private final IAulaRepository aulaRepository;

    private final IPrenotazioneRepository prenotazioneRepository;

    AulaService(IAulaRepository aulaRepository, IPrenotazioneRepository prenotazioneRepository) {
        this.aulaRepository = aulaRepository;
        this.prenotazioneRepository = prenotazioneRepository;
    }

    // Ottieni tutte le aule
    public List<Aula> getAllAule() {
        logger.debug("getAllAule - Recupero tutte le aule");
        List<Aula> aule = aulaRepository.findAll();
        logger.debug("getAllAule - Totale aule recuperate: {}", aule.size());
        return aule;
    }

    // Ottieni una singola aula per ID
    public Optional<Aula> getAulaById(Long id) {
        logger.debug("getAulaById - ID: {}", id);
        Optional<Aula> aula = aulaRepository.findById(id);
        logger.debug("getAulaById - Aula trovata: {}", aula.isPresent());
        return aula;
    }

    // Crea una nuova aula
    public Aula createAula(AulaRequest request) {
        logger.debug("INIZIO createAula - Dati ricevuti: Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   request.getNome(), request.getCapienza(), request.getPiano(), request.isVirtual());
        
        if (aulaRepository.existsByNomeIgnoreCase(request.getNome())) {
            logger.debug("FINE createAula - Nome gia' esistente: {}", request.getNome());
            throw new DomainConflictException("ROOM_NAME_TAKEN",
                    "Nome aula gia' esistente: " + request.getNome(),
                    "Esiste gia' un'aula con questo nome.");
        }
        

        // Nessuna validazione qui: AulaRequest porta gia' @NotBlank, @Positive e
        // @PositiveOrZero e il controller usa @Valid, quindi Bean Validation respinge
        // prima e con un messaggio migliore. Ripeterle a mano significava tenere due
        // punti in cui la stessa regola poteva divergere.

        Aula aula = new Aula();
        aula.setNome(request.getNome().trim());
        aula.setCapienza(request.getCapienza());
        aula.setPiano(request.getPiano());
        aula.setVirtual(request.isVirtual());

        logger.debug("Validazioni superate, creazione aula - Dati finali: Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   aula.getNome(), aula.getCapienza(), aula.getPiano(), aula.isVirtual());

        // Nessun try/catch: un errore di salvataggio deve arrivare a GlobalExceptionHandler,
        // che sa tradurlo. Prima veniva ingoiato e restituito come null, e il controller lo
        // presentava come "verifica che il nome non sia gia' esistente" - una supposizione,
        // falsa quando la causa era il database. Il caso concreto e' la violazione di
        // aule.nome UNIQUE fra due creazioni concorrenti: ora e' un 409, non un 400.
        Aula savedAula = aulaRepository.save(aula);
        logger.debug("FINE createAula - Aula salvata con successo - ID: {}, Nome: {}", savedAula.getId(), savedAula.getNome());
        return savedAula;
    }

    // Aggiorna un'aula esistente
    public Aula updateAula(Long id, AulaRequest request) {
        logger.debug("INIZIO updateAula - ID: {}, Dati ricevuti: Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   id, request.getNome(), request.getCapienza(), request.getPiano(), request.isVirtual());
        
        Aula aula = aulaRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.perId("Aula", "ROOM_NOT_FOUND", id));
        logger.debug("Aula esistente trovata - Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   aula.getNome(), aula.getCapienza(), aula.getPiano(), aula.isVirtual());

        if (aulaRepository.existsByNomeIgnoreCaseAndIdNot(request.getNome(), id)) {
            logger.debug("FINE updateAula - Nome gia' esistente: {}", request.getNome());
            throw new DomainConflictException("ROOM_NAME_TAKEN",
                    "Nome aula gia' esistente: " + request.getNome(),
                    "Esiste gia' un'aula con questo nome.");
        }

        // Come in createAula: le validazioni le fa @Valid, non questo metodo.

        aula.setNome(request.getNome().trim());
        aula.setCapienza(request.getCapienza());
        aula.setPiano(request.getPiano());
        aula.setVirtual(request.isVirtual());

        logger.debug("Validazioni superate, aggiornamento aula - Dati finali: Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   aula.getNome(), aula.getCapienza(), aula.getPiano(), aula.isVirtual());

        // Come in createAula: l'errore va lasciato salire fino al gestore globale.
        Aula savedAula = aulaRepository.save(aula);
        logger.debug("FINE updateAula - Aula aggiornata con successo - ID: {}, Nome: {}", savedAula.getId(), savedAula.getNome());
        return savedAula;
    }

    // Elimina un'aula
    public void deleteAula(Long id) {
        logger.debug("INIZIO deleteAula - ID: {}", id);
        
        if (!aulaRepository.existsById(id)) {
            throw ResourceNotFoundException.perId("Aula", "ROOM_NOT_FOUND", id);
        }

        // false significava due cose opposte: "aula inesistente" (sopra) e "cancellazione
        // fallita" (qui). Ora significa solo la prima, e un errore vero sale al gestore.
        // Il caso realistico e' una prenotazione che referenzia l'aula: e' un 409, e
        // dirlo e' piu' utile che rispondere "aula non trovata" su un'aula che esiste.
        aulaRepository.deleteById(id);
        logger.debug("FINE deleteAula - Aula eliminata con successo - ID: {}", id);
    }

    // Filtra aule per piano
    public List<Aula> getAuleByPiano(int piano) {
        logger.debug("INIZIO getAuleByPiano - Piano: {}", piano);
        List<Aula> aule = aulaRepository.findByPiano(piano);
        logger.debug("FINE getAuleByPiano - Aule trovate: {}", aule.size());
        return aule;
    }
    
    // Filtra aule per capienza minima
    public List<Aula> getAuleByCapienzaMinima(int minCapienza) {
        logger.debug("INIZIO getAuleByCapienzaMinima - Capienza minima: {}", minCapienza);
        List<Aula> aule = aulaRepository.findByCapienzaGreaterThanEqual(minCapienza);
        logger.debug("FINE getAuleByCapienzaMinima - Aule trovate: {}", aule.size());
        return aule;
    }

    // Ottieni i dettagli completi di tutte le aule con informazioni di stato e prenotazioni
    public List<RoomDetailsResponse> getAllRoomsWithDetails() {
        logger.debug("INIZIO getAllRoomsWithDetails");
        List<RoomDetailsResponse> response = getRoomsDetailsFromList(aulaRepository.findAll());
        logger.debug("FINE getAllRoomsWithDetails - Dettagli elaborati per {} aule", response.size());
        return response;
    }

    // Ottieni i dettagli completi di una singola aula
    public RoomDetailsResponse getRoomWithDetails(Long aulaId) {
        logger.debug("INIZIO getRoomWithDetails - ID Aula: {}", aulaId);

        Aula aula = aulaRepository.findById(aulaId)
                .orElseThrow(() -> ResourceNotFoundException.perId("Aula", "ROOM_NOT_FOUND", aulaId));

        RoomDetailsResponse roomDetails = toRoomDetails(
                aula, prenotazioneRepository.findByAulaId(aula.getId()), LocalDateTime.now());

        logger.debug("FINE getRoomWithDetails - Dettagli elaborati per aula: {}", aulaId);
        return roomDetails;
    }
    
    // Metodi per gestire aule fisiche e virtuali
    
    
    // Ottieni aule fisiche ordinate per piano e nome
    public List<Aula> getPhysicalRoomsOrdered() {
        logger.debug("INIZIO getPhysicalRoomsOrdered - Recupero aule fisiche ordinate");
        List<Aula> aule = aulaRepository.findPhysicalRoomsOrderByPianoAndNome();
        logger.debug("FINE getPhysicalRoomsOrdered - Aule fisiche ordinate: {}", aule.size());
        return aule;
    }
    
    // Ottieni aule virtuali ordinate per nome
    public List<Aula> getVirtualRoomsOrdered() {
        logger.debug("INIZIO getVirtualRoomsOrdered - Recupero aule virtuali ordinate");
        List<Aula> aule = aulaRepository.findVirtualRoomsOrderByNome();
        logger.debug("FINE getVirtualRoomsOrdered - Aule virtuali ordinate: {}", aule.size());
        return aule;
    }
    
    // Ottieni i dettagli delle aule fisiche
    public List<RoomDetailsResponse> getPhysicalRoomsWithDetails() {
        logger.debug("INIZIO getPhysicalRoomsWithDetails - Recupero dettagli aule fisiche");
        List<Aula> aule = aulaRepository.findByIsVirtual(false);
        List<RoomDetailsResponse> details = getRoomsDetailsFromList(aule);
        logger.debug("FINE getPhysicalRoomsWithDetails - Dettagli elaborati: {}", details.size());
        return details;
    }
    
    // Ottieni i dettagli delle aule virtuali
    public List<RoomDetailsResponse> getVirtualRoomsWithDetails() {
        logger.debug("INIZIO getVirtualRoomsWithDetails - Recupero dettagli aule virtuali");
        List<Aula> aule = aulaRepository.findByIsVirtual(true);
        List<RoomDetailsResponse> details = getRoomsDetailsFromList(aule);
        logger.debug("FINE getVirtualRoomsWithDetails - Dettagli elaborati: {}", details.size());
        return details;
    }
    
    // Conta aule fisiche
    public long countPhysicalRooms() {
        logger.debug("INIZIO countPhysicalRooms - Conteggio aule fisiche");
        long count = aulaRepository.countByIsVirtual(false);
        logger.debug("FINE countPhysicalRooms - Totale aule fisiche: {}", count);
        return count;
    }
    
    // Conta aule virtuali  
    public long countVirtualRooms() {
        logger.debug("INIZIO countVirtualRooms - Conteggio aule virtuali");
        long count = aulaRepository.countByIsVirtual(true);
        logger.debug("FINE countVirtualRooms - Totale aule virtuali: {}", count);
        return count;
    }
    
    // Costruisce i dettagli per un elenco di aule gia' selezionato.
    private List<RoomDetailsResponse> getRoomsDetailsFromList(List<Aula> aule) {
        logger.debug("INIZIO getRoomsDetailsFromList - Elaborazione dettagli per {} aule", aule.size());

        // Un solo istante per tutte le aule. Prima LocalDateTime.now() veniva invocato
        // dentro il ciclo, quindi aule della stessa risposta potevano essere valutate
        // rispetto a momenti diversi.
        LocalDateTime adesso = LocalDateTime.now();

        // Una query per tutte le aule, non una per aula: prima l'elenco costava 1+N
        // interrogazioni, e con le relazioni EAGER di Prenotazione anche parecchie di piu'.
        List<Long> aulaIds = aule.stream().map(Aula::getId).toList();
        Map<Long, List<Prenotazione>> prenotazioniPerAula = aulaIds.isEmpty()
                ? Map.of()
                : prenotazioneRepository.findByAulaIdIn(aulaIds).stream()
                        .collect(Collectors.groupingBy(prenotazione -> prenotazione.getAula().getId()));

        List<RoomDetailsResponse> response = new ArrayList<>();
        for (Aula aula : aule) {
            response.add(toRoomDetails(
                    aula, prenotazioniPerAula.getOrDefault(aula.getId(), List.of()), adesso));
        }

        logger.debug("FINE getRoomsDetailsFromList - Completata elaborazione per {} aule", response.size());
        return response;
    }

    /**
     * Vista di dettaglio di UNA aula rispetto a un istante dato.
     *
     * Questo blocco esisteva in tre copie identiche (getAllRoomsWithDetails,
     * getRoomWithDetails e getRoomsDetailsFromList): una modifica alle regole di stato
     * andava replicata a mano tre volte, e bastava dimenticarne una perche' lo stesso
     * dato risultasse diverso a seconda dell'endpoint interrogato.
     *
     * L'istante arriva dal chiamante invece di essere letto qui: rende il metodo
     * deterministico e permette a un elenco di aule di condividere lo stesso "adesso".
     */
    private RoomDetailsResponse toRoomDetails(Aula aula, List<Prenotazione> prenotazioni, LocalDateTime adesso) {
        RoomDetailsResponse roomDetails = new RoomDetailsResponse(
                aula.getId(), aula.getNome(), aula.getPiano(), aula.getCapienza(), aula.isVirtual());

        DisponibilitaAula status = DisponibilitaAula.LIBERA;
        RoomDetailsResponse.CurrentBooking currentBooking = null;
        RoomDetailsResponse.BlockInfo blockInfo = null;

        // L'aula e' occupata o bloccata proprio adesso?
        for (Prenotazione prenotazione : prenotazioni) {
            if (prenotazione.getInizio().isBefore(adesso) && prenotazione.getFine().isAfter(adesso)) {
                if (prenotazione.getStato() == StatoPrenotazione.PRENOTATA) {
                    status = DisponibilitaAula.PRENOTATA;
                    currentBooking = toCurrentBooking(prenotazione);
                } else if (prenotazione.getStato().isInterventoAdmin()) {
                    status = DisponibilitaAula.BLOCCATA;
                    blockInfo = new RoomDetailsResponse.BlockInfo(
                        descrizioneOppure(prenotazione, MOTIVO_BLOCCO_PREDEFINITO),
                        BLOCCATA_DA,
                        prenotazione.getDataCreazione().toLocalDate().format(FORMATO_DATA)
                    );
                }
                break;
            }
        }

        // Se e' libera adesso, guarda se c'e' una prenotazione imminente.
        if (status == DisponibilitaAula.LIBERA) {
            LocalDateTime finePreavviso = adesso.plusHours(ORE_DI_PREAVVISO);
            for (Prenotazione prenotazione : prenotazioni) {
                if (prenotazione.getInizio().isAfter(adesso) && prenotazione.getInizio().isBefore(finePreavviso) &&
                    prenotazione.getStato() == StatoPrenotazione.PRENOTATA) {
                    status = DisponibilitaAula.PRENOTATA;
                    currentBooking = toCurrentBooking(prenotazione);
                    break;
                }
            }
        }

        List<RoomDetailsResponse.BookingInfo> bookingInfos = new ArrayList<>();
        for (Prenotazione prenotazione : prenotazioni) {
            if (prenotazione.getStato() == StatoPrenotazione.PRENOTATA) {
                bookingInfos.add(new RoomDetailsResponse.BookingInfo(
                    prenotazione.getInizio().toLocalDate().format(FORMATO_DATA),
                    prenotazione.getInizio().format(FORMATO_ORA),
                    prenotazione.getFine().format(FORMATO_ORA),
                    prenotazione.getUtente().getNome(),
                    descrizioneOppure(prenotazione, SCOPO_PREDEFINITO)
                ));
            }
        }

        roomDetails.setStatus(status);
        roomDetails.setBooking(currentBooking);
        roomDetails.setBlocked(blockInfo);
        roomDetails.setBookings(bookingInfos);
        return roomDetails;
    }

    private RoomDetailsResponse.CurrentBooking toCurrentBooking(Prenotazione prenotazione) {
        return new RoomDetailsResponse.CurrentBooking(
                prenotazione.getUtente().getNome(),
                prenotazione.getInizio().toLocalDate().format(FORMATO_DATA),
                prenotazione.getInizio().format(FORMATO_ORA) + "-" + prenotazione.getFine().format(FORMATO_ORA),
                descrizioneOppure(prenotazione, SCOPO_PREDEFINITO));
    }

    private static String descrizioneOppure(Prenotazione prenotazione, String predefinita) {
        return prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : predefinita;
    }
}