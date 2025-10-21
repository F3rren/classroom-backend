### CONFIGURAZIONE

1. **VERIFICARE PREREQUISITI**
   PostgreSQL 18.0
   Java 17
   Spring Boot 3.4.0

### INSTALLAZIONE E AVVIO POSTGRESQL   

2. **CREA IL DATABASE POSTGRESQL**:
   ```sql
   CREATE DATABASE prenotazioni_aule;
   ```

3. **CONFIGURAZIONE DELLE CREDENZIALI DEL DATABASE**:
   
   Nel file `src/main/resources/application.properties` modificare le seguenti proprietà secondo la configurazione prescelta:

   ```properties
   # Configurazione PostgreSQL
   spring.datasource.url=jdbc:postgresql://localhost:5432/prenotazioni_aule
   spring.datasource.username=postgres //da modificare con valore personalizzato
   spring.datasource.password=root //da modificare con valore personalizzato
   ```

   **PARAMETRI CONFIGURABILI:**

   - `localhost:17102` - Cambia con l'host e la porta del tuo server PostgreSQL (porta standard: 5432)
   - `prenotazioni_aule` - Nome del database
   - `postgres` - Username del database
   - `root` - Password del database

   # - Se il database è su un altro server, cambia 'localhost' con l'IP del server
   # - Se è cambiato la porta di PostgreSQL, modificare '17102'   

   # ABILITA CORS GLOBALE
   prenotazioni.cors.allowed-origins=http://indirizzo_ip_chiamante_del_frontend

4. **BUILD DEL PROGETTO** 

   # 1. BUILD IL JAR GENERATO
   ```bash
   mvnw.cmd spring-boot:run
   ```
   # 2. BUILD DEL PROGETTO
   ```bash
   mvnw.cmd clean package
   ```
   # 3. ESEGUI IL JAR GENERATO
   java -jar target/backend-0.0.1-SNAPSHOT.jar

5. **TEST DELL'APPLICAZIONE**

   Apri il browser e vai a:
   ```
   http://localhost:8080/api/aule
   ```

   Dovresti vedere la lista delle aule (vuota se il database è nuovo).

   Quando l'applicazione è avviata correttamente, vedrai nel terminale:

   ```
   .   ____          _            __ _ _
   /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
   ( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
   \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
   '  |____| .__|_| |_|_| |_\__, | / / / /
   =========|_|==============|___/=/_/_/_/
   :: Spring Boot ::                (v3.4.0)

   ...
   Started BackendApplication in 3.456 seconds (process running for 4.123)
   Tomcat started on port(s): 8080 (http) with context path ''
   ```
