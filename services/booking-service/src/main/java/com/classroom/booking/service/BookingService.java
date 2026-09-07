package com.classroom.booking.service;

import com.classroom.booking.dto.BookingDetailDto;
import com.classroom.booking.model.Room;
import com.classroom.booking.model.Course;
import com.classroom.exception.BookingConflictException;
import com.classroom.exception.DomainConflictException;
import com.classroom.exception.ResourceNotFoundException;
import com.classroom.exception.ResourceType;
import com.classroom.booking.model.Booking;
import com.classroom.booking.model.BookingOwner;
import com.classroom.booking.model.RoomStatus;
import com.classroom.booking.model.BookingStatus;
import com.classroom.booking.repository.RoomRepository;
import com.classroom.booking.repository.CourseRepository;
import com.classroom.booking.repository.BookingRepository;

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

    // Books a room for a lesson.
    @Transactional
    public Booking bookRoom(Long roomId, Long courseId, BookingOwner owner, LocalDateTime startTime, LocalDateTime endTime, String description) {
        logger.debug("START bookRoom");
        logger.debug("room booking requested - roomId: {}, courseId: {}, userId: {}, period: {} - {}", roomId, courseId, owner.getId(), startTime, endTime);
        
        // Availability check
        if (!isRoomAvailable(roomId, startTime, endTime)) {
            logger.warn("room ID {} is not available for the period {} - {}", roomId, startTime, endTime);
            throw new BookingConflictException("BOOKING_CONFLICT",
                    "Room " + roomId + " busy from " + startTime + " to " + endTime,
                    "L'aula non e' disponibile nel periodo richiesto.");
        }
        
        Optional<Room> room = roomRepository.findById(roomId);

        if (room.isEmpty()) {
            // 404 and no longer 409: a missing room and a busy room were both a null, and
            // the controller presented them all as a conflict. They are different things.
            throw ResourceNotFoundException.forId(ResourceType.ROOM, roomId);
        }
        // The user's existence is no longer checked: this service does not have the users
        // table any more. The token guarantees it, signed by auth-service at login. The
        // window for a deleted user holding a still-valid token is bounded by its expiry.
        
        // The course is optional - null for a booking with no course attached
        Optional<Course> course = Optional.empty();
        if (courseId != null) {
            course = courseRepository.findById(courseId);
            if (course.isEmpty()) {
                // The course is optional, but if given it has to exist: passing one that
                // does not is a caller error, not a booking without a course.
                throw ResourceNotFoundException.forId(ResourceType.COURSE, courseId);
            }
        }

        logger.debug("creating a booking for room - roomId: {}, courseId: {}, userId: {}, period: {} - {}", roomId, courseId, owner.getId(), startTime, endTime);
        Booking booking = new Booking();
        booking.setRoom(room.get());
        booking.setCourse(course.orElse(null)); // Può essere null
        booking.setUser(owner);
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setDescription(description);
        booking.setCreatedAt(LocalDateTime.now());
        
        Booking savedBooking = bookingRepository.save(booking);
        
        // Refresh the room status if the booking is active RIGHT NOW
        updateRoomStatus(roomId);
        
        logger.info("booking created - id={} room='{}' userId={} period={} - {}", savedBooking.getId(), room.get().getName(), owner.getId(), startTime, endTime);
        logger.debug("END bookRoom");
        return savedBooking;
    }
    
    // Blocks a room. Admin only.
    @Transactional
    public Booking blockRoom(Long roomId, BookingOwner admin, LocalDateTime startTime, LocalDateTime endTime, String reason) {
        logger.debug("START blockRoom");
        logger.debug("room block requested - roomId: {}, AdminId: {}, period: {} - {}", roomId, admin.getId(), startTime, endTime);
        
        // Availability check
        if (!isRoomAvailable(roomId, startTime, endTime)) {
            logger.warn("room ID {} is not available for the period {} - {}", roomId, startTime, endTime);
            throw new BookingConflictException("BLOCK_CONFLICT",
                    "Room " + roomId + " busy from " + startTime + " to " + endTime,
                    "L'aula non e' disponibile nel periodo richiesto.");
        }
        
        Optional<Room> room = roomRepository.findById(roomId);

        // The role is not re-read from the database: it comes from the token, and the
        // controller is already annotated @PreAuthorize("hasRole('ADMIN')").
        if (room.isEmpty()) {
            throw ResourceNotFoundException.forId(ResourceType.ROOM, roomId);
        }
        
        logger.debug("blocking room - roomId: {}, AdminId: {}, period: {} - {}", roomId, admin.getId(), startTime, endTime);
        Booking blocco = new Booking();
        blocco.setRoom(room.get());
        blocco.setCourse(null); // Nessun corso per i blocchi
        blocco.setUser(admin);
        blocco.setStartTime(startTime);
        blocco.setEndTime(endTime);
        blocco.setStatus(BookingStatus.BLOCKED);
        blocco.setDescription(reason);
        blocco.setCreatedAt(LocalDateTime.now());
        
        logger.info("room block created - id={} room='{}' adminId={} period={} - {}", blocco.getId(), room.get().getName(), admin.getId(), startTime, endTime);
        logger.debug("END blockRoom");
        return bookingRepository.save(blocco);
    }
    
    // Is a room free over a given period?
    public boolean isRoomAvailable(Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        logger.debug("START isRoomAvailable");
        logger.debug("Room availability check - roomId: {}, period: {} - {}", roomId, startTime, endTime);
        List<Booking> conflicts = bookingRepository.findConflictingBookings(roomId, startTime, endTime);
        boolean available = conflicts.isEmpty();
        logger.debug("room availability result - roomId: {}, period: {} - {}", roomId, startTime, endTime, available);
        return available;
    }
    
    // The current status of a room.
    public String getRoomStatus(Long roomId, LocalDateTime moment) {
        logger.debug("START getRoomStatus");
        logger.debug("checking room status - roomId: {}, moment: {}", roomId, moment);
        List<Booking> activeBookings = bookingRepository.findActiveBookings(roomId, moment);
            
        if (activeBookings.isEmpty()) {
            logger.debug("room status - roomId: {}, moment: {} - FREE", roomId, moment);
            return "FREE";
        }
        
        // Precedence: MAINTENANCE > BLOCKED > BOOKED
        for (Booking p : activeBookings) {
            if (p.getStatus() == BookingStatus.MAINTENANCE) {
                logger.debug("room status - roomId: {}, moment: {} - MAINTENANCE", roomId, moment);
                return "MAINTENANCE";
            }
        }
        
        for (Booking p : activeBookings) {
            if (p.getStatus() == BookingStatus.BLOCKED) {
                logger.debug("room status - roomId: {}, moment: {} - BLOCKED", roomId, moment);
                return "BLOCKED";
            }
        }
        
        logger.debug("room status - roomId: {}, moment: {} - BOOKED", roomId, moment);
        logger.debug("END getRoomStatus");
        return "BOOKED";
    }
    
    // Refreshes the room status from the bookings active right now.
    private void updateRoomStatus(Long roomId) {
        logger.debug("START updateRoomStatus - roomId: {}", roomId);
        
        Optional<Room> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            logger.warn("room not found while refreshing its status - roomId: {}", roomId);
            return;
        }
        
        Room room = roomOpt.get();
        LocalDateTime now = LocalDateTime.now();
        
        // The bookings active at this moment
        List<Booking> activeBookings = bookingRepository.findActiveBookings(roomId, now);
        
        RoomStatus newStatus;
        if (activeBookings.isEmpty()) {
            newStatus = RoomStatus.FREE;
        } else {
            // Is there a maintenance or blocking booking among them?
            boolean hasMaintenance = activeBookings.stream()
                .anyMatch(p -> p.getStatus() == BookingStatus.MAINTENANCE);
            boolean hasBlocked = activeBookings.stream()
                .anyMatch(p -> p.getStatus() == BookingStatus.BLOCKED);
            
            if (hasMaintenance) {
                newStatus = RoomStatus.MAINTENANCE;
            } else if (hasBlocked) {
                newStatus = RoomStatus.BLOCKED;
            } else {
                newStatus = RoomStatus.BUSY;
            }
        }
        
        // Write only if the status changed
        if (newStatus != room.getStatus()) {
            logger.debug("refreshing status of room {} da '{}' a '{}'", roomId, room.getStatus(), newStatus);
            room.setStatus(newStatus);
            roomRepository.save(room);
        } else {
            logger.debug("room status {} unchanged: '{}'", roomId, room.getStatus());
        }
        
        logger.debug("END updateRoomStatus");
    }
    
    // Cancels a booking.
    @Transactional
    public boolean cancelBooking(Long bookingId, Long userId, boolean isAdmin) {
        logger.debug("START cancelBooking");
        logger.debug("booking cancellation requested - bookingId: {}, userId: {}", bookingId, userId);
        Optional<Booking> booking = bookingRepository.findById(bookingId);
        
        if (booking.isEmpty()) {
            throw ResourceNotFoundException.forId(ResourceType.BOOKING, bookingId);
        }
        
        logger.debug("checking cancellation permissions for booking - bookingId: {}, userId: {}", bookingId, userId);
        Booking p = booking.get();
        
        // Only the creator or an admin may cancel

        logger.debug("checking cancellation permissions for booking - bookingId: {}, userId: {}", bookingId, userId);
        boolean isCreator = p.getUser().getId().equals(userId);
                
        if (!isCreator && !isAdmin) {
            // AccessDeniedException and not a boolean: the global handler already turns it
            // into a 403. The controller used to have to REDO this same check to work out
            // whether the false meant "not allowed" or something else.
            throw new org.springframework.security.access.AccessDeniedException(
                    "Puoi annullare solo le tue prenotazioni.");
        }

        // Only an active booking can be cancelled through this endpoint. Without this check
        // a second cancellation succeeded and answered "cancelled successfully" while
        // changing nothing; blocks and maintenance, which are not "booked", are cancelled
        // through the admin endpoint (cancelBookingAsAdmin, deliberately permissive about
        // the status). The rule is about the status, not the role: it applies to admins too.
        if (!p.getStatus().isActive()) {
            // 409: the booking exists and is visible, but its status does not admit
            // cancellation. It is neither "not found" nor "not allowed".
            throw new DomainConflictException("INVALID_STATE",
                    "Prenotazione " + bookingId + " in state " + p.getStatus().getValue(),
                    "Questa prenotazione non puo' essere annullata nello stato attuale.");
        }

        p.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(p);
        
        // Refresh the room status
        updateRoomStatus(p.getRoom().getId());
        
        logger.info("booking ID {} cancelled by user ID {}", bookingId, userId);
        logger.debug("END cancelBooking");
        return true;
    }
    
    // Every booking, for the admin views.
    public List<Booking> getAllBookings() {
        logger.debug("START getAllBookings");
        logger.debug("fetching every booking from the database");
        List<Booking> bookings = bookingRepository.findAll();
        logger.debug("fetched {} bookings in total", bookings.size());
        logger.debug("END getAllBookings");
        return bookings;
    }
    
    // The bookings of one user.
    public List<Booking> getUserBookings(Long userId) {
        logger.debug("START getUserBookings");
        logger.debug("fetching bookings for user - userId: {}", userId);
        List<Booking> bookings = bookingRepository.findByUserId(userId);
        logger.debug("fetched {} bookings for user ID {}", bookings.size(), userId);
        logger.debug("END getUserBookings");
        return bookings;
    }
    
    // Full details for one room.
    public List<BookingDetailDto> getRoomCompleteDetails(Long roomId) {
        logger.debug("START getRoomCompleteDetails");
        logger.debug("fetching full details for room - roomId: {}", roomId);
        logger.debug("END getRoomCompleteDetails");
        return bookingRepository.findCompleteDetailsByRoomId(roomId);
    }
    
    // Full details for every booking.
    public List<BookingDetailDto> getAllCompleteDetails() {
        logger.debug("START getAllCompleteDetails");
        logger.debug("fetching full details for every booking");
        logger.debug("END getAllCompleteDetails");
        return bookingRepository.findAllCompleteDetails();
    }
    
    // A single booking by id.
    public Booking getBookingById(Long id) {
        logger.debug("START getBookingById");
        logger.debug("fetching booking by ID - bookingId: {}", id);
        Optional<Booking> booking = bookingRepository.findById(id);
        logger.debug("END getBookingById");
        return booking.orElse(null);
    }
    
    // Full details for a single booking.
    public List<BookingDetailDto> getBookingCompleteDetails(Long bookingId) {
        logger.debug("START getBookingCompleteDetails");
        logger.debug("fetching full details for booking - bookingId: {}", bookingId);
        logger.debug("END getBookingCompleteDetails");
        return bookingRepository.findCompleteDetailsByBookingId(bookingId);
    }
    
    // The bookings in a given status.
    public List<Booking> getBookingsByStatus(String status) {
        logger.debug("START getBookingsByStatus");
        logger.debug("fetching bookings by status - status: {}", status);
        logger.debug("END getBookingsByStatus");
        return bookingRepository.findByStatus(BookingStatus.from(status));
    }
    
    // The bookings that start in the future.
    public List<Booking> getFutureBookings() {
        logger.debug("START getFutureBookings");
        logger.debug("fetching bookings starting from now");
        logger.debug("END getFutureBookings");
        return bookingRepository.findFutureBookings(LocalDateTime.now());
    }
    
    // The admin path for cancelling any booking at all.
    @Transactional
    public boolean cancelBookingAsAdmin(Long bookingId, Long adminId, String reason) {
        logger.debug("START cancelBookingAsAdmin");
        logger.debug("booking cancellation requested by an admin - bookingId: {}, AdminId: {}, reason: {}", bookingId, adminId, reason);
        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            throw ResourceNotFoundException.forId(ResourceType.BOOKING, bookingId);
        }
        
        logger.debug("booking cancellation requested by an admin - bookingId: {}, AdminId: {}, reason: {}", bookingId, adminId, reason);
        Booking booking = bookingOpt.get();
        
        // The admin role has already been checked by the JWT filter and by @PreAuthorize:
        // re-reading it here would mean calling auth-service on every cancellation.
        
        logger.debug("booking cancelled by the admin - bookingId: {}, AdminId: {}, reason: {}", bookingId, adminId, reason);
        // An admin may cancel any booking, whatever its status
        booking.setStatus(BookingStatus.CANCELLED);
        
        logger.debug("updating the booking description to record the admin action - bookingId: {}, AdminId: {}, reason: {}", bookingId, adminId, reason);
        // Record in the description that an admin did this
        String originalDescription = booking.getDescription() != null ? booking.getDescription() : "";
        String newDescription = originalDescription + 
            (originalDescription.isEmpty() ? "" : " | ") +
            "CANCELLED DALL'AMMINISTRATORE: " + reason;
        booking.setDescription(newDescription);

        logger.debug("saving the updated booking - bookingId: {}", bookingId);
        bookingRepository.save(booking);
        
        // Refresh the room status
        updateRoomStatus(booking.getRoom().getId());
        
        logger.debug("END cancelBookingAsAdmin");
        return true;
    }

    // Updates an existing booking.
    @Transactional
    public Booking updateBooking(Long bookingId, Long roomId, Long courseId, Long userId, boolean isAdmin, LocalDateTime startTime, LocalDateTime endTime, String description) {
        logger.debug("START updateBooking");
        logger.debug("booking update requested - bookingId: {}, roomId: {}, courseId: {}, userId: {}, period: {} - {}", bookingId, roomId, courseId, userId, startTime, endTime);
        
        // Find the booking
        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            throw ResourceNotFoundException.forId(ResourceType.BOOKING, bookingId);
        }
        
        Booking booking = bookingOpt.get();
        
        // Authorisation - only the creator or an admin may change it
        boolean isCreator = booking.getUser().getId().equals(userId);
                
        if (!isCreator && !isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Puoi modificare solo le tue prenotazioni.");
        }
        
        // The room has to exist
        Optional<Room> room = roomRepository.findById(roomId);
        if (room.isEmpty()) {
            // 404 and no longer 409: a missing room and a busy room were both a null, and
            // the controller presented them all as a conflict. They are different things.
            throw ResourceNotFoundException.forId(ResourceType.ROOM, roomId);
        }
        
        // Is the room free over the new period? (this booking itself excluded)
        if (!isRoomAvailableExcluding(roomId, startTime, endTime, bookingId)) {
            throw new BookingConflictException("UPDATE_CONFLICT",
                    "Room " + roomId + " busy from " + startTime + " to " + endTime,
                    "L'aula non e' disponibile nel nuovo periodo richiesto.");
        }
        
        // The course is optional
        Optional<Course> course = Optional.empty();
        if (courseId != null) {
            course = courseRepository.findById(courseId);
            if (course.isEmpty()) {
                // The course is optional, but if given it has to exist: passing one that
                // does not is a caller error, not a booking without a course.
                throw ResourceNotFoundException.forId(ResourceType.COURSE, courseId);
            }
        }
        
        // Apply the new values
        logger.debug("applying the new booking values - bookingId: {}", bookingId);
        booking.setRoom(room.get());
        booking.setCourse(course.orElse(null));
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setDescription(description);
        
        Booking savedBooking = bookingRepository.save(booking);
        logger.info("booking updated - id={} room='{}' userId={} period={} - {}", savedBooking.getId(), room.get().getName(), userId, startTime, endTime);
        logger.debug("END updateBooking");
        return savedBooking;
    }
    
    // Is the room free over the period, ignoring one particular booking?
    private boolean isRoomAvailableExcluding(Long roomId, LocalDateTime startTime, LocalDateTime endTime, Long excludedBookingId) {
        logger.debug("Room availability check excluding a booking - roomId: {}, period: {} - {}, excluded: {}", roomId, startTime, endTime, excludedBookingId);
        List<Booking> conflicts = bookingRepository.findConflictingBookingsExcluding(roomId, startTime, endTime, excludedBookingId);
        boolean available = conflicts.isEmpty();
        logger.debug("room availability result (booking {} excluded) - roomId: {}, available: {}", excludedBookingId, roomId, available);
        return available;
    }
}
