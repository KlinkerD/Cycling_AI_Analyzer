# Multi-Stage Build für den report-delivery-service (Mail + Discord über MQTT)
# ARM64-kompatibel (Raspberry Pi 4/5)
# Build-Kontext: Repo-Root (docker compose setzt das), weil cycling-messaging mitgebaut wird.

FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY cycling-messaging ./cycling-messaging
COPY report-delivery-service ./report-delivery-service
COPY backend/pom.xml ./backend/pom.xml
COPY ai-service/pom.xml ./ai-service/pom.xml

RUN --mount=type=cache,target=/root/.m2     mvn -q -B -pl report-delivery-service -am package -DskipTests

# Runtime Image
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN mkdir -p /app/data

COPY --from=builder /app/report-delivery-service/target/report-delivery-service-*.jar app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "app.jar"]
