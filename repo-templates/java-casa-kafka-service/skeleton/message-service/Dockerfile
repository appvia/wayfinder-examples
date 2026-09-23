# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
# Shown by /api/healthz.
ARG RELEASE=dev
COPY .mvn ./.mvn
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# The tests run in CI before the image is built, so they are not repeated here.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests -Drelease="${RELEASE}" package

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /src/target/message-service.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
