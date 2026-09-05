package com.prenotazioni.auth.service;

import com.prenotazioni.auth.model.User;
import com.prenotazioni.exception.DomainConflictException;
import com.prenotazioni.exception.ResourceNotFoundException;
import com.prenotazioni.exception.ResourceType;
import com.prenotazioni.model.Role;
import com.prenotazioni.auth.repository.UserRepository;
import com.prenotazioni.auth.dto.CreateUserRequest;
import com.prenotazioni.auth.dto.UpdateUserRequest;
import com.prenotazioni.util.LogSanitizer;

import java.util.List;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User login(String email, String password) {
        User user = userRepository.findByEmail(email);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            // WARN e non INFO: un login fallito e' un segnale di sicurezza (brute-force,
            // credenziali compromesse) e deve restare visibile anche alzando il livello.
            logger.warn("Login fallito - credenziali non valide per {}", LogSanitizer.maskEmail(email));
            // Questo null RESTA, a differenza degli altri di questa classe: ha un solo
            // significato possibile, credenziali sbagliate, quindi il chiamante non deve
            // dedurre nulla. E' il null AMBIGUO il problema, non il null in se'.
            return null;
        }
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        logger.info("Login riuscito - utenteId={} ({})", user.getId(), LogSanitizer.maskEmail(email));
        return user;
    }

    public User register(CreateUserRequest request) {
        // Controlla se email o username sono già registrati
        if (userRepository.findByEmail(request.getEmail()) != null) {
            // Il codice resta USER_ALREADY_EXISTS, gia' esposto e veritiero. A cambiare e'
            // il messaggio: prima non diceva QUALE dei due campi fosse in conflitto, e chi
            // lo leggeva non sapeva cosa correggere.
            throw new DomainConflictException("USER_ALREADY_EXISTS",
                    "Email already registered",
                    "Questa email e' gia' associata a un altro utente.");
        }
        if (userRepository.findByUsername(request.getUsername()) != null) {
            throw new DomainConflictException("USER_ALREADY_EXISTS",
                    "Username already registered: " + request.getUsername(),
                    "Questo username e' gia' in uso.");
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.da(request.getRole()));
        user.setUsername(request.getUsername());
        
        // Imposta la data di registrazione (non modificabile)
        user.setRegisteredAt(LocalDateTime.now());
        // ultimoAccesso viene aggiornato solo al login
        User saved = userRepository.save(user);
        logger.info("Utente creato - utenteId={} ({})", saved.getId(), LogSanitizer.maskEmail(saved.getEmail()));
        return saved;
    }

    public List<User> getAllUsers() {
        List<User> users = userRepository.findAll();
        logger.debug("Elenco utenti recuperato - totale={}", users.size());
        return users;
    }

    public User updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            throw ResourceNotFoundException.forId(ResourceType.USER, id);
        }

        // Controlla se la nuova email o username sono già in uso da un altro utente
        User userWithEmail = userRepository.findByEmail(request.getEmail());
        if (userWithEmail != null && !userWithEmail.getId().equals(id)) {
            // 409 e non piu' 404: prima questo caso tornava lo stesso null di "utente
            // inesistente", e la risposta diceva "utente non trovato" di un utente che
            // esiste eccome. Era una risposta falsa, non solo imprecisa.
            throw new DomainConflictException("USER_ALREADY_EXISTS",
                    "Email already used by another utente",
                    "Questa email e' gia' associata a un altro utente.");
        }
        User userWithUsername = userRepository.findByUsername(request.getUsername());
        if (userWithUsername != null && !userWithUsername.getId().equals(id)) {
            throw new DomainConflictException("USER_ALREADY_EXISTS",
                    "Username already used by another utente",
                    "Questo username e' gia' in uso.");
        }
        // Aggiorna i campi modificabili
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        
        // Aggiorna la password solo se ne viene fornita una nuova
        boolean passwordCambiata = request.getPassword() != null && !request.getPassword().trim().isEmpty();
        if (passwordCambiata) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        
        // Il formato del ruolo (admin|user, case-insensitive) e' gia' garantito da @Pattern
        // sul DTO; qui resta solo la normalizzazione e il fallback per un ruolo non fornito.
        Role role = request.getRole() != null ? Role.da(request.getRole()) : user.getRole();
        user.setRole(role);
        user.setUsername(request.getUsername());
        
        // NON modifichiamo dataRegistrazione - rimane quella originale
        // ultimoAccesso viene aggiornato solo al login
        User saved = userRepository.save(user);
        logger.info("Utente aggiornato - utenteId={} passwordCambiata={}", id, passwordCambiata);
        return saved;
    }
}
