# Multi-Stage Build für den recommendation-service (Tagesempfehlung per REST, morgens an den delivery-service)
# ARM64-kompatibel (Raspberry Pi 4/5)
# Build-Kontext: Repo-Root (docker compose setzt das), weil cycling-messaging, intervals-api und cycling-web mitgebaut werden.

FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY cycling-messaging ./cycling-messaging
COPY intervals-api ./intervals-api
COPY intervals-service/pom.xml ./intervals-service/pom.xml
COPY report-service/pom.xml ./report-service/pom.xml
COPY delivery-service/pom.xml ./delivery-service/pom.xml
COPY fitness-service/pom.xml ./fitness-service/pom.xml
COPY recommendation-service ./recommendation-service
COPY cycling-web ./cycling-web
COPY ai-service/pom.xml ./ai-service/pom.xml

RUN --mount=type=cache,target=/root/.m2     mvn -q -B -pl recommendation-service -am package -DskipTests

# Runtime Image
FROM eclipse-temurin:25-jre
WORKDIR /app

# curl fuer den Docker-Healthcheck (/actuator/health)
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/recommendation-service/target/recommendation-service-*.jar app.jar

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "app.jar"]
