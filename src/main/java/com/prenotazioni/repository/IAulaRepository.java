package com.prenotazioni.repository;

import com.prenotazioni.model.Aula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IAulaRepository extends JpaRepository<Aula, Long> {
    
    // Verifica se esiste un'aula con un certo nome (case insensitive)
    @Query("SELECT COUNT(a) > 0 FROM Aula a WHERE LOWER(a.nome) = LOWER(:nome)")
    boolean existsByNomeIgnoreCase(@Param("nome") String nome);
    
    // Verifica se esiste un'aula con un certo nome escludendo un ID specifico
    @Query("SELECT COUNT(a) > 0 FROM Aula a WHERE LOWER(a.nome) = LOWER(:nome) AND a.id != :excludeId")
    boolean existsByNomeIgnoreCaseAndIdNot(@Param("nome") String nome, @Param("excludeId") Long excludeId);
    
    // Trova aule per piano
    List<Aula> findByPiano(int piano);
    
    // Trova aule con capienza maggiore o uguale a un valore
    List<Aula> findByCapienzaGreaterThanEqual(int capienza);
    
    // Trova aule fisiche o virtuali
    List<Aula> findByIsVirtual(boolean isVirtual);
    
    // Trova aule fisiche ordinate per piano e nome
    @Query("SELECT a FROM Aula a WHERE a.isVirtual = false ORDER BY a.piano ASC, a.nome ASC")
    List<Aula> findPhysicalRoomsOrderByPianoAndNome();
    
    // Trova aule virtuali ordinate per nome
    @Query("SELECT a FROM Aula a WHERE a.isVirtual = true ORDER BY a.nome ASC")
    List<Aula> findVirtualRoomsOrderByNome();
    
    // Conta aule fisiche e virtuali
    long countByIsVirtual(boolean isVirtual);
}
