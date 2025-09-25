package com.prenotazioni.controller;

import com.prenotazioni.service.AulaService;
import com.prenotazioni.service.JwtService;
import com.prenotazioni.service.PrenotazioneService;
import com.prenotazioni.model.Aula;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoomController.class)
public class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AulaService aulaService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private PrenotazioneService prenotazioneService;

    private List<Aula> testRooms;

    @BeforeEach
    public void setUp() {
        // Setup test data
        Aula room1 = new Aula();
        room1.setId(1L);
        room1.setNome("Aula 101");
        room1.setCapienza(30);
        room1.setPiano(1);
        room1.setVirtual(false);

        Aula room2 = new Aula();
        room2.setId(2L);
        room2.setNome("Aula 102");
        room2.setCapienza(50);
        room2.setPiano(1);
        room2.setVirtual(false);

        testRooms = Arrays.asList(room1, room2);
        
        // Mock JWT service per tutti i test
        when(jwtService.validateToken(anyString())).thenReturn(true);
        when(jwtService.getEmailFromToken(anyString())).thenReturn("test@example.com");
        when(jwtService.getRuoloFromToken(anyString())).thenReturn("UTENTE");
        when(jwtService.getUserIdFromToken(anyString())).thenReturn(1L);
    }

    @Test
    @WithMockUser
    public void testGetAllRooms() throws Exception {
        // Given
        when(aulaService.getAllAule()).thenReturn(testRooms);

        // When & Then
        mockMvc.perform(get("/api/rooms")
                .header("Authorization", "Bearer valid-jwt-token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms").isArray())
                .andExpect(jsonPath("$.rooms.length()").value(2))
                .andExpect(jsonPath("$.rooms[0].nome").value("Aula 101"))
                .andExpect(jsonPath("$.rooms[1].nome").value("Aula 102"));
    }

    @Test
    @WithMockUser
    public void testGetRoomById() throws Exception {
        // Given
        Long roomId = 1L;
        Aula room = testRooms.get(0);
        when(aulaService.getAulaById(roomId)).thenReturn(Optional.of(room));

        // When & Then
        mockMvc.perform(get("/api/rooms/{id}", roomId)
                .header("Authorization", "Bearer valid-jwt-token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.id").value(1))
                .andExpect(jsonPath("$.room.nome").value("Aula 101"));
    }

    @Test
    @WithMockUser
    public void testGetRoomDetailsById() throws Exception {
        // Given
        Long roomId = 1L;
        Aula room = testRooms.get(0);
        List<Map<String, Object>> mockDetails = Arrays.asList();
        
        when(aulaService.getAulaById(roomId)).thenReturn(Optional.of(room));
        when(prenotazioneService.getRoomCompleteDetails(roomId)).thenReturn(mockDetails);

        // When & Then
        mockMvc.perform(get("/api/rooms/{id}/details", roomId)
                .header("Authorization", "Bearer valid-jwt-token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aula").exists())
                .andExpect(jsonPath("$.prenotazioni").exists());
    }

    @Test
    @WithMockUser  
    public void testGetRoomByIdNotFound() throws Exception {
        // Given
        Long roomId = 999L;
        when(aulaService.getAulaById(roomId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/rooms/{id}", roomId)
                .header("Authorization", "Bearer valid-jwt-token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testGetAllRoomsWithoutAuthentication() throws Exception {
        // When & Then - dovrebbe fallire senza autenticazione
        mockMvc.perform(get("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}