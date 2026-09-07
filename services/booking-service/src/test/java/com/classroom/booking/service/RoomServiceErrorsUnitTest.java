package com.classroom.booking.service;

import com.classroom.exception.DomainConflictException;
import com.classroom.exception.ResourceNotFoundException;
import com.classroom.booking.dto.RoomRequest;
import com.classroom.booking.model.Room;
import com.classroom.booking.repository.RoomRepository;
import com.classroom.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What happens when the database refuses an operation on a room.
 *
 * A test was needed precisely because nothing visible used to happen: RoomService caught
 * every exception and returned null, and the controller presented that as "check the name is
 * not already taken" with a 400. A database problem reached the client disguised as a user
 * error.
 *
 * The concrete case is not hypothetical: rooms.name is UNIQUE in the database, so two
 * concurrent creations with the same name both pass the existsByNameIgnoreCase check and one
 * of them is refused by the constraint. That is a collision, meaning a 409, and
 * GlobalExceptionHandler already knows how to produce one - it just had to be let through.
 */
class RoomServiceErrorsUnitTest {

    private RoomRepository roomRepository;
    private RoomService service;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        service = new RoomService(roomRepository, mock(BookingRepository.class));
    }

    private RoomRequest request(String name) {
        RoomRequest r = new RoomRequest();
        r.setName(name);
        r.setCapacity(30);
        r.setFloor(1);
        r.setVirtual(false);
        return r;
    }

    @Test
    void aConstraintViolationOnCreateIsNotSwallowed() {
        when(roomRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(roomRepository.save(any(Room.class)))
                .thenThrow(new DataIntegrityViolationException("rooms_name_key"));

        // It has to propagate, not become null: GlobalExceptionHandler turns it into a 409.
        assertThatThrownBy(() -> service.createRoom(request("Aula Magna")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aDatabaseFailureOnCreateDoesNotBecomeAUserError() {
        when(roomRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(roomRepository.save(any(Room.class)))
                .thenThrow(new IllegalStateException("connessione persa"));

        // It used to become a 400 saying "check the name is not already taken": a false
        // message, sending the reader to look for the problem in the wrong place.
        assertThatThrownBy(() -> service.createRoom(request("Aula Nuova")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anAlreadyUsedNameBecomesADomainConflict() {
        // It used to be a null, which the controller presented as a 400. It is a type now,
        // and the global handler turns it into a 409 with a code that names the cause.
        when(roomRepository.existsByNameIgnoreCase("Aula Magna")).thenReturn(true);

        assertThatThrownBy(() -> service.createRoom(request("Aula Magna")))
                .isInstanceOf(DomainConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ROOM_NAME_TAKEN");
    }

    @Test
    void aConstraintViolationOnUpdateIsNotSwallowed() {
        Room existing = new Room();
        existing.setId(1L);
        existing.setName("Aula A");
        when(roomRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roomRepository.existsByNameIgnoreCaseAndIdNot(anyString(), anyLong())).thenReturn(false);
        when(roomRepository.save(any(Room.class)))
                .thenThrow(new DataIntegrityViolationException("rooms_name_key"));

        assertThatThrownBy(() -> service.updateRoom(1L, request("Aula B")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aDeleteBlockedByAConstraintDoesNotDisguiseItselfAsAMissingRoom() {
        Room existing = new Room();
        existing.setId(1L);
        when(roomRepository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("bookings_room_id_fkey"))
                .when(roomRepository).deleteById(1L);

        // It used to return false, indistinguishable from "room not found": the client got
        // a 404 about a room that exists perfectly well, and the real reason - there are
        // bookings referencing it - never reached anybody.
        assertThatThrownBy(() -> service.deleteRoom(1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAMissingRoomSaysItDoesNotExist() {
        // It used to return false, indistinguishable from "the deletion failed". Now
        // it is a 404 that names the resource, and a failed deletion is a separate case.
        when(roomRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteRoom(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ROOM_NOT_FOUND");
    }
}
