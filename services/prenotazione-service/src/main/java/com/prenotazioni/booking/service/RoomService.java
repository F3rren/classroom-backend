package com.prenotazioni.booking.service;

import com.prenotazioni.exception.DomainConflictException;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.exception.ResourceType;
import com.prenotazioni.booking.model.Room;
import com.prenotazioni.booking.model.RoomAvailability;
import com.prenotazioni.booking.model.Booking;
import com.prenotazioni.model.Role;
import com.prenotazioni.booking.model.BookingStatus;
import com.prenotazioni.booking.repository.RoomRepository;
import com.prenotazioni.booking.repository.BookingRepository;
import com.prenotazioni.booking.dto.RoomRequest;
import com.prenotazioni.booking.dto.RoomDetailsResponse;
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
public class RoomService {

    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FORMATO_ORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Con quanto anticipo una prenotazione futura fa gia' risultare l'aula "booked". */
    private static final int ORE_DI_PREAVVISO = 2;

    private static final String SCOPO_PREDEFINITO = "Lezione";
    private static final String MOTIVO_BLOCCO_PREDEFINITO = "Aula bloccata";
    /** Chi risulta autore di un blocco: i blocchi sono per definizione interventi admin. */
    private static final String BLOCCATA_DA = Role.ADMIN.getValue();

    private final RoomRepository roomRepository;

    private final BookingRepository bookingRepository;

    RoomService(RoomRepository roomRepository, BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    // Ottieni tutte le aule
    public List<Room> getAllRooms() {
        logger.debug("getAllAule - Recupero tutte le aule");
        List<Room> rooms = roomRepository.findAll();
        logger.debug("getAllAule - Totale aule recuperate: {}", rooms.size());
        return rooms;
    }

    // Ottieni una singola aula per ID
    public Optional<Room> getRoomById(Long id) {
        logger.debug("getAulaById - ID: {}", id);
        Optional<Room> room = roomRepository.findById(id);
        logger.debug("getAulaById - Aula trovata: {}", room.isPresent());
        return room;
    }

    // Crea una nuova aula
    public Room createRoom(RoomRequest request) {
        logger.debug("INIZIO createAula - Dati ricevuti: Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   request.getName(), request.getCapacity(), request.getFloor(), request.isVirtual());
        
        if (roomRepository.existsByNameIgnoreCase(request.getName())) {
            logger.debug("FINE createAula - Nome gia' esistente: {}", request.getName());
            throw new DomainConflictException("ROOM_NAME_TAKEN",
                    "Aula name already taken: " + request.getName(),
                    "Esiste gia' un'aula con questo nome.");
        }
        

        // Nessuna validazione qui: AulaRequest porta gia' @NotBlank, @Positive e
        // @PositiveOrZero e il controller usa @Valid, quindi Bean Validation respinge
        // prima e con un messaggio migliore. Ripeterle a mano significava tenere due
        // punti in cui la stessa regola poteva divergere.

        Room room = new Room();
        room.setName(request.getName().trim());
        room.setCapacity(request.getCapacity());
        room.setFloor(request.getFloor());
        room.setVirtual(request.isVirtual());

        logger.debug("Validazioni superate, creazione aula - Dati finali: Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   room.getName(), room.getCapacity(), room.getFloor(), room.isVirtual());

        // Nessun try/catch: un errore di salvataggio deve arrivare a GlobalExceptionHandler,
        // che sa tradurlo. Prima veniva ingoiato e restituito come null, e il controller lo
        // presentava come "verifica che il nome non sia gia' esistente" - una supposizione,
        // falsa quando la causa era il database. Il caso concreto e' la violazione di
        // aule.nome UNIQUE fra due creazioni concorrenti: ora e' un 409, non un 400.
        Room savedRoom = roomRepository.save(room);
        logger.debug("FINE createAula - Aula salvata con successo - ID: {}, Nome: {}", savedRoom.getId(), savedRoom.getName());
        return savedRoom;
    }

    // Aggiorna un'aula esistente
    public Room updateRoom(Long id, RoomRequest request) {
        logger.debug("INIZIO updateAula - ID: {}, Dati ricevuti: Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   id, request.getName(), request.getCapacity(), request.getFloor(), request.isVirtual());
        
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.forId(ResourceType.ROOM, id));
        logger.debug("Aula esistente trovata - Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   room.getName(), room.getCapacity(), room.getFloor(), room.isVirtual());

        if (roomRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            logger.debug("FINE updateAula - Nome gia' esistente: {}", request.getName());
            throw new DomainConflictException("ROOM_NAME_TAKEN",
                    "Aula name already taken: " + request.getName(),
                    "Esiste gia' un'aula con questo nome.");
        }

        // Come in createAula: le validazioni le fa @Valid, non questo metodo.

        room.setName(request.getName().trim());
        room.setCapacity(request.getCapacity());
        room.setFloor(request.getFloor());
        room.setVirtual(request.isVirtual());

        logger.debug("Validazioni superate, aggiornamento aula - Dati finali: Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   room.getName(), room.getCapacity(), room.getFloor(), room.isVirtual());

        // Come in createAula: l'errore va lasciato salire fino al gestore globale.
        Room savedRoom = roomRepository.save(room);
        logger.debug("FINE updateAula - Aula aggiornata con successo - ID: {}, Nome: {}", savedRoom.getId(), savedRoom.getName());
        return savedRoom;
    }

    // Elimina un'aula
    public void deleteRoom(Long id) {
        logger.debug("INIZIO deleteAula - ID: {}", id);
        
        if (!roomRepository.existsById(id)) {
            throw ResourceNotFoundException.forId(ResourceType.ROOM, id);
        }

        // false significava due cose opposte: "aula inesistente" (sopra) e "cancellazione
        // fallita" (qui). Ora significa solo la prima, e un errore vero sale al gestore.
        // Il caso realistico e' una prenotazione che referenzia l'aula: e' un 409, e
        // dirlo e' piu' utile che rispondere "aula non trovata" su un'aula che esiste.
        roomRepository.deleteById(id);
        logger.debug("FINE deleteAula - Aula eliminata con successo - ID: {}", id);
    }

    // Filtra aule per piano
    public List<Room> getRoomsByFloor(int floor) {
        logger.debug("INIZIO getAuleByPiano - Piano: {}", floor);
        List<Room> rooms = roomRepository.findByFloor(floor);
        logger.debug("FINE getAuleByPiano - Aule trovate: {}", rooms.size());
        return rooms;
    }
    
    // Filtra aule per capienza minima
    public List<Room> getRoomsByMinCapacity(int minCapacity) {
        logger.debug("INIZIO getAuleByCapienzaMinima - Capienza minima: {}", minCapacity);
        List<Room> rooms = roomRepository.findByCapacityGreaterThanEqual(minCapacity);
        logger.debug("FINE getAuleByCapienzaMinima - Aule trovate: {}", rooms.size());
        return rooms;
    }

    // Ottieni i dettagli completi di tutte le aule con informazioni di stato e prenotazioni
    public List<RoomDetailsResponse> getAllRoomsWithDetails() {
        logger.debug("INIZIO getAllRoomsWithDetails");
        List<RoomDetailsResponse> response = getRoomsDetailsFromList(roomRepository.findAll());
        logger.debug("FINE getAllRoomsWithDetails - Dettagli elaborati per {} aule", response.size());
        return response;
    }

    // Ottieni i dettagli completi di una singola aula
    public RoomDetailsResponse getRoomWithDetails(Long roomId) {
        logger.debug("INIZIO getRoomWithDetails - ID Aula: {}", roomId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> ResourceNotFoundException.forId(ResourceType.ROOM, roomId));

        RoomDetailsResponse roomDetails = toRoomDetails(
                room, bookingRepository.findByRoomId(room.getId()), LocalDateTime.now());

        logger.debug("FINE getRoomWithDetails - Dettagli elaborati per aula: {}", roomId);
        return roomDetails;
    }
    
    // Metodi per gestire aule fisiche e virtuali
    
    
    // Ottieni aule fisiche ordinate per piano e nome
    public List<Room> getPhysicalRoomsOrdered() {
        logger.debug("INIZIO getPhysicalRoomsOrdered - Recupero aule fisiche ordinate");
        List<Room> rooms = roomRepository.findPhysicalRoomsOrderByFloorAndName();
        logger.debug("FINE getPhysicalRoomsOrdered - Aule fisiche ordinate: {}", rooms.size());
        return rooms;
    }
    
    // Ottieni aule virtuali ordinate per nome
    public List<Room> getVirtualRoomsOrdered() {
        logger.debug("INIZIO getVirtualRoomsOrdered - Recupero aule virtuali ordinate");
        List<Room> rooms = roomRepository.findVirtualRoomsOrderByNome();
        logger.debug("FINE getVirtualRoomsOrdered - Aule virtuali ordinate: {}", rooms.size());
        return rooms;
    }
    
    // Ottieni i dettagli delle aule fisiche
    public List<RoomDetailsResponse> getPhysicalRoomsWithDetails() {
        logger.debug("INIZIO getPhysicalRoomsWithDetails - Recupero dettagli aule fisiche");
        List<Room> rooms = roomRepository.findByIsVirtual(false);
        List<RoomDetailsResponse> details = getRoomsDetailsFromList(rooms);
        logger.debug("FINE getPhysicalRoomsWithDetails - Dettagli elaborati: {}", details.size());
        return details;
    }
    
    // Ottieni i dettagli delle aule virtuali
    public List<RoomDetailsResponse> getVirtualRoomsWithDetails() {
        logger.debug("INIZIO getVirtualRoomsWithDetails - Recupero dettagli aule virtuali");
        List<Room> rooms = roomRepository.findByIsVirtual(true);
        List<RoomDetailsResponse> details = getRoomsDetailsFromList(rooms);
        logger.debug("FINE getVirtualRoomsWithDetails - Dettagli elaborati: {}", details.size());
        return details;
    }
    
    // Conta aule fisiche
    public long countPhysicalRooms() {
        logger.debug("INIZIO countPhysicalRooms - Conteggio aule fisiche");
        long count = roomRepository.countByIsVirtual(false);
        logger.debug("FINE countPhysicalRooms - Totale aule fisiche: {}", count);
        return count;
    }
    
    // Conta aule virtuali  
    public long countVirtualRooms() {
        logger.debug("INIZIO countVirtualRooms - Conteggio aule virtuali");
        long count = roomRepository.countByIsVirtual(true);
        logger.debug("FINE countVirtualRooms - Totale aule virtuali: {}", count);
        return count;
    }
    
    // Costruisce i dettagli per un elenco di aule gia' selezionato.
    private List<RoomDetailsResponse> getRoomsDetailsFromList(List<Room> rooms) {
        logger.debug("INIZIO getRoomsDetailsFromList - Elaborazione dettagli per {} aule", rooms.size());

        // Un solo istante per tutte le aule. Prima LocalDateTime.now() veniva invocato
        // dentro il ciclo, quindi aule della stessa risposta potevano essere valutate
        // rispetto a momenti diversi.
        LocalDateTime adesso = LocalDateTime.now();

        // Una query per tutte le aule, non una per aula: prima l'elenco costava 1+N
        // interrogazioni, e con le relazioni EAGER di Prenotazione anche parecchie di piu'.
        List<Long> roomIds = rooms.stream().map(Room::getId).toList();
        Map<Long, List<Booking>> bookingsByRoom = roomIds.isEmpty()
                ? Map.of()
                : bookingRepository.findByRoomIdIn(roomIds).stream()
                        .collect(Collectors.groupingBy(booking -> booking.getRoom().getId()));

        List<RoomDetailsResponse> response = new ArrayList<>();
        for (Room room : rooms) {
            response.add(toRoomDetails(
                    room, bookingsByRoom.getOrDefault(room.getId(), List.of()), adesso));
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
    private RoomDetailsResponse toRoomDetails(Room room, List<Booking> bookings, LocalDateTime adesso) {
        RoomDetailsResponse roomDetails = new RoomDetailsResponse(
                room.getId(), room.getName(), room.getFloor(), room.getCapacity(), room.isVirtual());

        RoomAvailability status = RoomAvailability.FREE;
        RoomDetailsResponse.CurrentBooking currentBooking = null;
        RoomDetailsResponse.BlockInfo blockInfo = null;

        // L'aula e' occupata o bloccata proprio adesso?
        for (Booking booking : bookings) {
            if (booking.getStartTime().isBefore(adesso) && booking.getEndTime().isAfter(adesso)) {
                if (booking.getStatus() == BookingStatus.BOOKED) {
                    status = RoomAvailability.BOOKED;
                    currentBooking = toCurrentBooking(booking);
                } else if (booking.getStatus().isAdminIntervention()) {
                    status = RoomAvailability.BLOCKED;
                    blockInfo = new RoomDetailsResponse.BlockInfo(
                        descrizioneOppure(booking, MOTIVO_BLOCCO_PREDEFINITO),
                        BLOCCATA_DA,
                        booking.getCreatedAt().toLocalDate().format(FORMATO_DATA)
                    );
                }
                break;
            }
        }

        // Se e' libera adesso, guarda se c'e' una prenotazione imminente.
        if (status == RoomAvailability.FREE) {
            LocalDateTime noticeEnd = adesso.plusHours(ORE_DI_PREAVVISO);
            for (Booking booking : bookings) {
                if (booking.getStartTime().isAfter(adesso) && booking.getStartTime().isBefore(noticeEnd) &&
                    booking.getStatus() == BookingStatus.BOOKED) {
                    status = RoomAvailability.BOOKED;
                    currentBooking = toCurrentBooking(booking);
                    break;
                }
            }
        }

        List<RoomDetailsResponse.BookingInfo> bookingInfos = new ArrayList<>();
        for (Booking booking : bookings) {
            if (booking.getStatus() == BookingStatus.BOOKED) {
                bookingInfos.add(new RoomDetailsResponse.BookingInfo(
                    booking.getStartTime().toLocalDate().format(FORMATO_DATA),
                    booking.getStartTime().format(FORMATO_ORA),
                    booking.getEndTime().format(FORMATO_ORA),
                    booking.getUser().getName(),
                    descrizioneOppure(booking, SCOPO_PREDEFINITO)
                ));
            }
        }

        roomDetails.setStatus(status);
        roomDetails.setBooking(currentBooking);
        roomDetails.setBlocked(blockInfo);
        roomDetails.setBookings(bookingInfos);
        return roomDetails;
    }

    private RoomDetailsResponse.CurrentBooking toCurrentBooking(Booking booking) {
        return new RoomDetailsResponse.CurrentBooking(
                booking.getUser().getName(),
                booking.getStartTime().toLocalDate().format(FORMATO_DATA),
                booking.getStartTime().format(FORMATO_ORA) + "-" + booking.getEndTime().format(FORMATO_ORA),
                descrizioneOppure(booking, SCOPO_PREDEFINITO));
    }

    private static String descrizioneOppure(Booking booking, String predefinita) {
        return booking.getDescription() != null ? booking.getDescription() : predefinita;
    }
}