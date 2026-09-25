# Phase 3 — container image for the FirstIn dashboard.
#
# Render has no native Java runtime, so the Render service uses
# `runtime: docker` (see render.yaml). Multi-stage build: the build stage
# compiles the Spring Boot jar and the frontend-maven-plugin builds the React
# SPA into it; the runtime stage ships only the JRE and the jar. Still $0.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Manifests first for better layer caching.
COPY backend/pom.xml backend/mvnw ./backend/
COPY backend/.mvn ./backend/.mvn
COPY frontend/package.json frontend/package-lock.json ./frontend/

# Sources.
COPY backend/src ./backend/src
COPY frontend/index.html frontend/vite.config.ts frontend/tsconfig*.json ./frontend/
COPY frontend/src ./frontend/src

# Tests run in CI; the deploy build skips them.
WORKDIR /app/backend
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/target/firstin-dashboard-*.jar app.jar
EXPOSE 8080
# Render free tier: 512 MB RAM. Let the JVM use most of it instead of the
# container default (~1/4 of RAM as max heap).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
