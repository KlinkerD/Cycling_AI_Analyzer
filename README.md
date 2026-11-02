# 🚴 Cycling Analysis – KI-gestützte Workout-Auswertung

Analysiere deine Radtrainings aus **intervals.icu** mit **Google Gemini AI** und erhalte strukturierte Reports mit Diagrammen.

## Tech-Stack

| Schicht | Technologie |
|---|---|
| Backend | Spring Boot 4 + Java 25, aufgeteilt in analysis-service, ai-service, report-delivery-service |
| Messaging | MQTT (Eclipse Mosquitto, Paho-v5-Client) |
| API-Vertrag | OpenAPI (springdoc) → OpenAPI Generator (typescript-angular) |
| Frontend | Angular 17 + Angular Material |
| KI | Google Gemini (Standard: gemini-2.5-flash) |
| Datenbank | H2 (persistent, File-basiert), je eine für backend und report-delivery-service |
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

**MQTT-Broker** (die Services sprechen über Mosquitto miteinander):
```bash
docker compose up -d mosquitto
# Ist Port 1883 schon belegt, in docker-compose.yml einen anderen Host-Port eintragen
# und MQTT_BROKER_URL=tcp://localhost:<port> für die Services setzen.
```

**Backend, ai-service, report-delivery-service** (jeweils in eigenem Terminal):
```bash
mvn install -DskipTests                       # einmalig, baut auch cycling-messaging
mvn -pl backend spring-boot:run               # http://localhost:8080, Swagger-UI: /swagger-ui.html
mvn -pl ai-service spring-boot:run            # braucht GEMINI_API_KEY
mvn -pl report-delivery-service spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm start          # erzeugt vorher den API-Client, öffnet http://localhost:4200
```

### Docker (Produktion / Raspberry Pi)

```bash
# Einmalig .env anlegen
cp .env.example .env
nano .env  # Werte eintragen

# Starten
docker compose up -d --build

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

## 🔌 API-Client (OpenAPI)

Die Angular-Models und -Services für die REST-API werden generiert, nicht von Hand geschrieben:

```
Backend (springdoc)  →  frontend/openapi/cycling-api.yaml  →  OpenAPI Generator  →  frontend/src/app/api/
```

- Die Spec ist eingecheckt. Der Test `OpenApiSpecTest` im Backend schlägt fehl, sobald sie nicht mehr zur API passt.
  Nach einer API-Änderung aktualisieren mit:
  ```bash
  mvn -pl backend test -Dtest=OpenApiSpecTest -Dopenapi.update=true
  ```
- `npm start` / `npm run build` erzeugen `src/app/api/` automatisch (`npm run generate:api`). Der Ordner ist nicht eingecheckt.
  Der Generator braucht Java (lokal vorhanden, im Docker-Build wird eine JRE installiert).
- Felder sind in der Spec immer vorhanden; Objekt-Typen sind `T | null`. Felder, die nie null sind, tragen im Backend `@ApiNonNull`.
- Die Services in `frontend/src/app/services/` sind dünne Fassaden über `ActivitiesApi` / `OverviewApi` (Datumsformat, Polling).

---

## Projektstruktur

```
cycling/
├── cycling-messaging/          # MQTT-Topics, Nachrichten-Verträge, MQTT-Client (geteilt)
├── backend/                    # analysis-service: REST-API, intervals.icu, Analysen, H2
├── ai-service/                 # Gemini: beantwortet Analyse-/Empfehlungs-Aufträge über MQTT
├── report-delivery-service/    # Mail + Discord, eigene H2 für den Versandstatus
├── mosquitto/config/           # Broker-Konfiguration
├── frontend/                   # Angular 17
│   ├── openapi/                # OpenAPI-Spec des Backends (Quelle für den API-Client)
│   └── src/app/
│       ├── api/                # generiert (OpenAPI Generator), nicht eingecheckt
│       ├── pages/              # Aktivitäten, Report, Übersicht
│       └── services/           # Fassaden über den generierten Client
├── docker-compose.yml
└── .env.example
```
