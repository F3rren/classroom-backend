# Prenotazioni Aule — Backend

API REST per la gestione di prenotazioni aule, corsi e notifiche.

## Prerequisiti

- **Java 17**
- **PostgreSQL 13 o superiore** (verificato su 18.3)
- **Maven** (il progetto non include il wrapper `mvnw`)
- **Docker** — facoltativo: serve solo a una classe di test, che senza viene saltata

Spring Boot **3.2.12**.

---

## 1. Creare il database

```sql
CREATE DATABASE prenotazione_aule;
```

Il database può restare **vuoto**: lo schema viene creato da Flyway al primo avvio.

## 2. Configurare i segreti

Le credenziali **non** stanno in `application.properties`, che è versionato. Vanno in
`config/config.properties`, ignorato da git e letto dall'esterno del jar.

Copiare `config/config.properties.example` in `config/config.properties` e valorizzarlo:

```properties
spring.datasource.password=LA_TUA_PASSWORD
jwt.secret=UN_SEGRETO_LUNGO_E_CASUALE
```

Il file deve contenere **solo segreti**. Host, porta e nome del database si cambiano nel
profilo (vedi sotto), non qui.

> `jwt.secret` non ha un valore di default nel codice: se manca, l'avvio **fallisce
> esplicitamente** invece di usare un segreto noto. È voluto.

Per generare un segreto:

```bash
openssl rand -base64 48
```

## 3. Avviare

```bash
mvn spring-boot:run
```

Il profilo predefinito è `dev`. Al primo avvio Flyway crea l'intero schema (log:
`Successfully applied 2 migrations`).

Verifica che funzioni:

```bash
curl -i http://localhost:8080/api/rooms
```

Attendersi **`401 Unauthorized`**: è la risposta corretta senza token, e prova che
database, migrazioni e configurazione si sono risolti. Documentazione interattiva su
<http://localhost:8080/swagger-ui.html> (attiva solo in `dev`).

---

## Configurazione per ambiente

| File | Contenuto |
|---|---|
| `application.properties` | chiavi valide ovunque |
| `application-dev.properties` | database locale, porta 8080, DevTools, CORS su localhost |
| `application-prod.properties` | valori da variabili d'ambiente, DevTools e Swagger disattivati |
| `config/config.properties` | **solo segreti**, non versionato |

### Produzione

```bash
mvn clean package
export CORS_ALLOWED_ORIGINS="https://tuo-frontend.example.it"
java -jar target/prenotazioni-aule-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Variabili riconosciute: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `PORT`, `LOG_FILE`.
La password arriva da `config/config.properties` oppure da `SPRING_DATASOURCE_PASSWORD`.

`CORS_ALLOWED_ORIGINS` **non ha un default**: se non è impostata l'avvio fallisce, invece
di pubblicare in produzione le origini di localhost. In `prod` Swagger è disattivato,
perché lo schema dell'API è servito su percorsi pubblici.

---

## Schema del database

Gestito da **Flyway**, in `src/main/resources/db/migration/`. `ddl-auto` è `validate`:
Hibernate non modifica mai lo schema, verifica soltanto che le entity corrispondano e
fallisce all'avvio se divergono.

Per modificare lo schema si aggiunge una migrazione (`V3__descrizione.sql`). Quelle già
applicate non vanno più modificate: Flyway ne verifica il checksum.

> I file in `src/main/java/com/prenotazioni/sql/` **non** sono lo schema: sono dati di
> popolamento da eseguire a mano. Vedi il `LEGGIMI.md` in quella cartella.

---

## Test

```bash
mvn test      # esegue la suite
mvn verify    # esegue la suite e fa fallire la build sotto l'80% di copertura
```

Il report di copertura finisce in `target/site/jacoco/index.html`.

La suite è composta da unit test senza Spring, test di integrazione HTTP su H2, e **una**
classe su PostgreSQL reale via Testcontainers, che verifica i vincoli di database che H2
non sa esprimere (il vincolo anti-sovrapposizione e i CHECK).

Quella classe richiede Docker: **senza, viene saltata e la build resta verde**. Alla prima
esecuzione con Docker attivo serve la rete per scaricare le immagini:

```bash
docker pull postgres:16-alpine
docker pull testcontainers/ryuk:0.7.0
```
