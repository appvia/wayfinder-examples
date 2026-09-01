# syntax=docker/dockerfile:1

# ---- Build -----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /src

# Dependencies first, so a source-only change reuses the cached layer.
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src

# VERSION is passed by CI: the git tag for a release, the short SHA otherwise.
ARG VERSION=dev
RUN mvn --batch-mode --no-transfer-progress package -DskipTests \
    && mv target/*.jar /app.jar

# ---- Run -------------------------------------------------------------------
# JRE rather than JDK: nothing here compiles, and the toolchain is a large
# attack surface to carry into production for no benefit.
FROM eclipse-temurin:21-jre

ARG VERSION=dev
ENV RELEASE=${VERSION}

RUN useradd --create-home --uid 10001 app
WORKDIR /app

COPY --from=build --chown=app:app /app.jar ./app.jar

USER app
EXPOSE ${{ .Inputs.port }}

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
