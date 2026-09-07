package com.classroom.booking.repository;

import com.classroom.booking.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
