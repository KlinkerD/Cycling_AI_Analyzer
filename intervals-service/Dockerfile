# Multi-Stage Build für den intervals-service (einzige Anbindung an intervals.icu)
# ARM64-kompatibel (Raspberry Pi 4/5)
# Build-Kontext: Repo-Root (docker compose setzt das), weil cycling-messaging und intervals-api mitgebaut werden.

FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY cycling-types ./cycling-types
COPY cycling-messaging ./cycling-messaging
COPY intervals-api ./intervals-api
COPY intervals-service ./intervals-service
COPY cycling-web/pom.xml ./cycling-web/pom.xml
COPY ai-service/pom.xml ./ai-service/pom.xml
COPY report-service/pom.xml ./report-service/pom.xml
COPY delivery-service/pom.xml ./delivery-service/pom.xml
COPY training-service/pom.xml ./training-service/pom.xml

COPY ftp-service/pom.xml ./ftp-service/pom.xml
COPY weather-api/pom.xml ./weather-api/pom.xml
COPY weather-service/pom.xml ./weather-service/pom.xml
COPY stats-service/pom.xml ./stats-service/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl intervals-service -am package -DskipTests

# Runtime Image
FROM eclipse-temurin:25-jre
WORKDIR /app

# curl fuer den Docker-Healthcheck (/actuator/health)
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/intervals-service/target/intervals-service-*.jar app.jar

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "app.jar"]
