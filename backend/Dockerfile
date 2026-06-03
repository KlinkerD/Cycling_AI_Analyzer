# Multi-Stage Build für Spring Boot Backend
# ARM64-kompatibel (Raspberry Pi 4/5)

FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Maven Wrapper oder Maven installieren
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# Runtime Image
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN mkdir -p /app/data

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

