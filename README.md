# Classroom Booking — Backend

A REST API for managing classroom bookings, courses and notifications.

## Prerequisites

- **Java 17**
- **PostgreSQL 13 or later** (checked against 18.3)
- **Maven** (the project does not ship the `mvnw` wrapper)
- **Docker** — optional: only a few test classes need it, and they skip themselves without it

Spring Boot **3.2.12**.

---

## 1. Create the database

```sql
CREATE DATABASE classroom;
```

The database can stay **empty**: Flyway creates the schema on the first start.

## 2. Configure the secrets

Every secret lives in **`.env`**, which git ignores. The versioned template is
`.env.example`, and it holds no values.

```bash
cp .env.example .env
openssl rand -base64 48     # -> JWT_SECRET
# then set SPRING_DATASOURCE_PASSWORD to your local PostgreSQL password
```

Nothing else is needed, in a container or outside one: **two things read `.env`**. Docker
Compose finds it on its own and injects the values into the containers; Spring imports it
directly, because every `application.properties` declares

```properties
spring.config.import=optional:file:./.env[.properties],optional:file:../.env[.properties]
```

Compose's `KEY=value` format is also that of Java `.properties` files, and `[.properties]` is
how you declare that to Spring. `optional:` because in the containers the file is not there
at all — the values arrive as environment variables already.

The names do not line up, though, and **the automatic conversion does not happen**: Spring's
*relaxed binding* treats uppercase-with-underscores only for real environment variables, not
for keys read from a file. The bridge is explicit, in every service:

```properties
jwt.secret=${JWT_SECRET}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:}
```

> **No quotes and no backslashes in `.env` values.** Compose strips quotes, Java keeps them:
> breaking that raises no error, it produces two different readings *of the same file*, from
> a file that looks right. Base64 secrets contain neither, and the constraint is pinned by
> `EnvFormatUnitTest` in `shared`.

`jwt.secret` has **no fallback** on purpose: if it is missing, the service refuses to start
rather than sign tokens with an empty key. Checked by moving `.env` away and getting
`Could not resolve placeholder 'JWT_SECRET'` — had it started anyway, that would have meant
the secret was coming from somewhere else.

`.env` must hold **only secrets and environment parameters**. The database host, port and
name are changed in the profile (see below), not here.

## 3. Start it

```bash
mvn spring-boot:run -pl booking-service -am -Dspring-boot.run.profiles=dev
```

`-pl booking-service` picks the module, `-am` builds `shared` first, which it depends on.

The default profile is `prod` - locked down, Swagger off, and (for this service) it demands
`CORS_ALLOWED_ORIGINS` with no fallback, so a bare `mvn spring-boot:run` with no profile at
all now refuses to start. `-Dspring-boot.run.profiles=dev` opts into the development
conveniences instead: Swagger UI, DevTools, and the settings this database already relies on.
On the first start Flyway creates the whole schema.

Check that it works:

```bash
curl -i http://localhost:17102/api/rooms
```

Expect **`401 Unauthorized`**: that is the correct answer without a token, and it proves the
database, the migrations and the configuration all resolved. Interactive documentation at
<http://localhost:17103/swagger-ui.html> (enabled only in `dev`).

---

## The project layout

```
services/           the four deployables: one per service
  gateway/            the only one exposed (17102)
  auth-service/
  booking-service/
  notification-service/
shared/             not a service: the library the four of them import
```

The distinction between `services/` and `shared/` is the only thing that tree has to say, and
it is why `shared` does not sit inside `services/`: it does not start, and it has no port.

The project is a multi-module Maven build, and the split is complete: every service has its
own database, its own deployable and its own Dockerfile (the same one, with the module passed
as an argument). Each module's name says what it does — the booking service used to be called
`app`, which said nothing.

| Module | Port | Database | Contents |
|---|---|---|---|
| `gateway` | **17102** | — | The single entry point: it routes by prefix |
| `broker` | 5672 | — | RabbitMQ: it carries the cancellation notification and the user-deletion event |
| `booking-service` | 17103 | `classroom` | Rooms, bookings, courses |
| `auth-service` | 17105 | `classroom_users` | Users, login, user administration |
| `notification-service` | 17104 | `classroom_notifications` | The notifications |
| `shared` | — | — | Common to all: `ApiEnvelope`, `GlobalExceptionHandler`, 401/403, `JwtVerifier`, `JwtAuthFilter`, `SecurityConfig`, `AppPrincipal`, `Role` |

### Inside a service

All three application services have the same shape, so moving between them does not mean
relearning where things are:

```
services/auth-service/src/main/java/com/classroom/auth/
  controller/     receives HTTP, decides nothing about the domain
  service/        the rules; this is where the domain exceptions are born
  repository/     Spring Data interfaces
  model/          JPA entities
  dto/            what comes in and goes out, kept apart from the entities
  messaging/      publishes UserDeletedEvent when an admin deletes a user

shared/src/main/java/com/classroom/
  config/         SecurityConfig, JwtAuthFilter, RequestCorrelationFilter, the 401/403 handlers
  exception/      GlobalExceptionHandler, the domain exceptions, ProtocolError
  security/       JwtVerifier, AppPrincipal
  events/         the messages that travel on RabbitMQ, and the queue and exchange names
  dto/            ApiEnvelope, the wrapper around every response
  model/          Role
  util/           LogSanitizer
```

`messaging/` (in `auth-service` and `booking-service`) and `events/` (in `notification-service`)
are where each service's side of the two queues lives. `booking-service` is on both sides at
once: it publishes `BookingCancelledEvent` and, in the same package, consumes
`UserDeletedEvent`. There is no more service-to-service REST client in this codebase — every
call that once went there is one of these two events instead.

### The rule on language

**Everything a programmer reads is English. Only what a person using the system reads stays
Italian.**

That is the whole rule, and it is worth being precise about where the line falls, because it
does not fall where you might expect.

| | Examples | Why |
|---|---|---|
| everything → **English** | class and method names, variables, comments, log lines, endpoints, JSON keys, table and column names, status values, error codes, Swagger summaries | the code has to be readable by any programmer, and the rest of the Java world is in English |
| what the user reads → **Italian** | `userMessage` in every error, the Bean Validation messages, the title and body of a notification, the `message` of a **successful** response | those are the words of the people using the system, and they are the one place the code's language is not the right one |

The asymmetry in the last row is easy to miss. On an **error** the envelope carries both
fields, and they have two different readers:

```json
{
  "error":       "BOOKING_CONFLICT",
  "message":     "Room 3 busy from 2026-09-10T09:00 to 2026-09-10T11:00",
  "userMessage": "L'aula non e' disponibile nel periodo richiesto."
}
```

`message` is read by whoever is investigating a failure — logs, development, a report passed
to somebody else. `userMessage` is read by the person booking a room.

On a **success** there is no `userMessage`, and `message` is the only text field there is:

```json
{ "success": true, "message": "Aula creata con successo", "data": { ... } }
```

So on success `message` is the user's field, and it stays Italian. A test pins that down.

> If more than one language were ever needed, the road is `MessageSource` with
> `messages_xx.properties` files and the language chosen from the `Accept-Language` header.
> It is not there today, and with a single language it would be a mechanism to maintain with
> nobody using it. The `error` field is already a stable code (`BOOKING_CONFLICT`,
> `USER_ALREADY_EXISTS`), so a frontend that wants to translate on its own can do so from
> that without waiting.

**The frontend knows only 17102.** The ports run in sequence from there, so adding a service
does not mean rethinking the allocation (the next one takes 17106). 8080 is deliberately
avoided: it is too common and collides with other projects on the same machine. Every port
stays overridable from an environment variable (`GATEWAY_PORT`, `BOOKING_PORT`, `AUTH_PORT`,
`NOTIFICATION_PORT`) with no code to touch.

### With Docker

The first time, once:

```bash
cp .env.example .env
openssl rand -base64 48    # paste the result into JWT_SECRET inside .env
```

Then, always:

```bash
docker compose up --build
```

Docker Compose reads `.env` on its own. `JWT_SECRET` has no default on purpose: a secret with
a convenience value ends up in production sooner or later, so the stack refuses to start
until a real one exists. It has to be kept **stable** between runs — changing it invalidates
every token already issued, and whoever was logged in gets a 401 for no visible reason.

`.env` is ignored by git; the versioned template is `.env.example`, which holds no values.

#### It starts in DEVELOPMENT mode

`docker-compose.yml` is the development file, and not "production with a couple of
conveniences". The five containers start on `SPRING_PROFILES_ACTIVE=dev,docker`, which is
what gives you:

| | |
|---|---|
| the SQL Hibernate is running | `logging.level.org.hibernate.SQL=DEBUG`, formatted, with the bound parameters — through the logger, so every statement carries the `requestId` of the request that caused it |
| the DEBUG narration | `START login`, `room 3 is free over the requested period`: written by the code all along, printed by nobody until the profile said so |
| errors that say what went wrong | `include-message` and `include-binding-errors` on, `?trace=true` for the stack trace — the mirror image of prod's three `never` |
| Swagger | each service's own, and the aggregated UI on the gateway |
| the broker | published on `127.0.0.1`, see below |

Two profiles and not one: `dev` says WHAT behaviour is wanted, `docker` says WHERE it is
running. The second is now a single guard — the `springProfile` conditions in
`logback-spring.xml` that keep the rolling file appender out of the image, since its relative
path resolves somewhere unwritable inside a container. It used to also carry
`booking-service`'s Flyway baseline put back to `false`, in `application-docker.properties`,
for a container's always-empty database; that file is gone now that the containers reach the
developer's own local PostgreSQL — the same one a plain `dev` run always targeted — so the
baseline setting is the same either way (see below).

**Production names its files explicitly** and puts all of it back:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

#### The three services connect to your local PostgreSQL

`docker-compose.yml` starts no PostgreSQL of its own. It used to start three — one per
service, published on `127.0.0.1:15432`/`15433`/`15434` so pgAdmin could reach them — which
meant a second, empty PostgreSQL next to whatever is already on the host, the common case.
Now `DB_HOST` is `host.docker.internal`, the name Docker Desktop resolves to the machine
running it, and the three containers reach the exact same instance a plain
`mvn spring-boot:run` would.

**The three databases have to exist beforehand** on that instance — Flyway still creates
every table on first start, the same as always, it just cannot create a database it has no
connection to yet:

```sql
CREATE DATABASE classroom_users;
CREATE DATABASE classroom;
CREATE DATABASE classroom_notifications;
```

`SPRING_DATASOURCE_PASSWORD` in `.env` — already the local PostgreSQL's password for
`mvn spring-boot:run` — is what the containers authenticate with too, since they are talking
to the very same instance. `booking-service` additionally reads `FLYWAY_BASELINE_ON_MIGRATE`
(default `true`): set it to `false` if the local `classroom` database is a fresh one rather
than the developer's old pre-Flyway one — see `application-dev.properties`.

Inspecting the data is whatever you already use for the local instance — pgAdmin or `psql` on
`localhost:5432` — with nothing extra published for it.

`docker-compose.prod.yml` does not follow this: production runs its **own** three PostgreSQL
containers, unpublished, because "connect to whatever is on the deploying machine" is a
development convenience, not a deployment story. See [Production](#production).

#### The broker's management UI

Still published, on <http://127.0.0.1:15672> (`guest`/`guest` by default): it answers the
question this architecture actually raises in development — did the event reach the queue,
and did anybody consume it. `127.0.0.1` and not `0.0.0.0`: reachable from this machine and no
further, where without the address Docker would publish on every interface, broker
credentials and all.

`docker-compose.prod.yml` takes it away again with `ports: !reset []`, which **needs Compose
2.24 or newer**. The tag exists because compose merges `ports` by APPENDING: without it there
is no way to take back a port declared in the base file. On an older compose the command fails
loudly rather than deploying a published broker — the right way round.

#### What is reachable, and what is not

The base file publishes **17102** and the broker's management UI above. The four application
services publish nothing in either mode: they talk on the internal network, so the
`/internal/` routes are unreachable from outside by construction and not merely by the
gateway's rule.

Swagger for all three APIs is aggregated behind the gateway, on the one port the frontend
knows: <http://localhost:17102/swagger-ui.html>, with a dropdown to switch between them.
Nothing is published per service. In production the gateway is on `prod`, `application-dev.yml`
is not loaded, and springdoc registers nothing at all — `/swagger-ui.html` 404s.

The database connection never depended on the profile either way: it lives in
`application.properties` with `${DB_HOST}`-style placeholders that take real values in a
container and fall back to `localhost` outside one.

### Without Docker

Four processes, each in its own terminal. The three application services default to `prod`
(locked down, Swagger off); pass `-Dspring-boot.run.profiles=dev` for the development
conveniences - `booking-service` needs it just to start, since `prod` demands
`CORS_ALLOWED_ORIGINS` with no fallback:

```bash
mvn spring-boot:run -pl booking-service -am -Dspring-boot.run.profiles=dev        # 17103
mvn spring-boot:run -pl auth-service -am -Dspring-boot.run.profiles=dev           # 17105
mvn spring-boot:run -pl notification-service -am -Dspring-boot.run.profiles=dev   # 17104
mvn spring-boot:run -pl gateway -am -Dspring-boot.run.profiles=dev                # 17102
```

The gateway does not validate tokens: it routes, and nothing else. Every service verifies the
JWT itself, so it stays protected even when reached directly. The gateway does close off
`/api/notifications/internal/**` and `/api/bookings/internal/**` from outside, kept as a
standing rule for the whole namespace even now that nothing lives under either path: both
of the endpoints that used to be there were replaced by the events described below.

Before the first start each service needs its database (empty: Flyway creates the schema):

```sql
CREATE DATABASE classroom_users;
CREATE DATABASE classroom_notifications;
```

`JWT_SECRET` in `.env` has to be the same for every service: that is what lets each of them
validate tokens on its own, without calling the others. It is also why the tests can sign
their own tokens instead of creating a user.

`shared` is a library and is not repackaged as an executable jar. Only what has a transitive
closure that does not touch the domain goes in: it is the compiler, not a convention, that
checks the boundary holds.

---

## Configuration per environment

| File | Contents |
|---|---|
| `application.properties` | everything needed to start, with `${VAR:default}` placeholders |
| `application-dev.properties` | what a development run looks like: the SQL, the DEBUG narration, talking errors, Swagger on, a small pool |
| `application-prod.properties` | **hardening only**: Swagger off, a wider pool, Flyway made safe — and each service's own default profile |
| `.env` | **secrets and environment parameters only**, not versioned |

**The connection and the port never depended on a profile.** They live in
`application.properties` in the `${DB_HOST:localhost}` form, which takes the real values in a
container and falls back to the development defaults outside one. **The profile itself now
does**, though: each service's default profile is `prod`, not `dev`, so forgetting
`SPRING_PROFILES_ACTIVE` lands you on the locked-down settings — Swagger off, no detail in
error responses — instead of the permissive ones. `SPRING_PROFILES_ACTIVE=dev` is what opts
back into development. For `booking-service` specifically, `prod` also demands
`CORS_ALLOWED_ORIGINS` with no fallback, so forgetting the profile there refuses to start
rather than merely running less protected.

`docker-compose.yml` sets `SPRING_PROFILES_ACTIVE=dev,docker` on all five containers, and
`docker-compose.prod.yml` puts them back to `prod`: the file you run says which environment
you are in, once, instead of each service being nudged one flag at a time.

That is a reversal. The base file used to leave everything on `prod` and override just
`SPRINGDOC_API_DOCS_ENABLED` and `SPRINGDOC_SWAGGER_UI_ENABLED`, because switching the whole
profile in a container had been tried and reverted twice: `dev` turns on a file log appender
with nowhere writable to go inside the image, and for `booking-service` a Flyway baseline
meant for one developer's pre-Flyway database, wrong against what was then a container's
always-empty one. Both were worked around by not using the profile; the first is now fixed
where it belongs — a `docker` condition on the appender in `logback-spring.xml`. The second
stopped being a problem a different way: `docker-compose.yml`'s containers now connect to that
same developer's local database instead of an empty one of their own (see
[With Docker](#with-docker)), so the baseline setting that was already right for `dev` is
right for `dev,docker` too, and the override that used to live in
`application-docker.properties` had nothing left to do. The two `SPRINGDOC_*` variables are
still read, and still default to off under `prod`: what changed is that nothing has to
remember to pass them.

It was not always so, and getting the connection out of the profiles cost dearly the first
time: the database URL lived only in `application-dev.properties`, written against
`localhost`, and with the OLD `spring.profiles.default=dev` a container without that variable
started pointing at localhost and died on its first query. The symptom was a 503 from the
gateway, and the cause sat three files away. That is fixed by keeping the connection
profile-independent, as above — not by which profile is the default, which is a separate
question the project answered the other way afterwards: default to `prod`, so a forgotten
profile degrades towards protection, not away from it.

All four modules have a development profile now (`application-dev.yml` for the gateway, which
is `.yml` for the reason below). They used not to: `dev` meant only "prod's hardening does not
apply", which left the visible behaviour of a development run to whatever the base file's
defaults happened to be — the DEBUG narration the code writes was printed by nobody, and
`booking-service`'s `dev` profile declared exactly the same log levels as its `prod` one.

There is no `application-docker.properties` any more. It used to exist for `booking-service`
alone, one line putting the Flyway baseline back to `false` because a container's database was
always new; now that `docker-compose.yml` points the containers at the developer's own local
database instead (see [With Docker](#with-docker)), that line had nothing left to override —
deleting it, rather than leaving a file that overrides nothing, is the same reasoning that
already kept the other three services from getting an empty one of their own.

### Why one file is .yml and ten are .properties

It is not an oversight left behind: it is a rule, and it is **`.properties` everywhere,
`.yml` only where the configuration is a list of nested objects**. Today that happens in one
place, the gateway's routes.

The difference is clearest when you look at what those routes would become in properties:

```properties
spring.cloud.gateway.routes[3].id=authentication
spring.cloud.gateway.routes[3].uri=${AUTH_SERVICE_URL:http://localhost:17105}
spring.cloud.gateway.routes[3].predicates[0]=Path=/api/auth/**,/api/admin/users/**
```

The order of the routes is not decorative: it is what sends `/api/admin/users` to
auth-service rather than booking-service, because both routes accept that path and the first
one wins. Written in properties, that order lives in the **indexes**, and inserting a route
in the middle means renumbering every one below it. Getting the renumbering wrong gives a 404
with no configuration error and nothing in the logs.

In the opposite direction, converting the ten files to YAML would cost more than it returns:
they are 243 lines of configuration and **501 of comment**, which is two lines of explanation
per setting, to be carried through an indentation-sensitive format — and a configuration
mistake does not fail the build, it shows up at startup or not at all.

> **Never both formats in the same folder.** Spring would load both files and `.properties`
> would win: whoever had just written the `.yml` would see it ignored with no signal at all,
> and would look for the defect in the code instead of in the file next door. CI has a step
> that refuses to continue if it finds such a pair.

### Production

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

`docker-compose.yml`'s three application containers connect out to the developer's own local
PostgreSQL (see [With Docker](#with-docker)) — a convenience with no meaning in a deployment,
since there is no developer's machine to reach. So on top of undoing `docker-compose.yml`'s
development conveniences, `docker-compose.prod.yml` is also where the three PostgreSQL
containers themselves are defined — fresh, not as an override, because the base file no longer
has any to override:

1. **it puts the profile back** to `prod` on all five containers — no SQL, no DEBUG, no detail
   in the error responses, no Swagger;
2. **it takes the fallback values away from the secrets**, described below;
3. **it unpublishes the broker** with `ports: !reset []`, which needs Compose 2.24 or newer —
   compose merges `ports` by appending, so an ordinary override could only add to them. The
   three databases need no such trick: being new to this file, they simply carry no `ports` at
   all.

On the second: the secrets that have a fallback in development — `RABBITMQ_USER`,
`RABBITMQ_PASSWORD`, `CORS_ALLOWED_ORIGINS` — become **mandatory** there,
`${RABBITMQ_USER:-guest}` becoming `${RABBITMQ_USER:?...}`. `DB_PASSWORD` has no development
fallback to take away in the first place: `docker-compose.yml` does not reference it at all any
more, so it is a production-only variable, `${DB_PASSWORD:?...}` from the start — the password
of the three containers this file creates.
The fallback in `docker-compose.yml` is there on purpose, because in development
`docker compose up` has to work with nothing prepared; the problem is **how that convenience
travels elsewhere**, which is silently. No warning, no error, just a broker reachable with the
password `guest`.

With the override, a missing variable stops compose before anything starts and says which one
is missing. Checked in all three states: without the variables it refuses, without the
override the same incomplete `.env` starts anyway (which is right, that is development), and
with both it is valid.

`JWT_SECRET` does not appear in that file because it is **already** mandatory everywhere: a
signing secret with a convenience value makes no sense even in development.

> The file holds no restart policies, memory limits or replicas. Those are decisions that
> depend on where you deploy, and writing them here would mean inventing them before knowing
> whether they are needed.

```bash
mvn clean package
export CORS_ALLOWED_ORIGINS="https://your-frontend.example.com"
java -jar services/booking-service/target/booking-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Recognised variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `BOOKING_PORT`.
The logs go to stdout: in a container Docker collects them (`docker compose logs`). Writing to
a file stays in the `dev` profile alone. The password comes from
`SPRING_DATASOURCE_PASSWORD`: an environment variable in the containers, read from `.env`
outside them.

`CORS_ALLOWED_ORIGINS` **has no default**: if it is unset the startup fails, instead of
publishing localhost origins to production. In `prod` Swagger is disabled, because the API
schema is served on public paths.

---

## How the packages are organised

Every service has a namespace of its own, and `shared` keeps the root:

| Module | Package |
|---|---|
| `shared` | `com.classroom.{dto,model,security,config,util,exception,events}` |
| `booking-service` | `com.classroom.booking.*` |
| `auth-service` | `com.classroom.auth.*` |
| `notification-service` | `com.classroom.notification.*` |
| `gateway` | `com.classroom.gateway.*` |

This is not an aesthetic convention. While the booking service sat under `com.classroom.*`
like `shared`, three packages were published by both jars and the boundary between the two
modules was not checked by the compiler: a class could use a package-private member of the
other module and still compile. Separating the namespaces made it happen for real —
`BookingAuthorizationService` was using `AppPrincipal` with no import, and now has to declare
it.

The practical consequence: every service declares an explicit `@ComponentScan` that includes
`shared`'s packages. Without it, the shared beans (the JWT filter, the security
configuration, the error handler) would fall outside the scan and the service would start
with no authentication at all.

## The path of a request

From the browser to the database row, with the points where something can stop it:

```
  browser
     |  POST /api/bookings      Authorization: Bearer <token>
     v
  gateway :17102 ------------------------------------------------ the only published port
     |  1. EdgeCorrelationFilter mints X-Request-Id (or reuses the one it received)
     |  2. picks the route by path prefix
     |     -> no route matches ................................. 404
     |     -> the service does not answer ...................... 503
     v
  booking-service :17103 ------------------------- not reachable from outside
     |  3. RequestCorrelationFilter puts X-Request-Id back into the MDC
     |  4. JwtAuthFilter verifies the token's signature, on its own
     |     -> token absent, expired or forged .................. 401
     |  5. SecurityConfig checks the role
     |     -> insufficient role ................................ 403
     |  6. Bean Validation on the body
     |     -> missing field or out of range .................... 400
     v
  controller -> service -> repository -> PostgreSQL
     |     -> the room is already taken ........................ 409
     |     -> a database constraint said no .................... 409
     v
  response: always the same JSON envelope, with the same X-Request-Id
```

**The gateway does not validate tokens.** It has no security configuration at all: it routes,
and nothing else. Each service verifies for itself, and that is a choice — a gateway that
authenticates becomes the point everything passes through and everything stops at, and the
services behind it end up trusting it without checking, left defenceless the day somebody
reaches them another way.

**The request id crosses everything.** It is born at the gateway, travels in the
`X-Request-Id` header, ends up in the MDC inside every service, and is sent back both in the
response header and in the body's `sessionId` field. It crosses the events on RabbitMQ too,
carried as a message header: it is the only key that lets you reconstruct an operation
touching three services, three databases and two different threads.

> That sentence used to have an exception nobody had noticed. The two handlers that answer
> **401 and 403** run inside the security filter chain, before any controller, and each minted
> an `AUTH_xxxxxxxx` of its own: a refused request came back with one id in the header and a
> different one in `sessionId`. Measured — header `REQ_852A1225`, body `AUTH_98C52C23` — which
> is the exact failure the id exists to prevent, and the worst way for a diagnostic tool to
> break, because it looks like it is working. They now read the id off the request they are
> handed (`RequestCorrelationFilter.current(request)`, rather than the no-argument version,
> because `RequestContextHolder` is not guaranteed to be populated that early).

## Tokens and authentication

**Only `auth-service` issues them.** It is the only module with `jjwt-impl` among its compile
dependencies: the others have `jjwt-api` alone and can verify, not sign. The boundary is
enforced by the classpath, not by a written rule.

**Every service verifies them on its own**, calling nobody. That is possible because the
signature is HMAC with a shared secret — `JWT_SECRET`, the same for all of them — and the
token carries everything needed to decide:

| Claim | What it is for |
|---|---|
| `sub` | the email of whoever logged in |
| `id` | the numeric id, used as the owner of bookings and notifications |
| `name`, `username` | denormalised into the bookings, so showing them needs no call to the user service |
| `role` | `admin` or `user`, from which Spring builds the authority `@PreAuthorize` looks for |

The price of this choice is that **a token cannot be revoked**: it lasts an hour and stays
valid until it expires. Deleting a user does not log them out. It is the flip side of offline
validation, and it is a conscious one — "I deleted the user" and "the user can no longer do
anything" are two different statements today.

`JWT_SECRET` has to be **identical** in every service, or whoever does not share it refuses
every token with a 401. Changing it invalidates all the tokens already issued.

## Error handling

**One envelope, for every response.** Success or error, the shape does not change: the reader
does not have to guess which schema they received.

```json
{
  "success": false,
  "error": "BOOKING_CONFLICT",
  "message": "Room 3 busy from 2026-09-10T09:00 to 2026-09-10T11:00",
  "userMessage": "L'aula non e' disponibile nel periodo richiesto.",
  "data": null,
  "timestamp": "2026-09-10 08:14:22",
  "sessionId": "REQ_A42118C7"
}
```

`message` is for whoever develops, `userMessage` for whoever uses: keeping them apart avoids
having to choose between a message useless to the investigator and one incomprehensible to
the person reading the screen. `sessionId` is the request id, so a report can quote it and the
logs of all three services are found by searching for that string.

**The controllers do not translate errors.** They throw a domain exception and
`GlobalExceptionHandler` — one of them, in `shared`, shared by every service — decides the
status once:

| Exception | Status | When |
|---|---|---|
| `InvalidRequestException` | 400 | the request asks for something that makes no sense |
| `MethodArgumentNotValidException` | 400 | Bean Validation rejected the body |
| `HandlerMethodValidationException` | 400 | a constraint on a parameter said no (`@Positive` on a path variable) |
| `AuthenticationFailedException` | 401 | a login with the wrong credentials |
| `TooManyRequestsException` | 429 | refused for asking too often, and carries the `Retry-After` delay |
| `AccessDeniedException` | 403 | authenticated, but not theirs and not an admin |
| `ResourceNotFoundException` | 404 | the object named does not exist |
| `DomainConflictException` | 409 | it exists, but its state does not admit the operation |
| `BookingConflictException` | 409 | overlapping bookings |
| `OptimisticLockingFailureException` | 409 | somebody else changed the same row first |
| `DataIntegrityViolationException` | 409 | a database constraint said no |
| anything else | 500 | unexpected, with the stack trace in the logs |

**A malformed request is the framework's business, not the domain's.** `GlobalExceptionHandler`
extends Spring's `ResponseEntityExceptionHandler`, which already knows the right status for
the twenty-odd exceptions Spring MVC raises when a request does not honour the protocol; the
only thing overridden is the body, so those answers arrive in the same envelope as every
other. `ProtocolError` is the catalogue that supplies the code and the Italian sentence, keyed
by status:

| Case | Status | Header it also sets |
|---|---|---|
| body that is not JSON, or a field of the wrong type | 400 | |
| path variable of the wrong type (`/api/rooms/abc`) | 400 | |
| missing query parameter | 400 | |
| method that does not exist on that path | 405 | `Allow` |
| `Content-Type` nobody reads | 415 | `Accept` |
| `Accept` the service cannot satisfy | 406 | *(no body: see below)* |
| path with nothing mapped to it | 404 | |

> This was not so until recently, and the way it failed is worth keeping written down.
> `@ExceptionHandler(Exception.class)` is consulted **before** Spring's own
> `DefaultHandlerExceptionResolver`, so it was catching all of those first and answering
> **500 INTERNAL_ERROR** to every one — each logged at ERROR with a stack trace, in a project
> whose rule is that an ERROR in production is a fact and not noise. Two cases of the family
> had already been found and patched one at a time (`NoResourceFoundException`,
> `NoHandlerFoundException`); inheriting covers the rest, including any Spring adds later.
>
> The consequence to remember when adding a handler: an `@ExceptionHandler` for a type the
> base class already maps is not an override, it is an **ambiguity**, and the context refuses
> to start. Validation and the two 404s are therefore written as `@Override` methods.

The **406 is the one answer with no body**, and that is deliberate: a client that accepts
nothing the service can produce cannot be sent the envelope either. Returning it anyway is
what used to make this case fail twice — the second failure escaped to the container's error
dispatch, which re-enters the security chain on `/error` unauthenticated, and the caller
received a **401 telling it to log in** instead of a 406.

**Three responses also carry the header that makes them actionable.** A status alone tells a
client what happened; these tell it what to do next, and without them the only guidance is a
sentence in Italian meant for a person:

| Response | Header | Why |
|---|---|---|
| 401 (protected resource) | `WWW-Authenticate: Bearer realm="classroom"` | mandatory per RFC 9110 §11.6.1. When a token *was* sent and refused it becomes `error="invalid_token"` (RFC 6750 §3.1): without a token you log in, with an expired one you refresh — opposite moves, and previously indistinguishable. This is also the case the README warns about below, where changing `JWT_SECRET` starts refusing every existing token "for no visible reason" |
| 429 | `Retry-After` | `LoginAttemptLimiter` is the only thing that knows when the window reopens, so it is the only thing that can say — the delay travels on `TooManyRequestsException`. Advisory: it is read separately from the check, so the window can roll over in between |
| 503 | `Retry-After` | at the gateway. A constant, and it has to be — nothing knows when a service that is not answering will be back |

The **401 of a failed login carries no challenge**, and that is deliberate rather than an
oversight: `/api/auth/login` does not use HTTP authentication, it reads a JSON body, so a
`Bearer` challenge would tell the caller to do the one thing that cannot help. A misleading
challenge is worse than an absent one. The two 401s are different cases, and only the one
above — a protected resource refusing a request — has something to challenge with.

**Editing a room or a booking is version-checked.** Both are read-modify-write across two
transactions — load, change some fields, save — so two people editing the same row both used
to succeed, and the later write silently replaced the earlier one: no error, no trace, the
first editor's change simply gone. `@Version` on `Room` and `Booking` (migration `V9`) turns
that into a **409** for whoever arrives second, with `CONCURRENT_MODIFICATION` and an invitation
to reload and retry.

> `room.status` is refreshed through `RoomRepository.updateStatus`, a targeted column update
> that deliberately **bypasses** the version. It is a cache of what the bookings say, not
> anybody's edit: going through `save()` would bump the version and make two people booking
> *different* slots in the same room at the same moment collide, so one would lose a perfectly
> valid booking to a 409 raised by bookkeeping neither of them asked for.

The distinction between 500 and 503 is not formal: they suggest two different actions. A 500
says "something is broken", a 503 says "try again". `ServiceUnavailableException` still
carries that meaning in `shared` for whichever future case needs it, but nothing throws it
today: deleting a user used to be the one case where the caller had to know a downstream
call had failed, and that call is gone — see "Communication between services" below.

`IllegalArgumentException` is deliberately **not** mapped to 400: it signals a programming
error, not a bad request, and turning it into a 400 would hide defects behind a response that
looks normal.

**A rule about a parameter lives on the parameter.** `@Positive` on a path variable, with the
sentence to show written on the annotation — not an `if` at the top of the method. That rule
used to be written the other way, and it is worth saying how it went: `id == null || id <= 0`
appeared **eight times** across three controllers, in **three different Italian wordings** of
the identical sentence, and whoever met two of them could not tell whether the difference
meant anything. The `id == null` half was unreachable into the bargain — Spring never passes
null for a required path variable, and a non-numeric segment was producing the 500 described
above, not a null.

> Both halves of Bean Validation answer with the same code, `VALIDATION_ERROR`: from the
> caller's side a rejected body and a rejected parameter are one thing, and the reason is in
> `userMessage` either way. The wordings live in `ValidationMessages` (booking-service) and
> beside the controller that uses them (auth-service), because an annotation attribute has to
> be a compile-time constant and an enum's fields are not one — which is why they are not in
> `ResourceType` with the rest of that service's wordings.
>
> **Do not annotate those controllers `@Validated`.** With it, Spring runs AOP-based
> validation instead and throws `ConstraintViolationException`, which nothing maps — the 400
> would go back to being a 500.

**The gateway has its own handler**, because it is WebFlux and does not share the services'.
It produces the same envelope — a test keeps the two shapes aligned — and tells an unreachable
service (503) apart from a path with no route (404).

## Logging

Configured in `shared/src/main/resources/logback-spring.xml`, inherited by the three services
that depend on `shared`. The gateway has a copy of its own, because it cannot depend on
`shared` (which brings Tomcat, and Tomcat has no place in WebFlux).

```
2026-09-05 17:45:30.369  WARN  REQ_A1B2C3D4 BookingService        : room 3 is not available ...
2026-09-05 17:45:30.372  INFO  -            FirstAdminBootstrap   : first administrator created
```

The id column comes from `%X{requestId}`, which Logback reads from the MDC: **every line
carries it**, including Spring's, Hibernate's and Flyway's, without anybody having to pass it
around. The dash marks the lines born outside a request — startup, message consumption,
scheduled jobs.

It was not always so: `RequestCorrelationFilter` had always been putting the id into the MDC,
but without a pattern to print it, it appeared nowhere. 127 calls out of 322 were passing it
by hand inside the message; the other 195 had no way of being traced back to a request. Now
the pattern puts it on all of them, and the 127 manual prefixes are gone.

### Which level for which case

There is one rule: **the level says who has to do something**, not how serious it is.

| Level | The case | Who has to act | In production |
|---|---|---|---|
| `DEBUG` | the narration of a request, step by step | nobody: it is for whoever is watching now | **off** |
| `INFO` | something changed and it still matters tomorrow: a user created, a login succeeded, a booking cancelled | nobody now, maybe somebody later | on |
| `WARN` | the request was **refused** and the refusal says something: 400, 401, 403, 409, 429, 503 | nobody on a single line; many identical lines are a signal | on |
| `ERROR` | **nobody knows what happened**: the 500 from `handleGeneric` | somebody, and now. Always with the stack trace | on |

The practical consequence: **an `ERROR` in the production logs is a fact, not noise.** In the
code they are 14 calls out of 322. If `ERROR` also covered the expected refusals, finding the
real failures would mean filtering them out — and then you may as well not have it.

`TRACE` is not used: when that much detail is needed, what is needed is a debugger.

### Where they go

To **stdout**, always. In a container Docker collects them (`docker compose logs`); writing
them to a file inside the image would mean producing them where nobody reads them and nobody
rotates them.

The one exception is a development run **outside** a container, which adds an appender on
`logs/application.log` with rotation at 10 MB, 30 days and 500 MB in total. It sits inside
`<springProfile name="dev &amp; !docker">`, next to the file it applies to.

The `!docker` half is what lets `docker-compose.yml` use the `dev` profile at all. The path is
relative, so inside the image it resolves somewhere unwritable and the service dies at
startup — which is why compose used to avoid the profile entirely and flip individual flags
instead. The condition is stated twice, as `dev &amp; !docker` on the file appender and
`!dev | docker` on the console-only root, because logback needs the two to cover every case
between them: exactly one `<root>` has to apply, whichever profiles are active.

## Communication between services

**Every cross-service coupling today is a RabbitMQ event.** That was not always true, and
saying so is worth more than a passing note: deleting a user used to be the one place a
service called another synchronously, over REST, and waited for the answer. It no longer is
— there is currently nothing in this system that needs the synchronous, know-the-outcome
kind of call `ServiceUnavailableException` exists for. If that changes, the table above is
where its next throw site would show up.

**A booking cancelled by an admin.** It used to be a REST call from `booking-service` to
`notification-service` and was lost if the second was down. Now it waits on a queue.

**A user deleted by an admin.** It used to be two REST calls from `auth-service`, made
synchronously and retried three times each, and the user was deleted **only if both
succeeded** — the guarantee a foreign key used to give for free. That guarantee is gone: now
`auth-service` deletes the user immediately and publishes a `UserDeletedEvent`;
`booking-service` and `notification-service` each remove their own rows independently,
whenever they get to the message.

Both events share the same shape of trade-off. The dependency moves from the service to the
broker — the window narrows, it does not close: if the broker is unreachable the message is
lost all the same, and the failure stays logged and unpropagated, because the action that
matters (the cancellation, the deletion) has already happened and failing the response would
not undo it. For the user-deletion event specifically, that means a broker outage at the
wrong moment leaves orphan bookings and notifications with nobody left to clean them up —
the same risk the old synchronous calls carried when a downstream service, rather than the
broker, was unreachable, just moved to a different failure point.

## The first administrator

On an empty users database `/api/admin/users` is unreachable: it already requires a token
with the `ADMIN` role. That is not a consequence of the split — the monolith had the same
constraint — but on new databases you meet it immediately, and the only way out used to be a
hand-written `INSERT` with a BCrypt hash computed elsewhere.

Two variables in `.env` are now enough:

```bash
BOOTSTRAP_ADMIN_EMAIL=your@email.example
BOOTSTRAP_ADMIN_PASSWORD=aLongPassword
```

At `auth-service` startup, **and only if the users table is empty**, an administrator is
created with those credentials. They should then be emptied, along with changing the password.

The condition is deliberately tight: on a non-empty table the mechanism is **inert** — it
promotes nobody, updates nobody, touches no existing user. That is what separates a help at
startup from a shortcut to administrator privileges, and it is held still by the tests in
`FirstAdminBootstrapUnitTest`. Creation goes through `AuthService.register`, the same road as
every other user, so the password passes the same `PasswordEncoder`.

If the database is empty and the variables are absent, the service still starts but **logs at
`WARN`** how to proceed: an empty, silent database is exactly how this problem comes back.

See [FirstAdminBootstrap.java](services/auth-service/src/main/java/com/classroom/auth/FirstAdminBootstrap.java).

## The database schema

Managed by **Flyway**, under each service's `src/main/resources/db/migration/`. `ddl-auto` is
`validate`: Hibernate never changes the schema, it only checks that the entities match and
fails at startup if they have drifted.

To change the schema you add a migration (`V9__description.sql`). The ones already applied are
never modified again: Flyway checksums them, and it also stores the description derived from
the filename, so renaming a file counts as modifying it.

---

## Tests

```bash
mvn test      # the suite across every module. Works WITHOUT Docker.
mvn verify    # adds the coverage gate. REQUIRES Docker.
```

**Without Docker, use `mvn test`.** The Testcontainers classes skip themselves (`Skipped: 20`,
reported by Maven as a warning so it stays visible) and the rest runs normally: that is the
command of the development cycle.

`mvn verify` requires Docker, and that is not an oversight. The gate certifies that the code
has been tested, and it cannot certify what it could not execute: with
`CancellationMessagingTest` skipped, notification-service drops to **0.67** against a
threshold of 0.80, because that class is the only thing exercising the AMQP topology.
Lowering the threshold would make the gate a formality.

> Maven's message in that case says only `Coverage checks have not been met`, **without
> naming Docker**. If you meet it, that is almost always the cause.

The coverage reports end up in each module's `target/site/jacoco/index.html`: the 80% gate is
applied to every module separately, because the denominator changes from module to module.

The suite is unit tests without Spring, HTTP integration tests on H2, and **three** classes
against real services in containers:

| Class | What it checks that cannot be checked otherwise |
|---|---|
| `PostgresSchemaConstraintsTest` | the anti-overlap constraint `EXCLUDE USING gist`, which does not exist in H2 |
| `UserConstraintsTest` | the `CHECK` on the role, which H2 applies differently — on H2 a test would pass **even with the constraint absent** |
| `CancellationMessagingTest` | the whole AMQP topology: exchange, routing key, binding, converter, listener. Calling the consumer's method would prove the method, not that the message arrives |

All three have `disabledWithoutDocker = true`, so without Docker they skip rather than fail
the build.

**The skip is harmless locally and impossible in CI**, and that distinction is deliberate: the
guard step in `.github/workflows/ci.yml` looks for the `@Testcontainers` classes itself and
fails if a report says `skipped` is not zero, if it is missing, or if it contains no tests.
Checked by actually switching Docker off: the three classes produce reports with `skipped` 3,
4 and 13, and the guard names all three.

> This is not a theoretical precaution. Before that guard existed, **four failed assertions
> stayed hidden for days** behind a class that skipped itself in silence.

On the first run with Docker up, the network is needed to pull the images:

```bash
docker pull postgres:16-alpine
docker pull rabbitmq:3.13-management-alpine
docker pull testcontainers/ryuk:0.7.0
```
