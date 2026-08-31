package com.prenotazioni.service;

import com.prenotazioni.model.Aula;
import com.prenotazioni.model.Prenotazione;
import com.prenotazioni.model.StatoPrenotazione;
import com.prenotazioni.repository.IAulaRepository;
import com.prenotazioni.repository.IPrenotazioneRepository;
import com.prenotazioni.dto.AulaRequest;
import com.prenotazioni.dto.RoomDetailsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

@Service
public class AulaService {

    private static final Logger logger = LoggerFactory.getLogger(AulaService.class);

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
        
        // Verifica che il nome non sia già esistente
        if (aulaRepository.existsByNomeIgnoreCase(request.getNome())) {
            logger.debug("FINE createAula - Nome già esistente: {}", request.getNome());
            return null; // Nome già esistente
        }
        

        // Validazioni
        if (request.getNome() == null || request.getNome().trim().isEmpty()) {
            logger.debug("FINE createAula - Nome non valido");
            return null; // Nome non valido
        }
        if (request.getCapienza() <= 0) {
            logger.debug("FINE createAula - Capienza non valida: {}", request.getCapienza());
            return null; // Capienza non valida
        }
        if (request.getPiano() < 0) {
            logger.debug("FINE createAula - Piano non valido: {}", request.getPiano());
            return null; // Piano non valido
        }

        Aula aula = new Aula();
        aula.setNome(request.getNome().trim());
        aula.setCapienza(request.getCapienza());
        aula.setPiano(request.getPiano());
        aula.setVirtual(request.isVirtual());

        logger.debug("Validazioni superate, creazione aula - Dati finali: Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   aula.getNome(), aula.getCapienza(), aula.getPiano(), aula.isVirtual());

        try {
            Aula savedAula = aulaRepository.save(aula);
            logger.debug("FINE createAula - Aula salvata con successo - ID: {}, Nome: {}", savedAula.getId(), savedAula.getNome());
            return savedAula;
        } catch (Exception e) {
            logger.error("FINE createAula - Errore durante il salvataggio dell'aula: {}", e.getMessage(), e);
            return null;
        }
    }

    // Aggiorna un'aula esistente
    public Aula updateAula(Long id, AulaRequest request) {
        logger.debug("INIZIO updateAula - ID: {}, Dati ricevuti: Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   id, request.getNome(), request.getCapienza(), request.getPiano(), request.isVirtual());
        
        Optional<Aula> aulaOptional = aulaRepository.findById(id);
        if (aulaOptional.isEmpty()) {
            logger.debug("FINE updateAula - Aula non trovata con ID: {}", id);
            return null; // Aula non trovata
        }

        Aula aula = aulaOptional.get();
        logger.debug("Aula esistente trovata - Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   aula.getNome(), aula.getCapienza(), aula.getPiano(), aula.isVirtual());

        // Verifica che il nome non sia già esistente (escludendo l'aula corrente)
        if (aulaRepository.existsByNomeIgnoreCaseAndIdNot(request.getNome(), id)) {
            logger.debug("FINE updateAula - Nome già esistente: {}", request.getNome());
            return null; // Nome già esistente
        }

        // Validazioni
        if (request.getNome() == null || request.getNome().trim().isEmpty()) {
            logger.debug("FINE updateAula - Nome non valido");
            return null; // Nome non valido
        }
        if (request.getCapienza() <= 0) {
            logger.debug("FINE updateAula - Capienza non valida: {}", request.getCapienza());
            return null; // Capienza non valida
        }
        if (request.getPiano() < 0) {
            logger.debug("FINE updateAula - Piano non valido: {}", request.getPiano());
            return null; // Piano non valido
        }

        aula.setNome(request.getNome().trim());
        aula.setCapienza(request.getCapienza());
        aula.setPiano(request.getPiano());
        aula.setVirtual(request.isVirtual());

        logger.debug("Validazioni superate, aggiornamento aula - Dati finali: Nome: {}, Capienza: {}, Piano: {}, isVirtual: {}", 
                   aula.getNome(), aula.getCapienza(), aula.getPiano(), aula.isVirtual());

        try {
            Aula savedAula = aulaRepository.save(aula);
            logger.debug("FINE updateAula - Aula aggiornata con successo - ID: {}, Nome: {}", savedAula.getId(), savedAula.getNome());
            return savedAula;
        } catch (Exception e) {
            logger.error("FINE updateAula - Errore durante l'aggiornamento dell'aula: {}", e.getMessage(), e);
            return null;
        }
    }

    // Elimina un'aula
    public boolean deleteAula(Long id) {
        logger.debug("INIZIO deleteAula - ID: {}", id);
        
        Optional<Aula> aulaOptional = aulaRepository.findById(id);
        if (aulaOptional.isEmpty()) {
            logger.debug("FINE deleteAula - Aula non trovata con ID: {}", id);
            return false; // Aula non trovata
        }

        try {
            aulaRepository.deleteById(id);
            logger.debug("FINE deleteAula - Aula eliminata con successo - ID: {}", id);
            return true;
        } catch (Exception e) {
            logger.error("FINE deleteAula - Errore durante l'eliminazione dell'aula ID: {}, Errore: {}", id, e.getMessage(), e);
            return false; // Errore durante l'eliminazione
        }
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
        List<Aula> aule = aulaRepository.findAll();
        logger.debug("Recuperate {} aule per elaborazione dettagli", aule.size());
        
        List<RoomDetailsResponse> response = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        
        for (Aula aula : aule) {
            RoomDetailsResponse roomDetails = new RoomDetailsResponse(aula.getId(), aula.getNome(), aula.getPiano(), aula.getCapienza(), aula.isVirtual());
            
            // Ottieni tutte le prenotazioni per questa aula
            List<Prenotazione> prenotazioni = prenotazioneRepository.findByAulaId(aula.getId());
            
            // Determina lo stato dell'aula
            String status = "libera";
            RoomDetailsResponse.CurrentBooking currentBooking = null;
            RoomDetailsResponse.BlockInfo blockInfo = null;
            
            LocalDateTime now = LocalDateTime.now();
            
            // Controllo se l'aula è attualmente occupata o bloccata
            for (Prenotazione prenotazione : prenotazioni) {
                if (prenotazione.getInizio().isBefore(now) && prenotazione.getFine().isAfter(now)) {
                    if (prenotazione.getStato() == StatoPrenotazione.PRENOTATA) {
                        status = "prenotata";
                        currentBooking = new RoomDetailsResponse.CurrentBooking(
                            prenotazione.getUtente().getNome(),
                            prenotazione.getInizio().toLocalDate().format(dateFormatter),
                            prenotazione.getInizio().format(timeFormatter) + "-" + prenotazione.getFine().format(timeFormatter),
                            prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "Lezione"
                        );
                    } else if (prenotazione.getStato().isInterventoAdmin()) {
                        status = "bloccata";
                        blockInfo = new RoomDetailsResponse.BlockInfo(
                            prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "Aula bloccata",
                            "admin",
                            prenotazione.getDataCreazione().toLocalDate().format(dateFormatter)
                        );
                    }
                    break;
                }
            }
            
            // Se non è attualmente occupata, controlla se ci sono prenotazioni future nelle prossime 2 ore
            if (status.equals("libera")) {
                LocalDateTime twoHoursLater = now.plusHours(2);
                for (Prenotazione prenotazione : prenotazioni) {
                    if (prenotazione.getInizio().isAfter(now) && prenotazione.getInizio().isBefore(twoHoursLater) &&
                        prenotazione.getStato() == StatoPrenotazione.PRENOTATA) {
                        status = "prenotata";
                        currentBooking = new RoomDetailsResponse.CurrentBooking(
                            prenotazione.getUtente().getNome(),
                            prenotazione.getInizio().toLocalDate().format(dateFormatter),
                            prenotazione.getInizio().format(timeFormatter) + "-" + prenotazione.getFine().format(timeFormatter),
                            prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "Lezione"
                        );
                        break;
                    }
                }
            }
            
            // Crea la lista delle prenotazioni
            List<RoomDetailsResponse.BookingInfo> bookingInfos = new ArrayList<>();
            for (Prenotazione prenotazione : prenotazioni) {
                if (prenotazione.getStato() == StatoPrenotazione.PRENOTATA) {
                    bookingInfos.add(new RoomDetailsResponse.BookingInfo(
                        prenotazione.getInizio().toLocalDate().format(dateFormatter),
                        prenotazione.getInizio().format(timeFormatter),
                        prenotazione.getFine().format(timeFormatter),
                        prenotazione.getUtente().getNome(),
                        prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "Lezione"
                    ));
                }
            }
            
            roomDetails.setStatus(status);
            roomDetails.setBooking(currentBooking);
            roomDetails.setBlocked(blockInfo);
            roomDetails.setBookings(bookingInfos);
            
            response.add(roomDetails);
        }
        
        logger.debug("FINE getAllRoomsWithDetails - Dettagli elaborati per {} aule", response.size());
        return response;
    }

    // Ottieni i dettagli completi di una singola aula
    public RoomDetailsResponse getRoomWithDetails(Long aulaId) {
        logger.debug("INIZIO getRoomWithDetails - ID Aula: {}", aulaId);
        
        Optional<Aula> aulaOpt = aulaRepository.findById(aulaId);
        if (aulaOpt.isEmpty()) {
            logger.debug("FINE getRoomWithDetails - Aula non trovata con ID: {}", aulaId);
            return null;
        }
        
        Aula aula = aulaOpt.get();
        RoomDetailsResponse roomDetails = new RoomDetailsResponse(aula.getId(), aula.getNome(), aula.getPiano(), aula.getCapienza(), aula.isVirtual());
        
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        
        // Ottieni tutte le prenotazioni per questa aula
        List<Prenotazione> prenotazioni = prenotazioneRepository.findByAulaId(aula.getId());
        
        // Determina lo stato dell'aula
        String status = "libera";
        RoomDetailsResponse.CurrentBooking currentBooking = null;
        RoomDetailsResponse.BlockInfo blockInfo = null;
        
        LocalDateTime now = LocalDateTime.now();
        
        // Controllo se l'aula è attualmente occupata o bloccata
        for (Prenotazione prenotazione : prenotazioni) {
            if (prenotazione.getInizio().isBefore(now) && prenotazione.getFine().isAfter(now)) {
                if (prenotazione.getStato() == StatoPrenotazione.PRENOTATA) {
                    status = "prenotata";
                    currentBooking = new RoomDetailsResponse.CurrentBooking(
                        prenotazione.getUtente().getNome(),
                        prenotazione.getInizio().toLocalDate().format(dateFormatter),
                        prenotazione.getInizio().format(timeFormatter) + "-" + prenotazione.getFine().format(timeFormatter),
                        prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "Lezione"
                    );
                } else if (prenotazione.getStato().isInterventoAdmin()) {
                    status = "bloccata";
                    blockInfo = new RoomDetailsResponse.BlockInfo(
                        prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "Aula bloccata",
                        "admin",
                        prenotazione.getDataCreazione().toLocalDate().format(dateFormatter)
                    );
                }
                break;
            }
        }
        
        // Se non è attualmente occupata, controlla se ci sono prenotazioni future nelle prossime 2 ore
        if (status.equals("libera")) {
            LocalDateTime twoHoursLater = now.plusHours(2);
            for (Prenotazione prenotazione : prenotazioni) {
                if (prenotazione.getInizio().isAfter(now) && prenotazione.getInizio().isBefore(twoHoursLater) &&
                    prenotazione.getStato() == StatoPrenotazione.PRENOTATA) {
                    status = "prenotata";
                    currentBooking = new RoomDetailsResponse.CurrentBooking(
                        prenotazione.getUtente().getNome(),
                        prenotazione.getInizio().toLocalDate().format(dateFormatter),
                        prenotazione.getInizio().format(timeFormatter) + "-" + prenotazione.getFine().format(timeFormatter),
                        prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "Lezione"
                    );
                    break;
                }
            }
        }
        
        // Crea la lista delle prenotazioni
        List<RoomDetailsResponse.BookingInfo> bookingInfos = new ArrayList<>();
        for (Prenotazione prenotazione : prenotazioni) {
            if (prenotazione.getStato() == StatoPrenotazione.PRENOTATA) {
                bookingInfos.add(new RoomDetailsResponse.BookingInfo(
                    prenotazione.getInizio().toLocalDate().format(dateFormatter),
                    prenotazione.getInizio().format(timeFormatter),
                    prenotazione.getFine().format(timeFormatter),
                    prenotazione.getUtente().getNome(),
                    prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "Lezione"
                ));
            }
        }
        
        roomDetails.setStatus(status);
        roomDetails.setBooking(currentBooking);
        roomDetails.setBlocked(blockInfo);
        roomDetails.setBookings(bookingInfos);
        
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
    
    // Metodo di utilità privato per evitare duplicazione del codice
    private List<RoomDetailsResponse> getRoomsDetailsFromList(List<Aula> aule) {
        logger.debug("INIZIO getRoomsDetailsFromList - Elaborazione dettagli per {} aule", aule.size());
        List<RoomDetailsResponse> response = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        
        for (Aula aula : aule) {
            RoomDetailsResponse roomDetails = new RoomDetailsResponse(aula.getId(), aula.getNome(), aula.getPiano(), aula.getCapienza(), aula.isVirtual());
            
            // Ottieni tutte le prenotazioni per questa aula
            List<Prenotazione> prenotazioni = prenotazioneRepository.findByAulaId(aula.getId());
            
            // Determina lo stato dell'aula
            String status = "libera";
            RoomDetailsResponse.CurrentBooking currentBooking = null;
            RoomDetailsResponse.BlockInfo blockInfo = null;
            
            LocalDateTime now = LocalDateTime.now();
            
            // Controllo se l'aula è attualmente occupata o bloccata
            for (Prenotazione prenotazione : prenotazioni) {
                if (prenotazione.getInizio().isBefore(now) && prenotazione.getFine().isAfter(now)) {
                    if (prenotazione.getStato() == StatoPrenotazione.PRENOTATA) {
                        status = "prenotata";
                        currentBooking = new RoomDetailsResponse.CurrentBooking(
                            prenotazione.getUtente().getNome(),
                            prenotazione.getInizio().toLocalDate().format(dateFormatter),
                            prenotazione.getInizio().format(timeFormatter) + "-" + prenotazione.getFine().format(timeFormatter),
                            prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "Lezione"
                        );
                    } else if (prenotazione.getStato().isInterventoAdmin()) {
                        status = "bloccata";
                        blockInfo = new RoomDetailsResponse.BlockInfo(
                            prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "Aula bloccata",
                            "admin",
                            prenotazione.getDataCreazione().toLocalDate().format(dateFormatter)
                        );
                    }
                    break;
                }
            }
            
            // Se non è attualmente occupata, controlla se ci sono prenotazioni future nelle prossime 2 ore
            if (status.equals("libera")) {
                LocalDateTime twoHoursLater = now.plusHours(2);
                for (Prenotazione prenotazione : prenotazioni) {
                    if (prenotazione.getInizio().isAfter(now) && prenotazione.getInizio().isBefore(twoHoursLater) &&
                        prenotazione.getStato() == StatoPrenotazione.PRENOTATA) {
                        status = "prenotata";
                        currentBooking = new RoomDetailsResponse.CurrentBooking(
                            prenotazione.getUtente().getNome(),
                            prenotazione.getInizio().toLocalDate().format(dateFormatter),
                            prenotazione.getInizio().format(timeFormatter) + "-" + prenotazione.getFine().format(timeFormatter),
                            prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "Lezione"
                        );
                        break;
                    }
                }
            }
            
            // Crea la lista delle prenotazioni
            List<RoomDetailsResponse.BookingInfo> bookingInfos = new ArrayList<>();
            for (Prenotazione prenotazione : prenotazioni) {
                if (prenotazione.getStato() == StatoPrenotazione.PRENOTATA) {
                    bookingInfos.add(new RoomDetailsResponse.BookingInfo(
                        prenotazione.getInizio().toLocalDate().format(dateFormatter),
                        prenotazione.getInizio().format(timeFormatter),
                        prenotazione.getFine().format(timeFormatter),
                        prenotazione.getUtente().getNome(),
                        prenotazione.getDescrizione() != null ? prenotazione.getDescrizione() : "Lezione"
                    ));
                }
            }
            
            roomDetails.setStatus(status);
            roomDetails.setBooking(currentBooking);
            roomDetails.setBlocked(blockInfo);
            roomDetails.setBookings(bookingInfos);
            
            response.add(roomDetails);
        }

        logger.debug("FINE getRoomsDetailsFromList - Completata elaborazione per {} aule", response.size());
        return response;
    }
}