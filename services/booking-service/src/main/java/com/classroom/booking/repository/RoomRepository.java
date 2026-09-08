package com.classroom.booking.repository;

import com.classroom.booking.model.Room;
import com.classroom.booking.model.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    
    // Is there a room with this name? Case insensitive.
    @Query("SELECT COUNT(a) > 0 FROM Room a WHERE LOWER(a.name) = LOWER(:name)")
    boolean existsByNameIgnoreCase(@Param("name") String name);
    
    // Is there a room with this name, ignoring one particular id?
    @Query("SELECT COUNT(a) > 0 FROM Room a WHERE LOWER(a.name) = LOWER(:name) AND a.id != :excludeId")
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("excludeId") Long excludeId);
    
    // Rooms on a given floor.
    List<Room> findByFloor(int floor);
    
    // Rooms with at least the given capacity.
    List<Room> findByCapacityGreaterThanEqual(int capacity);
    
    // Physical or virtual rooms.
    List<Room> findByIsVirtual(boolean isVirtual);
    
    // Physical rooms, ordered by floor then name.
    @Query("SELECT a FROM Room a WHERE a.isVirtual = false ORDER BY a.floor ASC, a.name ASC")
    List<Room> findPhysicalRoomsOrderByFloorAndName();
    
    // Virtual rooms, ordered by name.
    @Query("SELECT a FROM Room a WHERE a.isVirtual = true ORDER BY a.name ASC")
    List<Room> findVirtualRoomsOrderByName();
    
    // How many physical, or virtual, rooms there are.
    long countByIsVirtual(boolean isVirtual);

    /**
     * Writes only the derived status column, deliberately bypassing optimistic locking.
     *
     * room.status is a cache of what the bookings say, refreshed after every booking and
     * cancellation - it is not anybody's edit. Going through save() would bump the version
     * and make two people booking DIFFERENT slots in the same room at the same moment
     * collide: one of them would lose a perfectly valid booking to a 409 raised by a
     * bookkeeping write neither of them asked for. A targeted update cannot conflict with
     * anything, and it cannot clobber another writer's fields either, because it touches one
     * column.
     *
     * The consequence to know: an admin editing the room while this runs still writes back
     * the status they loaded, since save() persists the whole entity. That lost update
     * predates this method and heals itself on the next refresh - the value is derived, so
     * the next booking or cancellation recomputes it.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Room r SET r.status = :status WHERE r.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") RoomStatus status);
}
