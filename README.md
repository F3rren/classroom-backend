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

```
services/           i quattro deployable: uno per servizio
  gateway/            l'unico esposto (17102)
  auth-service/
  prenotazione-service/
  notifica-service/
shared/             non e' un servizio: e' la libreria che i quattro importano
```

La distinzione fra `services/` e `shared/` e' l'unica cosa che quell'albero deve dire, ed e'
il motivo per cui `shared` non sta dentro `services/`: non si avvia, non ha una porta.

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

### Dentro un servizio

Tutti e tre i servizi applicativi hanno la stessa forma, così passare dall'uno all'altro non
richiede di reimparare dove stanno le cose:

```
services/auth-service/src/main/java/com/prenotazioni/auth/
  controller/     riceve HTTP, non decide nulla di dominio
  service/        le regole; e' qui che nascono le eccezioni di dominio
  repository/     interfacce Spring Data
  model/          entita' JPA
  dto/            cio' che entra ed esce, separato dalle entita'
  client/         chiamate verso GLI ALTRI servizi (solo dove servono)

shared/src/main/java/com/prenotazioni/
  config/         SecurityConfig, JwtAuthFilter, CorrelazioneRichiesta, gestori 401/403
  exception/      GlobalExceptionHandler e le eccezioni di dominio
  security/       JwtVerifier, AppPrincipal
  eventi/         i messaggi che viaggiano su RabbitMQ e i nomi di code ed exchange
  dto/            ApiEnvelope, l'involucro di ogni risposta
  model/          Ruolo
  util/           LogSanitizer
```

I nomi tecnici restano in inglese perché sono la convenzione di Spring e chiunque li
riconosce; i nomi di dominio sono in italiano, come il dominio. `messaggistica/` in
`prenotazione-service` e `eventi/` in `notifica-service` sono i due lati della stessa coda:
chi pubblica e chi ascolta.

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

### Perche' un file e' .yml e dieci sono .properties

Non e' una svista rimasta indietro: e' una regola, ed e' **`.properties` ovunque, `.yml`
solo dove la configurazione e' una lista di oggetti annidati**. Oggi succede in un posto
solo, le rotte del gateway.

La differenza si vede meglio guardando cosa diventerebbero quelle rotte in properties:

```properties
spring.cloud.gateway.routes[3].id=autenticazione
spring.cloud.gateway.routes[3].uri=${AUTH_SERVICE_URL:http://localhost:17105}
spring.cloud.gateway.routes[3].predicates[0]=Path=/api/auth/**,/api/admin/utenti/**
```

L'ordine delle rotte non e' decorativo: e' cio' che manda `/api/admin/utenti` ad
auth-service invece che a prenotazione-service, perche' entrambe le rotte accettano quel
percorso e vince la prima. Scritto in properties, quell'ordine vive negli **indici**, e
inserire una rotta a meta' vuol dire rinumerare tutte quelle sotto. Sbagliare la
rinumerazione da' un 404 senza errori di configurazione e senza niente nei log.

Nella direzione opposta, convertire i dieci file a YAML costerebbe piu' di quanto renda:
sono 243 righe di configurazione e **501 di commento**, cioe' due righe di spiegazione per
ogni impostazione, da portare attraverso un formato sensibile all'indentazione — e un
errore di configurazione non fa fallire la build, si vede all'avvio o non si vede affatto.

> **Mai i due formati insieme nella stessa cartella.** Spring caricherebbe entrambi i file
> e `.properties` vincerebbe: chi ha appena scritto il `.yml` lo vedrebbe ignorato senza
> alcun segnale, e cercherebbe il difetto nel codice invece che nel file accanto. La CI ha
> un passo che si rifiuta di proseguire se trova una coppia del genere.

### Produzione

```bash
mvn clean package
export CORS_ALLOWED_ORIGINS="https://tuo-frontend.example.it"
java -jar services/prenotazione-service/target/prenotazione-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Variabili riconosciute: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `PRENOTAZIONE_PORT`.
I log vanno su stdout: in container li raccoglie Docker (`docker compose logs`). La
scrittura su file resta solo nel profilo `dev`.
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

## Flusso di una richiesta

Dal browser alla riga di database, con i punti in cui qualcosa può fermarla:

```
  browser
     |  POST /api/prenotazioni      Authorization: Bearer <token>
     v
  gateway :17102 ------------------------------------------------ l'unica porta pubblicata
     |  1. CorrelazioneAlBordo conia X-Request-Id (o riusa quello ricevuto)
     |  2. sceglie la rotta per prefisso del percorso
     |     -> nessuna rotta corrisponde ......................... 404
     |     -> il servizio non risponde ......................... 503
     v
  prenotazione-service :17103 ------------------------- non raggiungibile dall'esterno
     |  3. CorrelazioneRichiesta rimette X-Request-Id in MDC
     |  4. JwtAuthFilter verifica la firma del token, da solo
     |     -> token assente, scaduto o falso .................... 401
     |  5. SecurityConfig controlla il ruolo
     |     -> ruolo insufficiente ............................... 403
     |  6. Bean Validation sul corpo
     |     -> campo mancante o fuori intervallo ................. 400
     v
  controller -> service -> repository -> PostgreSQL
     |     -> aula gia' occupata ............................... 409
     |     -> vincolo del database violato ..................... 409
     v
  risposta: sempre lo stesso involucro JSON, con lo stesso X-Request-Id
```

**Il gateway non valida i token.** Non ha alcuna configurazione di sicurezza: instrada e
basta. La verifica la fa ogni servizio per conto proprio, ed è una scelta — un gateway che
autentica diventa il punto in cui tutto passa e tutto si ferma, e i servizi dietro finirebbero
per fidarsi di lui senza controllare, restando indifesi il giorno in cui qualcuno li
raggiungesse per altra via.

**L'identificativo di richiesta attraversa tutto.** Nasce al gateway, viaggia
nell'intestazione `X-Request-Id`, finisce in MDC dentro ogni servizio, e viene rimandato
indietro nella risposta e nel campo `sessionId` del corpo. Attraversa anche le chiamate REST
fra servizi e gli eventi su RabbitMQ. È l'unica chiave che permette di ricostruire
un'operazione che tocca tre servizi, tre database e due thread diversi.

## Token e autenticazione

**Li emette solo `auth-service`.** È l'unico modulo con `jjwt-impl` fra le dipendenze di
compilazione: gli altri hanno solo `jjwt-api` e possono verificare, non firmare. Il confine è
imposto dal classpath, non da una regola scritta.

**Li verifica ogni servizio da solo**, senza chiamare nessuno. È possibile perché la firma è
HMAC con un segreto condiviso — `JWT_SECRET`, lo stesso per tutti — e il token porta con sé
tutto ciò che serve a decidere:

| Claim | A cosa serve |
|---|---|
| `sub` | l'email di chi ha fatto il login |
| `id` | l'id numerico, usato come proprietario di prenotazioni e notifiche |
| `nome`, `username` | denormalizzati nelle prenotazioni, così mostrarle non richiede di interrogare il servizio utenti |
| `ruolo` | `admin` o `user`, da cui Spring costruisce l'authority che `@PreAuthorize` cerca |

Il prezzo di questa scelta è che **un token non si può revocare**: dura un'ora e resta valido
fino alla scadenza. Cancellare un utente non lo disconnette. È il rovescio della validazione
offline, ed è consapevole — «ho cancellato l'utente» e «l'utente non può più fare niente»
oggi sono due affermazioni diverse.

`JWT_SECRET` deve essere **identico** in tutti i servizi, altrimenti chi non ce l'ha uguale
rifiuta ogni token con un 401. Cambiarlo invalida tutti quelli già emessi.

## Gestione degli errori

**Un solo involucro, per ogni risposta.** Successo o errore, la forma non cambia: chi legge
non deve indovinare quale schema ha ricevuto.

```json
{
  "success": false,
  "error": "BOOKING_CONFLICT",
  "message": "Aula 3 occupata dal 2026-09-10T09:00 al 2026-09-10T11:00",
  "userMessage": "L'aula non e' disponibile nel periodo richiesto.",
  "data": null,
  "timestamp": "2026-09-10 08:14:22",
  "sessionId": "REQ_A42118C7"
}
```

`message` è per chi sviluppa, `userMessage` è per chi usa: separarli evita di dover scegliere
fra un messaggio inutile a chi indaga e uno incomprensibile a chi legge lo schermo.
`sessionId` è l'identificativo di richiesta, quindi una segnalazione può citarlo e i log di
tutti e tre i servizi si trovano cercando quella stringa.

**I controller non traducono gli errori.** Lanciano un'eccezione di dominio e
`GlobalExceptionHandler` — uno solo, in `shared`, condiviso da tutti i servizi — decide lo
status una volta sola:

| Eccezione | Status | Quando |
|---|---|---|
| `InvalidRequestException` | 400 | la richiesta chiede qualcosa che non ha senso |
| `MethodArgumentNotValidException` | 400 | Bean Validation ha respinto il corpo |
| `AccessDeniedException` | 403 | autenticato, ma non suo e non admin |
| `ResourceNotFoundException` | 404 | l'oggetto indicato non esiste |
| `DomainConflictException` | 409 | esiste, ma il suo stato non ammette l'operazione |
| `BookingConflictException` | 409 | sovrapposizione di prenotazioni |
| `DataIntegrityViolationException` | 409 | un vincolo del database ha detto no |
| `ServizioNonDisponibileException` | 503 | un servizio a valle non risponde: **ripetere ha senso** |
| qualunque altra | 500 | imprevisto, con lo stack trace nei log |

La distinzione fra 500 e 503 non è formale: suggeriscono due azioni diverse. Un 500 dice «è
rotto qualcosa», un 503 dice «riprova» — e in un sistema dove ripetere è ciò che porta a
termine una cancellazione a cascata, dirlo cambia l'esito.

`IllegalArgumentException` **non** è mappata a 400 di proposito: segnala un errore di
programmazione, non una richiesta sbagliata, e trasformarla in 400 nasconderebbe difetti
dietro una risposta che sembra normale.

**Il gateway ha il proprio gestore**, perché è WebFlux e non condivide quello dei servizi.
Produce lo stesso involucro — è un test a tenere allineate le due forme — e distingue un
servizio irraggiungibile (503) da un percorso senza rotta (404).

## Comunicazione fra servizi

Due modi, scelti caso per caso e non per gusto:

**Sincrono (REST)** quando il chiamante *deve* sapere l'esito. La cancellazione di un utente
è l'unico caso: `auth-service` rimuove prima i dati negli altri servizi e cancella l'utente
**solo se ci è riuscito**. Con una coda questa garanzia si perderebbe, e resterebbero righe
orfane che la chiave esterna impediva.

L'invariante è che l'utente se ne va per ultimo: finché c'è lui, le righe rimaste altrove
hanno ancora un proprietario e l'operazione si può ripetere. È tenuta ferma da un test, che
verifica anche l'*ordine* delle due operazioni — invertirle la perderebbe senza far fallire
nient'altro.

Ripetere però non deve dipendere da una persona che se ne accorge: le chiamate a valle si
ritentano tre volte con attesa crescente, perché la gran parte di questi guasti dura meno di
un secondo. Non si ritenta su un 4xx — è un rifiuto, non un guasto. Se dopo i tentativi
qualcosa resta indietro, la risposta è un **503 che nomina i dati non cancellati**, non un
500 generico.

**Asincrono (coda RabbitMQ)** quando il fallimento del destinatario non deve fermare nulla.
La notifica di una prenotazione cancellata da un admin: prima era una chiamata REST e andava
persa se `notifica-service` era spento. Ora aspetta in coda. La dipendenza si sposta dal
servizio al broker — la finestra si restringe, non si chiude: se il broker è irraggiungibile
il messaggio si perde comunque, e il fallimento resta loggato e non propagato, perché la
prenotazione è già stata cancellata.

## Il primo amministratore

Su un database utenti vuoto `/api/admin/utenti` non è raggiungibile: richiede già un
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

Vedi [AvvioPrimoAdmin.java](services/auth-service/src/main/java/com/prenotazioni/auth/AvvioPrimoAdmin.java).

## Schema del database

Gestito da **Flyway**, in `services/prenotazione-service/src/main/resources/db/migration/`. `ddl-auto` è `validate`:
Hibernate non modifica mai lo schema, verifica soltanto che le entity corrispondano e
fallisce all'avvio se divergono.

Per modificare lo schema si aggiunge una migrazione (`V3__descrizione.sql`). Quelle già
applicate non vanno più modificate: Flyway ne verifica il checksum.

---

## Test

```bash
mvn test      # la suite di tutti i moduli. Funziona SENZA Docker.
mvn verify    # aggiunge il gate di copertura. RICHIEDE Docker.
```

**Senza Docker si usa `mvn test`.** Le tre classi Testcontainers si saltano da sole
(`Skipped: 20`, segnalato da Maven come warning perche' resti visibile) e il resto gira
normalmente: e' il comando del ciclo di sviluppo.

`mvn verify` invece richiede Docker, e non e' una svista. Il gate certifica che il codice
sia provato, e non puo' certificare cio' che non ha potuto eseguire: saltata
`MessaggisticaCancellazioniTest`, notifica-service scende a **0.67** contro una soglia di
0.80, perche' quella classe e' l'unica a esercitare la topologia AMQP. Abbassare la soglia
renderebbe il gate una formalita'.

> Il messaggio di Maven in quel caso dice solo `Coverage checks have not been met`, **senza
> nominare Docker**. Se lo incontri, la causa e' quasi sempre questa.

I report di copertura finiscono in `shared/target/site/jacoco/index.html` e
`services/prenotazione-service/target/site/jacoco/index.html`: il gate all'80% e' applicato a ogni modulo
separatamente, perche' il denominatore cambia da modulo a modulo.

La suite è unit test senza Spring, test di integrazione HTTP su H2, e **tre** classi su
servizi reali in container:

| Classe | Serve a verificare cosa non si puo' verificare altrimenti |
|---|---|
| `PostgresSchemaConstraintsTest` | il vincolo anti-sovrapposizione `EXCLUDE USING gist`, che in H2 non esiste |
| `VincoliUtentiTest` | i `CHECK` sui ruoli, che H2 applica in modo diverso — su H2 un test passerebbe **anche col vincolo assente** |
| `MessaggisticaCancellazioniTest` | l'intera topologia AMQP: exchange, routing key, binding, converter, listener. Chiamare il metodo del consumatore proverebbe il metodo, non che il messaggio arrivi |

Tutte e tre hanno `disabledWithoutDocker = true`, quindi senza Docker si saltano invece di
far fallire la build.

**Il salto e' innocuo in locale e impossibile in CI**, ed e' una distinzione voluta: il passo
di guardia in `.github/workflows/ci.yml` cerca da se' le classi `@Testcontainers` e fallisce
se un report dice `skipped` diverso da zero, se manca, o se contiene zero test. Verificato
spegnendo Docker davvero: le tre classi producono report con `skipped` 3, 4 e 13, e la
guardia le nomina tutte e tre.

> Non e' una precauzione teorica. Prima che quella guardia esistesse, **quattro asserzioni
> fallite sono rimaste nascoste per giorni** dietro una classe che si saltava in silenzio.

Alla prima esecuzione con Docker attivo serve la rete per scaricare le immagini:

```bash
docker pull postgres:16-alpine
docker pull rabbitmq:3.13-management-alpine
docker pull testcontainers/ryuk:0.7.0
```
