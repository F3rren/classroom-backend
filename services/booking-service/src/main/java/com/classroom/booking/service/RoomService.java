package com.classroom.booking.service;

import com.classroom.exception.DomainConflictException;
import com.classroom.exception.ResourceNotFoundException;
import com.classroom.exception.ResourceType;
import com.classroom.booking.model.Room;
import com.classroom.booking.model.RoomAvailability;
import com.classroom.booking.model.Booking;
import com.classroom.model.Role;
import com.classroom.booking.model.BookingStatus;
import com.classroom.booking.repository.RoomRepository;
import com.classroom.booking.repository.BookingRepository;
import com.classroom.booking.dto.RoomRequest;
import com.classroom.booking.dto.RoomDetailsResponse;
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

    /** How far ahead a future booking already makes the room read as "booked". */
    private static final int ORE_DI_PREAVVISO = 2;

    private static final String SCOPO_PREDEFINITO = "Lezione";
    private static final String DEFAULT_BLOCK_REASON = "Aula bloccata";
    /** Who a block is attributed to: a block is by definition an admin intervention. */
    private static final String BLOCCATA_DA = Role.ADMIN.getValue();

    private final RoomRepository roomRepository;

    private final BookingRepository bookingRepository;

    RoomService(RoomRepository roomRepository, BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    // Every room.
    public List<Room> getAllRooms() {
        logger.debug("getAllRooms - fetching every room");
        List<Room> rooms = roomRepository.findAll();
        logger.debug("getAllRooms - total rooms fetched: {}", rooms.size());
        return rooms;
    }

    // A single room by id.
    public Optional<Room> getRoomById(Long id) {
        logger.debug("getroomById - ID: {}", id);
        Optional<Room> room = roomRepository.findById(id);
        logger.debug("getroomById - room found: {}", room.isPresent());
        return room;
    }

    // Creates a room.
    public Room createRoom(RoomRequest request) {
        logger.debug("START createroom - data received: name: {}, capacity: {}, floor: {}, isVirtual: {}", 
                   request.getName(), request.getCapacity(), request.getFloor(), request.isVirtual());
        
        if (roomRepository.existsByNameIgnoreCase(request.getName())) {
            logger.debug("END createroom - name already taken: {}", request.getName());
            throw new DomainConflictException("ROOM_NAME_TAKEN",
                    "Room name already taken: " + request.getName(),
                    "Esiste gia' un'aula con questo nome.");
        }
        

        // No validation here: RoomRequest already carries @NotBlank, @Positive and
        // @PositiveOrZero and the controller uses @Valid, so Bean Validation rejects earlier
        // and with a better message. Repeating them by hand meant keeping two places where
        // the same rule could drift apart.

        Room room = new Room();
        room.setName(request.getName().trim());
        room.setCapacity(request.getCapacity());
        room.setFloor(request.getFloor());
        room.setVirtual(request.isVirtual());

        logger.debug("validation passed, creating the room - final data: name: {}, capacity: {}, floor: {}, isVirtual: {}", 
                   room.getName(), room.getCapacity(), room.getFloor(), room.isVirtual());

        // No try/catch: a save failure has to reach GlobalExceptionHandler, which knows how
        // to translate it. It used to be swallowed and returned as null, and the controller
        // presented it as "check the name is not already taken" - a guess, and a false one
        // when the cause was the database. The concrete case is a violation of the UNIQUE on
        // rooms.name between two concurrent creations: that is a 409 now, not a 400.
        Room savedRoom = roomRepository.save(room);
        logger.debug("END createroom - room saved - ID: {}, name: {}", savedRoom.getId(), savedRoom.getName());
        return savedRoom;
    }

    // Updates an existing room.
    public Room updateRoom(Long id, RoomRequest request) {
        logger.debug("START updateroom - ID: {}, data received: name: {}, capacity: {}, floor: {}, isVirtual: {}", 
                   id, request.getName(), request.getCapacity(), request.getFloor(), request.isVirtual());
        
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.forId(ResourceType.ROOM, id));
        logger.debug("existing room found - name: {}, capacity: {}, floor: {}, isVirtual: {}", 
                   room.getName(), room.getCapacity(), room.getFloor(), room.isVirtual());

        if (roomRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            logger.debug("END updateroom - name already taken: {}", request.getName());
            throw new DomainConflictException("ROOM_NAME_TAKEN",
                    "Room name already taken: " + request.getName(),
                    "Esiste gia' un'aula con questo nome.");
        }

        // As in createRoom: the validation is @Valid's job, not this method's.

        room.setName(request.getName().trim());
        room.setCapacity(request.getCapacity());
        room.setFloor(request.getFloor());
        room.setVirtual(request.isVirtual());

        logger.debug("validation passed, updating the room - final data: name: {}, capacity: {}, floor: {}, isVirtual: {}", 
                   room.getName(), room.getCapacity(), room.getFloor(), room.isVirtual());

        // As in createRoom: the error is left to rise to the global handler.
        Room savedRoom = roomRepository.save(room);
        logger.debug("END updateroom - room updated - ID: {}, name: {}", savedRoom.getId(), savedRoom.getName());
        return savedRoom;
    }

    // Deletes a room.
    public void deleteRoom(Long id) {
        logger.debug("START deleteroom - ID: {}", id);
        
        if (!roomRepository.existsById(id)) {
            throw ResourceNotFoundException.forId(ResourceType.ROOM, id);
        }

        // false used to mean two opposite things: "no such room" (above) and "the deletion
        // failed" (here). It now means only the first, and a real error rises to the handler.
        // The realistic case is a booking still referencing the room: that is a 409, and
        // saying so is more use than answering "room not found" about a room that exists.
        roomRepository.deleteById(id);
        logger.debug("END deleteroom - room deleted - ID: {}", id);
    }

    // Rooms on a given floor.
    public List<Room> getRoomsByFloor(int floor) {
        logger.debug("START getAuleByfloor - floor: {}", floor);
        List<Room> rooms = roomRepository.findByFloor(floor);
        logger.debug("END getAuleByfloor - rooms found: {}", rooms.size());
        return rooms;
    }
    
    // Rooms with at least a given capacity.
    public List<Room> getRoomsByMinCapacity(int minCapacity) {
        logger.debug("START getAuleBycapacityMinima - capacity minima: {}", minCapacity);
        List<Room> rooms = roomRepository.findByCapacityGreaterThanEqual(minCapacity);
        logger.debug("END getAuleBycapacityMinima - rooms found: {}", rooms.size());
        return rooms;
    }

    // Full details of every room, with status and bookings.
    public List<RoomDetailsResponse> getAllRoomsWithDetails() {
        logger.debug("START getAllRoomsWithDetails");
        List<RoomDetailsResponse> response = getRoomsDetailsFromList(roomRepository.findAll());
        logger.debug("END getAllRoomsWithDetails - details built for {} rooms", response.size());
        return response;
    }

    // Full details of a single room.
    public RoomDetailsResponse getRoomWithDetails(Long roomId) {
        logger.debug("START getRoomWithDetails - ID room: {}", roomId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> ResourceNotFoundException.forId(ResourceType.ROOM, roomId));

        RoomDetailsResponse roomDetails = toRoomDetails(
                room, bookingRepository.findByRoomId(room.getId()), LocalDateTime.now());

        logger.debug("END getRoomWithDetails - details built for room: {}", roomId);
        return roomDetails;
    }
    
    // ==================== physical and virtual rooms ====================
    
    
    // Physical rooms, ordered by floor then name.
    public List<Room> getPhysicalRoomsOrdered() {
        logger.debug("START getPhysicalRoomsOrdered - fetching the physical rooms in order");
        List<Room> rooms = roomRepository.findPhysicalRoomsOrderByFloorAndName();
        logger.debug("END getPhysicalRoomsOrdered - physical rooms ordered: {}", rooms.size());
        return rooms;
    }
    
    // Virtual rooms, ordered by name.
    public List<Room> getVirtualRoomsOrdered() {
        logger.debug("START getVirtualRoomsOrdered - fetching the virtual rooms in order");
        List<Room> rooms = roomRepository.findVirtualRoomsOrderByNome();
        logger.debug("END getVirtualRoomsOrdered - virtual rooms ordered: {}", rooms.size());
        return rooms;
    }
    
    // Full details of the physical rooms.
    public List<RoomDetailsResponse> getPhysicalRoomsWithDetails() {
        logger.debug("START getPhysicalRoomsWithDetails - fetching physical room details");
        List<Room> rooms = roomRepository.findByIsVirtual(false);
        List<RoomDetailsResponse> details = getRoomsDetailsFromList(rooms);
        logger.debug("END getPhysicalRoomsWithDetails - details built: {}", details.size());
        return details;
    }
    
    // Full details of the virtual rooms.
    public List<RoomDetailsResponse> getVirtualRoomsWithDetails() {
        logger.debug("START getVirtualRoomsWithDetails - fetching virtual room details");
        List<Room> rooms = roomRepository.findByIsVirtual(true);
        List<RoomDetailsResponse> details = getRoomsDetailsFromList(rooms);
        logger.debug("END getVirtualRoomsWithDetails - details built: {}", details.size());
        return details;
    }
    
    // How many physical rooms.
    public long countPhysicalRooms() {
        logger.debug("START countPhysicalRooms - counting the physical rooms");
        long count = roomRepository.countByIsVirtual(false);
        logger.debug("END countPhysicalRooms - total physical rooms: {}", count);
        return count;
    }
    
    // How many virtual rooms.
    public long countVirtualRooms() {
        logger.debug("START countVirtualRooms - counting the virtual rooms");
        long count = roomRepository.countByIsVirtual(true);
        logger.debug("END countVirtualRooms - total virtual rooms: {}", count);
        return count;
    }
    
    // Builds the details for a list of rooms that has already been chosen.
    private List<RoomDetailsResponse> getRoomsDetailsFromList(List<Room> rooms) {
        logger.debug("START getRoomsDetailsFromList - building details for {} rooms", rooms.size());

        // One instant for every room. LocalDateTime.now() used to be called inside the
        // loop, so rooms in the same response could be evaluated against different moments.
        LocalDateTime adesso = LocalDateTime.now();

        // One query for all the rooms, not one per room: the listing used to cost 1+N
        // queries, and with Booking's EAGER relations a good deal more than that.
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

        logger.debug("END getRoomsDetailsFromList - finished building details for {} rooms", response.size());
        return response;
    }

    /**
     * The detail view of ONE room with respect to a given instant.
     *
     * This block existed in three identical copies (getAllRoomsWithDetails,
     * getRoomWithDetails and getRoomsDetailsFromList): a change to the status rules had to be
     * replicated by hand three times, and forgetting one was enough to make the same fact
     * come out differently depending on which endpoint was asked.
     *
     * The instant comes from the caller rather than being read here: that makes the method
     * deterministic and lets a list of rooms share one single "now".
     */
    private RoomDetailsResponse toRoomDetails(Room room, List<Booking> bookings, LocalDateTime adesso) {
        RoomDetailsResponse roomDetails = new RoomDetailsResponse(
                room.getId(), room.getName(), room.getFloor(), room.getCapacity(), room.isVirtual());

        RoomAvailability status = RoomAvailability.FREE;
        RoomDetailsResponse.CurrentBooking currentBooking = null;
        RoomDetailsResponse.BlockInfo blockInfo = null;

        // Is the room busy or blocked right now?
        for (Booking booking : bookings) {
            if (booking.getStartTime().isBefore(adesso) && booking.getEndTime().isAfter(adesso)) {
                if (booking.getStatus() == BookingStatus.BOOKED) {
                    status = RoomAvailability.BOOKED;
                    currentBooking = toCurrentBooking(booking);
                } else if (booking.getStatus().isAdminIntervention()) {
                    status = RoomAvailability.BLOCKED;
                    blockInfo = new RoomDetailsResponse.BlockInfo(
                        descriptionOr(booking, DEFAULT_BLOCK_REASON),
                        BLOCCATA_DA,
                        booking.getCreatedAt().toLocalDate().format(FORMATO_DATA)
                    );
                }
                break;
            }
        }

        // If it is free now, is there a booking about to start?
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
                    descriptionOr(booking, SCOPO_PREDEFINITO)
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
                descriptionOr(booking, SCOPO_PREDEFINITO));
    }

    private static String descriptionOr(Booking booking, String predefinita) {
        return booking.getDescription() != null ? booking.getDescription() : predefinita;
    }
}