package com.prenotazioni.service;

import com.prenotazioni.model.Aula;
import com.prenotazioni.repository.AulaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AulaServiceTest {

    @Mock
    private AulaRepository aulaRepository;

    @InjectMocks
    private AulaService aulaService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetAllAule() {
        // Given
        Aula aula1 = new Aula();
        aula1.setId(1L);
        aula1.setNome("Aula 101");
        aula1.setCapienza(30);

        Aula aula2 = new Aula();
        aula2.setId(2L);
        aula2.setNome("Aula 102");
        aula2.setCapienza(50);

        List<Aula> expectedAule = Arrays.asList(aula1, aula2);
        when(aulaRepository.findAll()).thenReturn(expectedAule);

        // When
        List<Aula> actualAule = aulaService.getAllAule();
        List<Aula> actualAule2 = aulaService.getAllAule();

        // Then
        assertNotNull(actualAule);
        assertNotNull(actualAule2);
        assertEquals(2, actualAule.size());
        assertEquals("Aula 101", actualAule.get(0).getNome());
        assertEquals("Aula 102", actualAule.get(1).getNome());
    
        verify(aulaRepository, times(2)).findAll();
    }

    @Test
    public void testGetAulaById() {
        // Given
        Long aulaId = 1L;
        Aula expectedAula = new Aula();
        expectedAula.setId(aulaId);
        expectedAula.setNome("Aula 101");
        
        when(aulaRepository.findById(aulaId)).thenReturn(Optional.of(expectedAula));

        // When
        Optional<Aula> actualAula = aulaService.getAulaById(aulaId);

        // Then
        assertTrue(actualAula.isPresent());
        assertEquals(aulaId, actualAula.get().getId());
        assertEquals("Aula 101", actualAula.get().getNome());
        
        verify(aulaRepository, times(1)).findById(aulaId);
    }

    @Test
    public void testGetAulaByIdNotFound() {
        // Given
        Long aulaId = 999L;
        when(aulaRepository.findById(aulaId)).thenReturn(Optional.empty());

        // When
        Optional<Aula> actualAula = aulaService.getAulaById(aulaId);

        // Then
        assertFalse(actualAula.isPresent());
        
        verify(aulaRepository, times(1)).findById(aulaId);
    }
}