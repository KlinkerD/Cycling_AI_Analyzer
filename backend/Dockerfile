# Multi-Stage Build für den analysis-service (Spring Boot Backend)
# ARM64-kompatibel (Raspberry Pi 4/5)
# Build-Kontext: Repo-Root (docker compose setzt das), weil cycling-messaging mitgebaut wird.

FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY cycling-messaging ./cycling-messaging
COPY intervals-api ./intervals-api
COPY intervals-service/pom.xml ./intervals-service/pom.xml
COPY backend ./backend
COPY ai-service/pom.xml ./ai-service/pom.xml
COPY report-service/pom.xml ./report-service/pom.xml
COPY delivery-service/pom.xml ./delivery-service/pom.xml
COPY fitness-service/pom.xml ./fitness-service/pom.xml

# -am baut cycling-messaging mit; der Cache-Mount spart das erneute Laden der Dependencies.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl backend -am package -DskipTests

# Runtime Image
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN mkdir -p /app/data

COPY --from=builder /app/backend/target/cycling-analysis-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
