# 🚴 Cycling Analysis – KI-gestützte Workout-Auswertung

Analysiere deine Radtrainings aus **intervals.icu** mit **Google Gemini AI** und erhalte strukturierte Reports mit Diagrammen.

## Tech-Stack

| Schicht | Technologie |
|---|---|
| Backend | Spring Boot 3.4 + Java 25 Runtime (kompiliert auf Java-21-Bytecode) |
| Frontend | Angular 17 + Angular Material |
| KI | Google Gemini 1.5 Flash |
| Datenbank | H2 (persistent, File-basiert) |
| Deployment | Docker Compose |

---

## 🔑 Voraussetzungen & API-Setup

### 1. intervals.icu API-Key

1. Auf [intervals.icu](https://intervals.icu) einloggen
2. **Einstellungen** → **API** → **API Key generieren**
3. Deine **Athlete-ID** steht in der URL: `intervals.icu/athlete/`**`i12345`**`/calendar`

### 2. Google Gemini API-Key

1. Auf [Google AI Studio](https://aistudio.google.com/app/apikey) gehen
2. **"Create API Key"** klicken
3. Kostenloses Kontingent: ca. 1.500 Anfragen/Tag mit Gemini 1.5 Flash

### 3. Konfiguration

#### Lokal (Entwicklung)
Datei `backend/src/main/resources/application.properties` anpassen:
```properties
intervals.api.key=dein-api-key
intervals.athlete.id=i12345
gemini.api.key=dein-gemini-key
cycling.ftp=250        # Dein FTP in Watt
cycling.max-hr=190     # Deine maximale Herzfrequenz
```

#### Docker / Raspberry Pi
```bash
cp .env.example .env
# .env mit deinen Werten befüllen
nano .env
```

---

## 🚀 Starten

### Lokal (Entwicklung)

**Backend:**
```bash
cd backend
mvn spring-boot:run
# Läuft auf http://localhost:8080
# H2 Console: http://localhost:8080/h2-console
```

> Hinweis: Das Backend läuft problemlos mit einer Java-25-Runtime, wird aber absichtlich auf Java-21-Bytecode kompiliert, weil Spring Boot 3.4 / Spring Framework 6.2 Java-25-Klassen beim Scannen noch nicht vollständig unterstützen.

**Frontend:**
```bash
cd frontend
npm install
npm start
# Öffnet http://localhost:4200
```

### Docker (Produktion / Raspberry Pi)

```bash
# Einmalig .env anlegen
cp .env.example .env
nano .env  # Werte eintragen

# Starten
docker-compose up -d --build

# App öffnen
open http://localhost
```

**Raspberry Pi (ARM64):** Die Docker-Images basieren auf `eclipse-temurin` und `nginx:alpine`, die ARM64 unterstützen.

---

## 🤖 GitHub Actions + Docker Images für Raspberry Pi

Die Repository-Pipeline baut jetzt automatisch beide Images (`backend` und `frontend`) und veröffentlicht sie in der GitHub Container Registry (`ghcr.io`).

### Was passiert automatisch?
- **Push auf `main`** → Images werden gebaut und gepusht
- **Pull Request** → Images werden nur gebaut, nicht gepusht
- **Plattformen** → `linux/amd64` und `linux/arm64`
- **Raspberry Pi** → kann direkt das `arm64`-Image ziehen

### Image-Namen
Je nach GitHub-Account heißen die Images typischerweise so:
```bash
ghcr.io/<dein-github-username>/cycling-backend:latest
ghcr.io/<dein-github-username>/cycling-frontend:latest
```

### Auf dem Raspberry Pi nutzen
```bash
# Login nur nötig, wenn das Paket privat ist
docker login ghcr.io

# Backend und Frontend ziehen
docker pull ghcr.io/<dein-github-username>/cycling-backend:latest
docker pull ghcr.io/<dein-github-username>/cycling-frontend:latest

# Dann starten, z. B. mit docker compose
# docker-compose.yml kann auf image: ... umgestellt werden,
# oder du nutzt die Images direkt in einem eigenen Compose-File.
```

### GitHub Secrets / Berechtigungen
Für öffentliche Repos reicht meist `GITHUB_TOKEN` in der Action.
Für private Repositories musst du auf dem Raspberry Pi per `docker login ghcr.io` anmelden oder ein PAT mit `read:packages` verwenden.

---

## 📊 Funktionen

### Aktivitätsauswahl
- Zeitraum wählen (Datepicker)
- Nur Rad-Aktivitäten werden angezeigt
- Schnellstatistiken pro Aktivität

### KI-Report (Gemini)
- 🏆 **Score** (1–10)
- 💪 **Was gut lief** – konkrete Stärken
- ⚠️ **Auffälligkeiten** – ungewöhnliche Muster
- 📈 **Fortschritt** – Vergleich mit letzten 3 Trainings
- 📝 **Fazit** – prägnante Zusammenfassung

### Diagramme
| Chart | Beschreibung |
|---|---|
| Leistung vs. Distanz | Watt-Kurve über die Strecke |
| Herzfrequenz vs. Distanz | HF-Verlauf über die Strecke |
| Leistungszonen | FTP-basierte Zonen Z1–Z7 |
| Herzfrequenzzonen | Max-HR-basierte Zonen Z1–Z5 |
| Trainingsfortschritt | Durchschnittsleistung & Score der letzten Trainings |

---

## ⚠️ Hinweis zur intervals.icu Streams API

Die Streams-API (`GET /api/v1/activity/{id}/streams`) gibt die Zeitreihendaten zurück.
Das genaue Antwortformat bitte in der [intervals.icu API-Dokumentation](https://intervals.icu/api/v1/docs) prüfen.
Der `IntervalsService.java` ist so gebaut, dass er beide üblichen Formate (Array of `{type, data}` oder direktes Objekt) automatisch erkennt.

Falls nötig, die Methode `parseStreams()` in `IntervalsService.java` anpassen.

---

## Projektstruktur

```
cycling/
├── backend/                    # Spring Boot Backend
│   ├── src/main/java/com/cycling/
│   │   ├── controller/         # REST API Endpoints
│   │   ├── service/            # Business Logic + AI
│   │   ├── model/              # JPA Entities
│   │   ├── repository/         # H2 Datenbank
│   │   └── dto/                # Data Transfer Objects
│   └── src/main/resources/
│       └── application.properties
├── frontend/                   # Angular 17 Frontend
│   └── src/app/
│       ├── pages/              # Activity List + Report
│       ├── services/           # API Service
│       └── models/             # TypeScript Interfaces
├── docker-compose.yml
└── .env.example
```
