# ============================================================================
# Un solo Dockerfile per tutti e quattro i servizi: cambia solo il modulo.
#
#     docker build --build-arg MODULO=auth-service -t prenotazioni/auth .
#
# Il build e' multi-stage perche' l'immagine finale non deve contenere Maven ne'
# i sorgenti: solo un JRE e il jar.
#
# NOTA: il primo stage copia TUTTI i pom prima dei sorgenti. Serve a far usare a
# Docker la cache: finche' le dipendenze non cambiano, "mvn dependency:go-offline"
# non viene rieseguito e una modifica al codice non riscarica mezzo repository.
# ============================================================================

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /sorgenti

COPY pom.xml .
COPY shared/pom.xml shared/
COPY services/auth-service/pom.xml services/auth-service/
COPY services/notifica-service/pom.xml services/notifica-service/
COPY services/prenotazione-service/pom.xml services/prenotazione-service/
COPY services/gateway/pom.xml services/gateway/
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY shared shared
COPY services services

ARG MODULO
# -am costruisce anche shared, da cui tutti dipendono. I test girano nella pipeline
# di CI, non qui: un'immagine non e' il posto dove scoprire che la suite fallisce.
RUN mvn -B -q package -pl services/${MODULO} -am -DskipTests

# Il nome del jar cambia da modulo a modulo, quindi si isola qui invece di
# ripeterlo nel comando di avvio.
RUN cp services/${MODULO}/target/*.jar /applicazione.jar

FROM eclipse-temurin:17-jre
WORKDIR /opt/prenotazioni

# Utente non privilegiato: un processo che non ha bisogno di root non deve averlo.
RUN useradd --system --create-home --shell /usr/sbin/nologin prenotazioni
USER prenotazioni

COPY --from=build /applicazione.jar applicazione.jar

ENTRYPOINT ["java", "-jar", "applicazione.jar"]
