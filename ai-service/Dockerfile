# Multi-Stage Build für den ai-service (Gemini über MQTT)
# ARM64-kompatibel (Raspberry Pi 4/5)
# Build-Kontext: Repo-Root (docker compose setzt das), weil cycling-messaging mitgebaut wird.

FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY cycling-messaging ./cycling-messaging
COPY intervals-api/pom.xml ./intervals-api/pom.xml
COPY intervals-service/pom.xml ./intervals-service/pom.xml
COPY ai-service ./ai-service
COPY cycling-web/pom.xml ./cycling-web/pom.xml
COPY report-service/pom.xml ./report-service/pom.xml
COPY delivery-service/pom.xml ./delivery-service/pom.xml
COPY training-service/pom.xml ./training-service/pom.xml

COPY ftp-service/pom.xml ./ftp-service/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl ai-service -am package -DskipTests

# Runtime Image
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=builder /app/ai-service/target/ai-service-*.jar app.jar

# Klein halten: der Service macht nur MQTT + HTTP-Aufrufe zu Gemini.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "app.jar"]
