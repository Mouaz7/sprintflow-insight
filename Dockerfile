# Base images are pinned by digest; Dependabot (docker ecosystem) proposes updates.
FROM node:24-bookworm-slim@sha256:dc26ae92c9fb15cf6b1d63e20f0944b2efe0f115b20e00be48fb23fceb061c54 AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk-jammy@sha256:5ba254809ef542504d40d47271c7d6c4f240fe40ca1386259806d431f13b6b4f AS backend
WORKDIR /build/backend
COPY backend/pom.xml backend/mvnw ./
COPY backend/.mvn ./.mvn
RUN chmod +x ./mvnw
RUN ./mvnw -B dependency:go-offline
COPY docker/HealthCheck.java /build/health/HealthCheck.java
RUN javac --release 25 -d /build/health/classes /build/health/HealthCheck.java
COPY backend/src ./src
COPY docs/openapi.yaml /build/docs/openapi.yaml
COPY --from=frontend /build/frontend/dist /build/frontend/dist
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre-jammy@sha256:a4e9fb35b48412cafbf036dabe201f22ee5e96f789e975bd6866ba0cbec45d17 AS runtime
RUN groupadd --gid 1000 sprintflow && useradd --uid 1000 --gid sprintflow --no-create-home sprintflow
WORKDIR /app
COPY --from=backend /build/backend/target/*.jar /app/app.jar
COPY --from=backend /build/health/classes /app/health
RUN mkdir /app/data && chown sprintflow:sprintflow /app/data
USER 1000:1000
ENV SERVER_ADDRESS=0.0.0.0 PORT=8081
EXPOSE 8081
# No curl or wget in the image: a tiny JDK HTTP client class probes actuator health.
# Orchestrators should use their own HTTP probes against /actuator/health instead.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
  CMD ["java", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Xmx16m", "-cp", "/app/health", "HealthCheck"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
