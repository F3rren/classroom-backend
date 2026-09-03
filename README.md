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

Tutti i segreti stanno in **`.env`**, ignorato da git. Il modello versionato è
`.env.example`, che non contiene valori.

```bash
cp .env.example .env
openssl rand -base64 48     # -> JWT_SECRET
# poi valorizzare SPRING_DATASOURCE_PASSWORD con la password del PostgreSQL locale
```

Non serve fare altro, né in container né fuori: **`.env` lo leggono in due**. Docker
Compose lo trova da solo e inietta i valori nei container; Spring lo importa direttamente,
perché ogni `application.properties` dichiara

```properties
spring.config.import=optional:file:./.env[.properties],optional:file:../.env[.properties]
```

Il formato `KEY=valore` di Compose è anche quello dei file `.properties` di Java, e
`[.properties]` è come lo si dichiara a Spring. `optional:` perché nei container il file
non c'è affatto — i valori arrivano già come variabili d'ambiente.

I nomi però non combaciano, e **la conversione automatica non avviene**: il *relaxed
binding* di Spring tratta le maiuscole con underscore solo per le variabili d'ambiente
vere, non per le chiavi lette da un file. Il ponte è esplicito, in ogni servizio:

```properties
jwt.secret=${JWT_SECRET}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:}
```

> **Niente virgolette e niente backslash nei valori di `.env`.** Compose toglie le
> virgolette, Java le tiene: violarlo non dà errore, dà due letture diverse *dello stesso
> file*, con il file che a guardarlo sembra giusto. I segreti base64 non ne contengono, e
> il vincolo è fissato da `FormatoEnvUnitTest` in `shared`.

`jwt.secret` è **senza valore di ripiego** apposta: se manca, il servizio si rifiuta di
partire invece di firmare token con una chiave vuota. Verificato spostando `.env` e
ottenendo `Could not resolve placeholder 'JWT_SECRET'` — se fosse partito lo stesso,
significherebbe che il segreto arriva da un'altra parte.

`.env` deve contenere **solo segreti e parametri d'ambiente**. Host, porta e nome del
database si cambiano nel profilo (vedi sotto), non qui.

> `jwt.secret` non ha un valore di default nel codice: se manca, l'avvio **fallisce
> esplicitamente** invece di usare un segreto noto. È voluto.

Per generare un segreto:

```bash
openssl rand -base64 48
```

## 3. Avviare

```bash
mvn spring-boot:run -pl prenotazione-service -am
```

`-pl prenotazione-service` sceglie il modulo, `-am` costruisce prima `shared` da cui dipende.

Il profilo predefinito è `dev`. Al primo avvio Flyway crea l'intero schema (log:
`Successfully applied 2 migrations`).

Verifica che funzioni:

```bash
curl -i http://localhost:17102/api/rooms
```

Attendersi **`401 Unauthorized`**: è la risposta corretta senza token, e prova che
database, migrazioni e configurazione si sono risolti. Documentazione interattiva su
<http://localhost:17103/swagger-ui.html> (attiva solo in `dev`).

---

## Struttura del progetto

Il progetto e' un build Maven multi-modulo, e la scomposizione e' completa: ogni servizio
ha il proprio database, il proprio deployable e il proprio Dockerfile (lo stesso, con il
modulo passato come argomento). Il nome di ciascun modulo dice cosa fa — il servizio delle
prenotazioni si chiamava `app`, che non diceva niente.

| Modulo | Porta | Database | Contenuto |
|---|---|---|---|
| `gateway` | **17102** | — | Punto di ingresso unico: instrada per prefisso |
| `broker` | 5672 | — | RabbitMQ: trasporta la notifica di cancellazione |
| `prenotazione-service` | 17103 | `prenotazione_aule` | Aule, prenotazioni, corsi |
| `auth-service` | 17105 | `prenotazione_aule_utenti` | Utenti, login, amministrazione utenti |
| `notifica-service` | 17104 | `prenotazione_aule_notifiche` | Le notifiche |
| `shared` | — | — | Comune a tutti: `ApiEnvelope`, `GlobalExceptionHandler`, 401/403, `JwtVerifier`, `JwtAuthFilter`, `SecurityConfig`, `AppPrincipal`, `Ruolo` |

**Il frontend conosce solo la 17102.** Le porte crescono in sequenza a partire da lì, così
aggiungere un servizio non obbliga a ripensare l'assegnazione (il prossimo servizio prenderà la 17106). La 8080 è volutamente evitata: è troppo comune e collide con altri
progetti sulla stessa macchina. Ogni porta resta sovrascrivibile da variabile d'ambiente
(`GATEWAY_PORT`, `PRENOTAZIONE_PORT`, `AUTH_PORT`, `NOTIFICA_PORT`) senza toccare codice.

### Con Docker

Al primo avvio, una volta sola:

```bash
cp .env.example .env
openssl rand -base64 48    # incollare il risultato in JWT_SECRET dentro .env
```

Poi, sempre:

```bash
docker compose up --build
```

Docker Compose legge `.env` da solo. `JWT_SECRET` non ha un default di proposito: un
segreto con un valore di comodo prima o poi finisce in produzione, quindi lo stack si
rifiuta di partire finché non ne esiste uno vero. Va tenuto **stabile** fra un avvio e
l'altro — cambiarlo invalida tutti i token già emessi, e chi era autenticato riceve un 401
senza una ragione visibile.

`.env` è ignorato da git; il modello versionato è `.env.example`, che non contiene valori.

Alza tre PostgreSQL (uno per servizio), i quattro servizi e pubblica **solo la 17102**.
Gli altri si parlano sulla rete interna e non sono raggiungibili da fuori: le rotte
`/interne/` diventano così irraggiungibili per costruzione, non solo per regola del gateway.

Provato: le quattro immagini si costruiscono, lo stack sale e il giro completo (login →
creazione aula → notifiche) passa dal gateway. Serve comunque inserire a mano il primo
admin nel database utenti, per la ragione spiegata più sotto.

> Dentro i container `prenotazione-service` gira con il profilo **`prod`**, e non è una preferenza:
> `application-dev.properties` ha l'URL del database scritto su `localhost`, quindi con il
> profilo predefinito `DB_HOST` verrebbe ignorato e il servizio morirebbe alla prima
> connessione. Solo `prod` legge le variabili d'ambiente.

### Senza Docker

Servono quattro processi, ognuno in un terminale:

```bash
mvn spring-boot:run -pl prenotazione-service -am              # 17103
mvn spring-boot:run -pl auth-service -am     # 17105
mvn spring-boot:run -pl notifica-service -am # 17104
mvn spring-boot:run -pl gateway -am          # 17102
```

Il gateway non valida i token: instrada e basta. Ogni servizio verifica il JWT da sé, così
resta protetto anche se raggiunto direttamente. Il gateway chiude però dall'esterno le
rotte `/api/notifiche/interne/**`, che sono chiamate fra servizi.

Prima del primo avvio serve il suo database (vuoto: lo schema lo crea Flyway):

```sql
CREATE DATABASE prenotazione_aule_notifiche;
```

`JWT_SECRET` in `.env` deve essere lo stesso per tutti i servizi:
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
| `application-dev.properties` | database locale, porta 17103, DevTools, CORS su localhost |
| `application-prod.properties` | valori da variabili d'ambiente, DevTools e Swagger disattivati |
| `.env` | **solo segreti e parametri d'ambiente**, non versionato |

### Produzione

```bash
mvn clean package
export CORS_ALLOWED_ORIGINS="https://tuo-frontend.example.it"
java -jar prenotazione-service/target/prenotazione-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Variabili riconosciute: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `PORT`, `LOG_FILE`.
La password arriva da `SPRING_DATASOURCE_PASSWORD`: variabile d'ambiente nei container,
letta da `.env` fuori.

`CORS_ALLOWED_ORIGINS` **non ha un default**: se non è impostata l'avvio fallisce, invece
di pubblicare in produzione le origini di localhost. In `prod` Swagger è disattivato,
perché lo schema dell'API è servito su percorsi pubblici.

---

## Organizzazione dei package

Ogni servizio ha un namespace proprio, e `shared` tiene la radice:

| Modulo | Package |
|---|---|
| `shared` | `com.prenotazioni.{dto,model,security,config,util,exception,eventi}` |
| `prenotazione-service` | `com.prenotazioni.prenotazione.*` |
| `auth-service` | `com.prenotazioni.auth.*` |
| `notifica-service` | `com.prenotazioni.notifica.*` |
| `gateway` | `com.prenotazioni.gateway.*` |

Non è una convenzione estetica. Finché `prenotazione-service` stava sotto `com.prenotazioni.*` come `shared`,
tre package erano pubblicati da entrambi i jar e il confine fra i due moduli non era
verificato dal compilatore: una classe poteva usare un membro package-private dell'altro
modulo e compilare. Separando i namespace è successo davvero — `PrenotazioneAuthorizationService`
usava `AppPrincipal` senza import, e ora deve dichiararlo.

Conseguenza pratica: ogni servizio dichiara un `@ComponentScan` esplicito che include i
package di `shared`. Senza, i bean condivisi (filtro JWT, configurazione di sicurezza,
gestore degli errori) resterebbero fuori dalla scansione e il servizio partirebbe senza
autenticazione.

## Comunicazione fra servizi

Due modi, scelti caso per caso e non per gusto:

**Sincrono (REST)** quando il chiamante *deve* sapere l'esito. La cancellazione di un utente
è l'unico caso: `auth-service` rimuove prima i dati negli altri servizi e cancella l'utente
solo se ci è riuscito. Se fallisce, l'utente resta e l'operazione è ripetibile. Con una coda
questa garanzia si perderebbe, e resterebbero righe orfane che la chiave esterna impediva.

**Asincrono (coda RabbitMQ)** quando il fallimento del destinatario non deve fermare nulla.
La notifica di una prenotazione cancellata da un admin: prima era una chiamata REST e andava
persa se `notifica-service` era spento. Ora aspetta in coda. La dipendenza si sposta dal
servizio al broker — la finestra si restringe, non si chiude: se il broker è irraggiungibile
il messaggio si perde comunque, e il fallimento resta loggato e non propagato, perché la
prenotazione è già stata cancellata.

## Il primo amministratore

Su un database utenti vuoto `/api/admin/register` non è raggiungibile: richiede già un
token con ruolo `ADMIN`. Non è una conseguenza della separazione — il monolite aveva lo
stesso vincolo — ma su database nuovi si incontra subito, e prima si usciva dal cerchio
solo con una `INSERT` a mano e un hash BCrypt calcolato fuori.

Ora bastano due variabili in `.env`:

```bash
BOOTSTRAP_ADMIN_EMAIL=tua@email.it
BOOTSTRAP_ADMIN_PASSWORD=unaPasswordLunga
```

All'avvio di `auth-service`, **e solo se la tabella utenti è vuota**, viene creato un
amministratore con quelle credenziali. Poi vanno svuotate, insieme al cambio della password.

La condizione è stretta apposta: a tabella non vuota il meccanismo è **inerte** — non
promuove, non aggiorna, non tocca nessun utente esistente. È ciò che separa un aiuto
all'avvio da una scorciatoia per ottenere privilegi da amministratore, ed è tenuto fermo dai
test in `AvvioPrimoAdminUnitTest`. La creazione passa da `AuthService.register`, la stessa
strada di ogni altro utente, quindi la password attraversa lo stesso `PasswordEncoder`.

Se il database è vuoto e le variabili non ci sono, il servizio parte comunque ma **logga a
`WARN`** come procedere: un database vuoto e silenzioso è esattamente il modo in cui questo
problema si ripresenta.

Vedi [AvvioPrimoAdmin.java](auth-service/src/main/java/com/prenotazioni/auth/AvvioPrimoAdmin.java).

## Schema del database

Gestito da **Flyway**, in `prenotazione-service/src/main/resources/db/migration/`. `ddl-auto` è `validate`:
Hibernate non modifica mai lo schema, verifica soltanto che le entity corrispondano e
fallisce all'avvio se divergono.

Per modificare lo schema si aggiunge una migrazione (`V3__descrizione.sql`). Quelle già
applicate non vanno più modificate: Flyway ne verifica il checksum.

> I file in `scripts/dati-di-esempio/` **non** sono lo schema: sono dati di
> popolamento da eseguire a mano. Vedi il `LEGGIMI.md` in quella cartella.

---

## Test

```bash
mvn test      # esegue la suite di tutti i moduli
mvn verify    # aggiunge il gate di copertura, per modulo
```

I report di copertura finiscono in `shared/target/site/jacoco/index.html` e
`prenotazione-service/target/site/jacoco/index.html`: il gate all'80% e' applicato a ogni modulo
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
