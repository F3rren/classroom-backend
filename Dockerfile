# ============================================================================
# One Dockerfile for all four services: only the module changes.
#
#     docker build --build-arg MODULE=auth-service -t classroom/auth .
#
# The build is multi-stage because the final image must contain neither Maven nor the
# sources: just a JRE and the jar.
#
# NOTE: the first stage copies ALL the poms before the sources. That is what lets Docker use
# its cache: as long as the dependencies do not change, "mvn dependency:go-offline" is not
# re-run and a code change does not re-download half a repository.
# ============================================================================

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /sorgenti

COPY pom.xml .
COPY shared/pom.xml shared/
COPY services/auth-service/pom.xml services/auth-service/
COPY services/notification-service/pom.xml services/notification-service/
COPY services/booking-service/pom.xml services/booking-service/
COPY services/gateway/pom.xml services/gateway/
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY shared shared
COPY services services

ARG MODULE
# -am builds shared too, which everything depends on. The tests run in the CI pipeline, not
# here: an image is not the place to discover that the suite fails.
RUN mvn -B -q package -pl services/${MODULE} -am -DskipTests

# The jar's name changes from module to module, so it is isolated here rather than repeated
# in the start command.
RUN cp services/${MODULE}/target/*.jar /application.jar

FROM eclipse-temurin:17-jre
WORKDIR /opt/classroom

# An unprivileged user: a process that does not need root must not have it.
RUN useradd --system --create-home --shell /usr/sbin/nologin classroom
USER classroom

COPY --from=build /application.jar application.jar

ENTRYPOINT ["java", "-jar", "application.jar"]
