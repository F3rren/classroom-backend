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
mvn spring-boot:run -pl app -am
```

`-pl app` sceglie il modulo applicativo, `-am` costruisce prima `shared` da cui dipende.

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

## Struttura del progetto

Il progetto e' un build Maven multi-modulo. E' il primo passo della scomposizione verso
un'architettura a microservizi: la struttura e' divisa, il deployable e' ancora uno solo.

| Modulo | Porta | Database | Contenuto |
|---|---|---|---|
| `shared` | — | — | Comune a tutti i servizi: `ApiEnvelope`, `GlobalExceptionHandler`, 401/403, `JwtVerifier`, `JwtAuthFilter`, `SecurityConfig`, `AppPrincipal`, `Ruolo` |
| `notifica-service` | 8081 | `prenotazione_aule_notifiche` | Le notifiche |
| `app` | 8080 | `prenotazione_aule` | Aule, prenotazioni, corsi, utenti |

Il servizio notifiche va avviato a parte:

```bash
mvn spring-boot:run -pl notifica-service -am
```

Prima del primo avvio serve il suo database (vuoto: lo schema lo crea Flyway):

```sql
CREATE DATABASE prenotazione_aule_notifiche;
```

`jwt.secret` in `config/config.properties` deve essere lo stesso per entrambi i servizi:
e' cio' che permette a ognuno di validare i token da solo, senza chiamare gli altri. E'
anche il motivo per cui i test possono firmarsi i propri token invece di creare un utente.

`shared` e' una libreria e non viene ripacchettata come jar eseguibile. Ci entra solo cio'
la cui chiusura transitiva non tocca il dominio: e' il compilatore, non una convenzione, a
verificare che il confine regga.

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
java -jar app/target/prenotazioni-aule-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Variabili riconosciute: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `PORT`, `LOG_FILE`.
La password arriva da `config/config.properties` oppure da `SPRING_DATASOURCE_PASSWORD`.

`CORS_ALLOWED_ORIGINS` **non ha un default**: se non è impostata l'avvio fallisce, invece
di pubblicare in produzione le origini di localhost. In `prod` Swagger è disattivato,
perché lo schema dell'API è servito su percorsi pubblici.

---

## Schema del database

Gestito da **Flyway**, in `app/src/main/resources/db/migration/`. `ddl-auto` è `validate`:
Hibernate non modifica mai lo schema, verifica soltanto che le entity corrispondano e
fallisce all'avvio se divergono.

Per modificare lo schema si aggiunge una migrazione (`V3__descrizione.sql`). Quelle già
applicate non vanno più modificate: Flyway ne verifica il checksum.

> I file in `app/src/main/java/com/prenotazioni/sql/` **non** sono lo schema: sono dati di
> popolamento da eseguire a mano. Vedi il `LEGGIMI.md` in quella cartella.

---

## Test

```bash
mvn test      # esegue la suite di tutti i moduli
mvn verify    # aggiunge il gate di copertura, per modulo
```

I report di copertura finiscono in `shared/target/site/jacoco/index.html` e
`app/target/site/jacoco/index.html`: il gate all'80% e' applicato a ogni modulo
separatamente, perche' il denominatore cambia da modulo a modulo.

La suite è composta da unit test senza Spring, test di integrazione HTTP su H2, e **una**
classe su PostgreSQL reale via Testcontainers, che verifica i vincoli di database che H2
non sa esprimere (il vincolo anti-sovrapposizione e i CHECK).

Quella classe richiede Docker: **senza, viene saltata e la build resta verde**. Alla prima
esecuzione con Docker attivo serve la rete per scaricare le immagini:

```bash
docker pull postgres:16-alpine
docker pull testcontainers/ryuk:0.7.0
```
