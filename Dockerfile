FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk-jammy AS backend
WORKDIR /build/backend
COPY backend/pom.xml backend/mvnw ./
COPY backend/.mvn ./.mvn
RUN chmod +x ./mvnw
RUN ./mvnw -B dependency:go-offline
COPY backend/src ./src
COPY docs/openapi.yaml /build/docs/openapi.yaml
COPY --from=frontend /build/frontend/dist /build/frontend/dist
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 1000 sprintflow && useradd --uid 1000 --gid sprintflow --no-create-home sprintflow
WORKDIR /app
COPY --from=backend /build/backend/target/*.jar /app/app.jar
RUN mkdir /app/data && chown sprintflow:sprintflow /app/data
USER 1000:1000
ENV SERVER_ADDRESS=0.0.0.0 PORT=8081
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 CMD curl -fsS http://127.0.0.1:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
