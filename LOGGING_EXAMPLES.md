# Guida al Logging nell'Applicazione PrenotazioniAule

## Configurazione Logback

Il file `logback-spring.xml` è configurato per:
- **File generale**: `logs/app-log.txt` (tutti i log)
- **File errori**: `logs/error-log.txt` (solo WARN e ERROR)
- **Console**: output durante sviluppo
- **Rotazione**: file ruotano automaticamente (max 10MB, tenuti per 30 giorni)

## Livelli di Log utilizzati

### INFO - Operazioni importanti e flusso principale
```java
logger.info("Tentativo di prenotazione aula - AulaId: {}, UtenteId: {}", aulaId, utenteId);
logger.info("Prenotazione creata con successo - ID: {}", prenotazione.getId());
```

### WARN - Situazioni anomale ma gestibili
```java
logger.warn("Aula ID {} non disponibile per il periodo richiesto", aulaId);
logger.warn("Autenticazione fallita per tentativo di prenotazione");
```

### ERROR - Errori che impediscono l'operazione
```java
logger.error("Utente con ID {} non trovato nel database", utenteId);
logger.error("Errore interno durante prenotazione: {}", e.getMessage(), e);
```

### DEBUG - Dettagli tecnici per debugging
```java
logger.debug("Utente autenticato con ID: {}", utenteId);
logger.debug("Verifica disponibilità aula {} per periodo {}-{}", aulaId, inizio, fine);
```

## Come aggiungere logging nelle tue classi

### 1. Importare le librerie SLF4J
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

### 2. Creare il logger nella classe
```java
@RestController
public class MioController {
    private static final Logger logger = LoggerFactory.getLogger(MioController.class);
    
    // ... resto del codice
}
```

### 3. Utilizzare il logger nei metodi
```java
@GetMapping("/test")
public ResponseEntity<?> test() {
    logger.info("Chiamata API test ricevuta");
    
    try {
        // logica business
        logger.debug("Elaborazione completata");
        return ResponseEntity.ok("Success");
    } catch (Exception e) {
        logger.error("Errore durante elaborazione: {}", e.getMessage(), e);
        return ResponseEntity.status(500).body("Error");
    }
}
```

## File di Log generati

### `logs/app-log.txt`
Contiene tutti i log dell'applicazione con formato:
```
2024-01-15 14:30:25.123 [http-nio-8080-exec-1] INFO  com.prenotazioni.controller.PrenotazioneController - Tentativo di prenotazione aula - AulaId: 1, CorsoId: 2
```

### `logs/error-log.txt` 
Contiene solo WARNING e ERROR per monitoraggio veloce dei problemi.

## Suggerimenti per un logging efficace

1. **Usa parametri invece di concatenazione**:
   ✅ `logger.info("User {} logged in", username);`
   ❌ `logger.info("User " + username + " logged in");`

2. **Logga sempre le eccezioni con stack trace**:
   ```java
   logger.error("Database error: {}", e.getMessage(), e);
   ```

3. **Usa livelli appropriati**:
   - ERROR: per eccezioni che bloccano l'operazione
   - WARN: per situazioni anomale ma gestibili  
   - INFO: per eventi importanti del business
   - DEBUG: per dettagli tecnici durante sviluppo

4. **Non loggare informazioni sensibili** (password, token completi, dati personali)

5. **Aggiungi contesto utile**:
   ```java
   logger.info("Prenotazione {} cancellata dall'utente {}", prenotazioneId, userId);
   ```

## Monitoraggio in produzione

I file di log ti permettono di:
- Tracciare le operazioni degli utenti
- Identificare errori e pattern problematici
- Monitorare le performance dell'applicazione
- Debugging di problemi in produzione

## Query SQL Logging

Per vedere le query SQL eseguite da Hibernate, imposta nel `application.properties`:
```properties
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```