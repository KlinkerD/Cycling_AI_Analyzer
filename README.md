# 🚴 Cycling Analysis – KI-gestützte Workout-Auswertung

Analysiere deine Radtrainings aus **intervals.icu** mit **Google Gemini AI** und erhalte strukturierte Reports mit Diagrammen.

## Tech-Stack

| Schicht | Technologie |
|---|---|
| Backend | Spring Boot 4 + Java 25, aufgeteilt in analysis-service, intervals-service, ai-service, report-service, fitness-service, delivery-service |
| Messaging | MQTT (Eclipse Mosquitto, Paho-v5-Client) |
| API-Vertrag | OpenAPI (springdoc) → OpenAPI Generator (typescript-angular) |
| Frontend | Angular 17 + Angular Material |
| KI | Google Gemini (Standard: gemini-2.5-flash) |
| Datenbank | H2 (persistent, File-basiert), je eine für backend und delivery-service |
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
Umgebungsvariablen für die Services setzen, die sie brauchen:
```properties
INTERVALS_API_KEY=dein-api-key     # intervals-service
INTERVALS_ATHLETE_ID=i12345        # intervals-service
GEMINI_API_KEY=dein-gemini-key     # ai-service
CYCLING_FTP=250                    # backend: Default-FTP in Watt
CYCLING_MAX_HR=190                 # backend: Default-Max-HF
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

**intervals-service, Backend, ai-service, report-service, fitness-service, delivery-service** (jeweils in eigenem Terminal):
```bash
mvn install -DskipTests                       # einmalig, baut auch cycling-messaging und intervals-api
mvn -pl intervals-service spring-boot:run     # http://localhost:8081, braucht INTERVALS_API_KEY/INTERVALS_ATHLETE_ID
mvn -pl backend spring-boot:run               # http://localhost:8080, Swagger-UI: /swagger-ui.html
mvn -pl ai-service spring-boot:run            # braucht GEMINI_API_KEY
mvn -pl report-service spring-boot:run
mvn -pl fitness-service spring-boot:run
mvn -pl delivery-service spring-boot:run
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
Dazu kommen `cycling-intervals`, `cycling-ai-service`, `cycling-report` und `cycling-delivery` für die übrigen Services.

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

### Versand (Mail / Discord)

Absender bauen den Inhalt, der delivery-service stellt zu:

- **report-service** bekommt `cycling/report/ready` bzw. `cycling/report/resend`, holt den Report beim Backend,
  rendert die Charts und baut daraus eine neutrale `DeliveryMessage` (Titel, Felder, Abschnitte, PNG-Anhänge).
  Kein eigener Speicher: Fehlschläge werden nach 1, 5 und 15 Minuten wiederholt, danach hilft „Erneut senden“.
- **fitness-service** schickt einmal am Tag eine Fitness-Zusammenfassung auf `cycling/delivery/request/fitness`
  (siehe unten).
- **delivery-service** abonniert `cycling/delivery/request/+` (letzter Teil = Quelle, z. B. `report`),
  speichert die Nachricht und versendet sie per Mail/Discord – mit Wiederholung alle 15 Minuten (max. 5×, 2 Tage).
  Bilder werden nach 7 Tagen gelöscht, Statuszeilen nach 90 Tagen.

Neue Absender schicken einfach eine `DeliveryMessage` auf `cycling/delivery/request/<quelle>` –
am delivery-service ändert sich nichts.

**Ziele:** Absender nennen nur logische Ziele (`default`, `reports`, `alerts`, …). Die echten Adressen kennt nur der
delivery-service:

| Variable | Bedeutung |
|---|---|
| `DISCORD_CHANNEL_ID` / `REPORT_MAIL_TO` | Ziel `default` für Discord bzw. Mail |
| `DELIVERY_DISCORD_DESTINATIONS_<NAME>` | weiteres Discord-Ziel, z. B. `..._REPORTS=1234…` |
| `DELIVERY_MAIL_DESTINATIONS_<NAME>` | weiteres Mail-Ziel |
| `REPORT_TARGETS` (report-service) | wohin Reports gehen, z. B. `DISCORD:reports,MAIL:default`; leer = `default` jedes aktiven Kanals |
| `FITNESS_TARGETS` (fitness-service) | wohin die Fitness-Zusammenfassung geht; Default `DISCORD:fitness` (braucht `DELIVERY_DISCORD_DESTINATIONS_FITNESS`) |

Unbekannte Ziele werden sofort als fehlgeschlagen markiert.

### Fitness-Zusammenfassung (fitness-service)

Der **fitness-service** abonniert `cycling/intervals/wellness/updated` (retained, vom intervals-service) und schickt
**einmal am Tag ab 21:00** (`FITNESS_SUMMARY_TIME`, Zeitzone `FITNESS_SUMMARY_ZONE`, Default `Europe/Berlin`)
eine Zusammenfassung an den delivery-service:

- Felder: Fitness (CTL), Fatigue (ATL), Form (TSB) jeweils mit Änderung zum Vortag, dazu Ramp in CTL/Woche
- Form-Zone als farbiger Abschnitt (mit „gestern: …“ bei Zonenwechsel); die Zone bestimmt auch die Farbe:

  | Zone | Form |
  |---|---|
  | ⚪ Übergang | ≥ +25 |
  | 🔵 Frisch | +5 … < +25 |
  | ⚫ Grauzone | −10 … < +5 |
  | 🟢 Optimal | −30 … < −10 |
  | 🔴 Hohes Risiko | < −30 |

- Ramp-Hinweis nur außerhalb von −5 … +6: > 6 zügiger Aufbau, > 8 zu schnell, < −5 Fitness baut ab

Kein eigener Speicher: Der letzte Stand liegt im Speicher, nach einem Start liefert der Broker die retained Nachricht.
Liegt um 21:00 noch kein Stand von heute vor, wird gewartet und bis Mitternacht nachgeholt (Prüfung jede Minute).
Die messageId `fitness-daily-<datum>` sorgt dafür, dass der delivery-service eine zweite Nachricht für denselben Tag
(z. B. nach einem Neustart) verwirft.

---

## 🔗 intervals.icu-Anbindung (intervals-service)

Nur der **intervals-service** spricht mit intervals.icu und kennt `INTERVALS_API_KEY` / `INTERVALS_ATHLETE_ID`.
Er speichert nichts dauerhaft, hält die Antworten aber im Speicher (Caffeine):
Aktivitätsliste 2 min, Wellness 15 min, Zonen/Schwellenwerte 1 h, Streams/Zonenzeiten/Power-Curve einer Aktivität 6 h.

**REST für das Backend** (Vertrag als Records im Modul `intervals-api`, Fehler als ProblemDetail mit `code`):

| Endpoint | Liefert |
|---|---|
| `GET /api/intervals/activities?from&to` | Rad-Aktivitäten (Ride, VirtualRide) |
| `GET /api/intervals/activities/{id}/streams` | Zeitreihen (Leistung, HF, Tempo, Höhe, Trittfrequenz) |
| `GET /api/intervals/activities/{id}/zones` | Zonenzeiten, Indoor-Kennung, `icu_ftp` |
| `GET /api/intervals/activities/{id}/power-curve` | Power-Curve |
| `GET /api/intervals/athlete/thresholds` · `/athlete/zones` | FTP, LTHR, Max-HF, Gewicht · Power-/HF-Zonen |
| `GET /api/intervals/wellness?from&to` | Fitness (CTL), Fatigue (ATL), Form, Ramp pro Tag |
| `POST /api/intervals/cache/evict` | leert die Caches (z. B. nach FTP-Änderung; „Aktualisieren“ in der Übersicht) |

**MQTT** (Abfrage alle `INTERVALS_POLL_INTERVAL`, Default 15 min):

| Topic | Inhalt |
|---|---|
| `cycling/intervals/activity/detected` | `ActivityDetected` – bei jedem Lauf alle Rad-Fahrten der letzten 2 Tage. Das Backend startet die Analyse für Fahrten ohne Report und ohne laufenden Job, so werden auch fehlgeschlagene Analysen erneut versucht. |
| `cycling/intervals/wellness/updated` | `WellnessUpdated` (retained) – heutige CTL/ATL/Form/Ramp plus Vortag, sobald sich ein Wert (auf eine Nachkommastelle) ändert. Der fitness-service macht daraus abends eine `DeliveryMessage` auf `cycling/delivery/request/fitness`. |

**Fehler und Rate-Limit:** Timeouts 5 s/30 s, bei 429/5xx/Netzwerkfehlern höchstens 2 Wiederholungen
(1 s, 3 s; `Retry-After` bis 10 s), höchstens 4 gleichzeitige Anfragen. Ans Backend gehen 502 (`INTERVALS_AUTH`,
`INTERVALS_UNAVAILABLE`), 503 (`INTERVALS_RATE_LIMIT`, `INTERVALS_NOT_CONFIGURED`) oder 404; das Backend gibt sie
als ProblemDetail ans Frontend weiter. Schwellenwerte, Zonen und Power-Curve sind im Backend fail-soft (leer statt Fehler).

Das Rohformat von intervals.icu (z. B. Streams als Array von `{type, data}` oder als Objekt) liest nur
`IntervalsParser` im intervals-service. Zum Prüfen der Zugangsdaten: `intervals-service/test-intervals-auth.ps1`.

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
├── intervals-api/              # REST-Vertrag des intervals-service (Pfade + Records), geteilt mit backend
├── intervals-service/          # einzige intervals.icu-Anbindung: REST mit Cache, meldet Aktivitäten/Fitness per MQTT
├── backend/                    # analysis-service: REST-API, Analysen, Tagesempfehlung, H2
├── ai-service/                 # Gemini: beantwortet Analyse-/Empfehlungs-Aufträge über MQTT
├── report-service/             # Report -> DeliveryMessage (Charts via QuickChart), ohne Speicher
├── fitness-service/            # tägliche Fitness-Zusammenfassung (CTL/ATL/Form/Ramp) -> DeliveryMessage, ohne Speicher
├── delivery-service/           # Mail + Discord für alle Quellen, eigene H2 für Nachrichten + Versandstatus
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
