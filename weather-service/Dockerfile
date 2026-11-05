# Multi-Stage Build für den weather-service (Wetter zu Ort und Zeit von Open-Meteo, per REST)
# ARM64-kompatibel (Raspberry Pi 4/5)
# Build-Kontext: Repo-Root (docker compose setzt das), weil weather-api mitgebaut wird.

FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY cycling-types ./cycling-types
COPY cycling-messaging/pom.xml ./cycling-messaging/pom.xml
COPY intervals-api/pom.xml ./intervals-api/pom.xml
COPY weather-api ./weather-api
COPY cycling-web/pom.xml ./cycling-web/pom.xml
COPY ai-service/pom.xml ./ai-service/pom.xml
COPY report-service/pom.xml ./report-service/pom.xml
COPY delivery-service/pom.xml ./delivery-service/pom.xml
COPY intervals-service/pom.xml ./intervals-service/pom.xml
COPY training-service/pom.xml ./training-service/pom.xml
COPY ftp-service/pom.xml ./ftp-service/pom.xml
COPY weather-service ./weather-service

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl weather-service -am package -DskipTests

# Runtime Image
FROM eclipse-temurin:25-jre
WORKDIR /app

# curl fuer den Docker-Healthcheck (/actuator/health)
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/weather-service/target/weather-service-*.jar app.jar

ENV SERVER_PORT=8080
EXPOSE 8080

# Klein halten: der Service macht nur REST rein und HTTP zu Open-Meteo.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "app.jar"]
