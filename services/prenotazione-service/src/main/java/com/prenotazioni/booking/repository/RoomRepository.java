package com.prenotazioni.booking.repository;

import com.prenotazioni.booking.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    
    // Verifica se esiste un'aula con un certo nome (case insensitive)
    @Query("SELECT COUNT(a) > 0 FROM Room a WHERE LOWER(a.name) = LOWER(:name)")
    boolean existsByNameIgnoreCase(@Param("name") String name);
    
    // Verifica se esiste un'aula con un certo nome escludendo un ID specifico
    @Query("SELECT COUNT(a) > 0 FROM Room a WHERE LOWER(a.name) = LOWER(:name) AND a.id != :excludeId")
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("excludeId") Long excludeId);
    
    // Trova aule per piano
    List<Room> findByFloor(int floor);
    
    // Trova aule con capienza maggiore o uguale a un valore
    List<Room> findByCapacityGreaterThanEqual(int capacity);
    
    // Trova aule fisiche o virtuali
    List<Room> findByIsVirtual(boolean isVirtual);
    
    // Trova aule fisiche ordinate per piano e nome
    @Query("SELECT a FROM Room a WHERE a.isVirtual = false ORDER BY a.floor ASC, a.name ASC")
    List<Room> findPhysicalRoomsOrderByFloorAndName();
    
    // Trova aule virtuali ordinate per nome
    @Query("SELECT a FROM Room a WHERE a.isVirtual = true ORDER BY a.name ASC")
    List<Room> findVirtualRoomsOrderByNome();
    
    // Conta aule fisiche e virtuali
    long countByIsVirtual(boolean isVirtual);
}
