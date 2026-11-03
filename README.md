# 🚴 Cycling Analysis – KI-gestützte Workout-Auswertung

Analysiere deine Radtrainings aus **intervals.icu** mit **Google Gemini AI** und erhalte strukturierte Reports mit Diagrammen.

## Tech-Stack

| Schicht | Technologie |
|---|---|
| Backend | Spring Boot 4 + Java 25, aufgeteilt in intervals-service, report-service, fitness-service, recommendation-service, ai-service, delivery-service |
| Messaging | MQTT (Eclipse Mosquitto, Paho-v5-Client) |
| API-Vertrag | OpenAPI (springdoc) je Service → OpenAPI Generator (typescript-angular), ein Client pro Service |
| Frontend | Angular 17 + Angular Material |
| KI | Google Gemini (Standard: gemini-2.5-flash) |
| Datenbank | H2 (persistent, File-basiert), je eine für report-service und delivery-service |
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
CYCLING_FTP=250                    # report-service: Default-FTP in Watt
CYCLING_MAX_HR=190                 # report-service: Default-Max-HF
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

**intervals-service, report-service, fitness-service, recommendation-service, ai-service, delivery-service** (jeweils in eigenem Terminal):
```bash
mvn install -DskipTests                       # einmalig, baut auch cycling-messaging, intervals-api und cycling-web
mvn -pl intervals-service spring-boot:run     # http://localhost:8081, braucht INTERVALS_API_KEY/INTERVALS_ATHLETE_ID
mvn -pl report-service spring-boot:run        # http://localhost:8082 (/api/reports), Swagger-UI: /swagger-ui.html
mvn -pl fitness-service spring-boot:run       # http://localhost:8083 (/api/fitness)
mvn -pl recommendation-service spring-boot:run  # http://localhost:8084 (/api/recommendation)
mvn -pl ai-service spring-boot:run            # braucht GEMINI_API_KEY
mvn -pl delivery-service spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm start          # erzeugt vorher die API-Clients, öffnet http://localhost:4200
```
Der Dev-Proxy (`proxy.conf.json`) leitet `/api/reports` an 8082, `/api/fitness` an 8083 und `/api/recommendation`
an 8084 weiter; im Docker-Betrieb macht das nginx (`frontend/nginx.conf`).

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

### Migration: Analysen aus dem alten backend übernehmen

Die Analysen lagen früher in der H2-Datei des backends (`cycling-analysis.mv.db`, Volume `cycling-data`), jetzt im
report-service (`report.mv.db`, Volume `report-data`). Tabellen und Spalten sind gleich geblieben, deshalb reicht es,
die Datei **einmal** zu kopieren, bevor neue Analysen entstehen:

```bash
docker volume ls | grep -E "cycling-data|report-data"   # Namen haben das Projekt als Präfix, z. B. cycling_ai_analyzer_
docker compose create report-service                     # legt report-data an, falls noch nicht vorhanden
docker compose stop report-service
docker run --rm -v <präfix>_cycling-data:/old:ro -v <präfix>_report-data:/new alpine \
  cp /old/cycling-analysis.mv.db /new/report.mv.db
docker compose start report-service
```

Lokal: `backend/data/cycling-analysis.mv.db` nach `report-service/data/report.mv.db` kopieren (report-service vorher
stoppen). Das alte Volume bleibt als Backup liegen, bis du es mit `docker volume rm <präfix>_cycling-data` löschst.

---

## 🤖 GitHub Actions + Docker Images für Raspberry Pi

Die Repository-Pipeline baut automatisch die Images aller Services und des Frontends und veröffentlicht sie in der GitHub Container Registry (`ghcr.io`).

### Was passiert automatisch?
- **Push auf `main`** → Images werden gebaut und gepusht
- **Pull Request** → Images werden nur gebaut, nicht gepusht
- **Plattformen** → `linux/amd64` und `linux/arm64`
- **Raspberry Pi** → kann direkt das `arm64`-Image ziehen

### Image-Namen
Je nach GitHub-Account heißen die Images typischerweise so:
```bash
ghcr.io/<dein-github-username>/cycling-report:latest
ghcr.io/<dein-github-username>/cycling-frontend:latest
```
Dazu kommen `cycling-intervals`, `cycling-fitness`, `cycling-recommendation`, `cycling-ai-service` und
`cycling-delivery` für die übrigen Services.

### Auf dem Raspberry Pi nutzen
```bash
# Login nur nötig, wenn das Paket privat ist
docker login ghcr.io

# z. B. report-service und Frontend ziehen
docker pull ghcr.io/<dein-github-username>/cycling-report:latest
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
- Schnellstatistiken pro Aktivität, Kennzeichnung bereits analysierter Fahrten (`analyzed`)

### Analyse und Report (report-service)

Der **report-service** besitzt alles rund um die Analyse einer Fahrt:

- **Start:** per Button (`POST /api/reports/{id}/analysis`, 202 + Job, das Frontend fragt den Job ab) oder automatisch,
  wenn der intervals-service eine neue Fahrt meldet (`cycling/intervals/activity/detected`). Fahrten mit Report oder
  laufendem Job werden übersprungen.
- **KI:** Auftrag an den ai-service (`cycling/ai/analysis/request|result`), Abbruch nach `ANALYSIS_JOB_TIMEOUT` (15 min).
- **Speicher:** eigene H2 (`report.mv.db`) mit Analysen, Power-Curves und Analyse-Jobs.
- **Report-Seite:** `GET /api/reports/{id}` lädt die Analyse und holt Streams/Zonen frisch vom intervals-service.
- **Versand:** nach der Analyse automatisch, sonst per „Erneut senden“ (`POST /api/reports/{id}/resend`).

| Endpoint | Liefert |
|---|---|
| `GET /api/reports/activities?from&to` | Rad-Aktivitäten mit `analyzed` |
| `POST /api/reports/{id}/analysis` | startet die Analyse (202 + Job) |
| `GET /api/reports/analysis-jobs/{jobId}` · `GET /api/reports/{id}/analysis-job` | Job-Status · jüngster Job einer Fahrt |
| `GET /api/reports/{id}` | Report (404, wenn noch nicht analysiert) |
| `POST /api/reports/{id}/resend` | Versand anstoßen (202, 409 kein Kanal aktiv, 503 Versand/Report nicht verfügbar) |

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

- **report-service** rendert nach jeder Analyse (und bei „Erneut senden“) die Charts und baut daraus eine neutrale
  `DeliveryMessage` (Titel, Felder, Abschnitte, PNG-Anhänge) auf `cycling/delivery/request/report`. Welche Kanäle
  aktiv sind, weiß er aus `cycling/delivery/status`. Fehlschläge beim Rendern oder Senden werden nach 1, 5 und
  15 Minuten wiederholt, danach hilft „Erneut senden“.
- **fitness-service** schickt einmal am Tag eine Fitness-Zusammenfassung auf `cycling/delivery/request/fitness`
  (siehe unten).
- **recommendation-service** schickt einmal am Tag die Tagesempfehlung auf `cycling/delivery/request/recommendation`
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
| `RECOMMENDATION_TARGETS` (recommendation-service) | wohin die Tagesempfehlung geht; Default `DISCORD:recommendation` (braucht `DELIVERY_DISCORD_DESTINATIONS_RECOMMENDATION`) |

Unbekannte Ziele werden sofort als fehlgeschlagen markiert.

### Fitness und Athlet (fitness-service)

Der **fitness-service** liefert dem Frontend den Fitness-Chart und das Athleten-Profil (Daten vom intervals-service;
mit `refresh=true` werden dessen Caches vorher geleert):

| Endpoint | Liefert |
|---|---|
| `GET /api/fitness/wellness?from&to[&refresh]` | Fitness (CTL), Fatigue (ATL), Form pro Tag |
| `GET /api/fitness/athlete[?refresh]` | Schwellenwerte (Kopfzeile) und Power-/HF-Zonen (Übersicht); leer statt Fehler |

**Tageszusammenfassung:** Der fitness-service abonniert `cycling/intervals/wellness/updated` (retained, vom intervals-service) und schickt
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

### Tagesempfehlung (recommendation-service)

Der **recommendation-service** berechnet die Trainingsempfehlung für die Overview-Karte und für Discord.
Er hat keine Datenbank; der letzte Stand liegt im Speicher.

- **Regeln:** Kategorie aus der Form (TSB) des jüngsten Wellness-Punkts (> +15 harte Intervalle, +5 … +15 Tempo,
  −10 … +5 Grundlage, −25 … −10 locker, < −25 Ruhetag). Steigt die CTL in 7 Tagen um mehr als 6, wird eine Stufe
  lockerer empfohlen. Daten: Wellness (21 Tage) und Fahrten (7 Tage, mit TSS/IF von intervals.icu) per REST vom
  intervals-service.
- **Text:** sofort ein fester Text pro Kategorie, den formulierten Text liefert der ai-service nach
  (`cycling/ai/recommendation/request|result`, höchstens 90 s Wartezeit).
- **Stand:** pro Berechnung zweimal mit derselben `requestId`: erst mit festem Text (`textPending=true`), dann mit
  KI-Text – oder, wenn die KI ausfällt, noch einmal mit festem Text (`textPending=false`). Ist der intervals-service
  nicht erreichbar, bleibt der letzte Stand.
- **Wann:** alle 4 Stunden (`RECOMMENDATION_REFRESH_CRON`), per Refresh-Button und morgens für Discord.

**Morgen-Nachricht:** ab 09:00 (`RECOMMENDATION_DAILY_TIME`, Zeitzone `RECOMMENDATION_ZONE`, Default `Europe/Berlin`)
wird frisch gerechnet und mit KI-Text verschickt, spätestens nach 2 Minuten mit festem Text (Fußzeile
„KI-Text nicht verfügbar“). Scheitert die Berechnung oder die Übergabe, folgt der nächste Versuch eine Minute später.
Nach einem Neustart wird nur bis 12:00 (`RECOMMENDATION_DAILY_CATCH_UP_UNTIL`) nachgeholt. Die messageId
`recommendation-daily-<datum>` verhindert doppelte Nachrichten.

**REST:** `GET /api/recommendation` liefert den letzten Stand (direkt nach dem Start wird einmal gerechnet),
`POST /api/recommendation/refresh` rechnet sofort neu und liefert den ersten Stand; solange `textPending=true` ist,
fragt die Overview erneut ab. Scheitert die Berechnung, bleibt der letzte Stand; gibt es noch keinen, kommt 503
(ProblemDetail mit `code`).

---

## 🔗 intervals.icu-Anbindung (intervals-service)

Nur der **intervals-service** spricht mit intervals.icu und kennt `INTERVALS_API_KEY` / `INTERVALS_ATHLETE_ID`.
Er speichert nichts dauerhaft, hält die Antworten aber im Speicher (Caffeine):
Aktivitätsliste 2 min, Wellness 15 min, Zonen/Schwellenwerte 1 h, Streams/Zonenzeiten/Power-Curve einer Aktivität 6 h.

**REST für report-, fitness- und recommendation-service** (Vertrag als Records im Modul `intervals-api`, Fehler als
ProblemDetail mit `code`; alle nutzen den gemeinsamen `IntervalsClient` aus demselben Modul):

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
| `cycling/intervals/activity/detected` | `ActivityDetected` – bei jedem Lauf alle Rad-Fahrten der letzten 2 Tage. Der report-service startet die Analyse für Fahrten ohne Report und ohne laufenden Job, so werden auch fehlgeschlagene Analysen erneut versucht. |
| `cycling/intervals/wellness/updated` | `WellnessUpdated` (retained) – heutige CTL/ATL/Form/Ramp plus Vortag, sobald sich ein Wert (auf eine Nachkommastelle) ändert. Der fitness-service macht daraus abends eine `DeliveryMessage` auf `cycling/delivery/request/fitness`. |

**Fehler und Rate-Limit:** Timeouts 5 s/30 s, bei 429/5xx/Netzwerkfehlern höchstens 2 Wiederholungen
(1 s, 3 s; `Retry-After` bis 10 s), höchstens 4 gleichzeitige Anfragen. An die Clients gehen 502 (`INTERVALS_AUTH`,
`INTERVALS_UNAVAILABLE`), 503 (`INTERVALS_RATE_LIMIT`, `INTERVALS_NOT_CONFIGURED`) oder 404; die Services geben sie
als ProblemDetail ans Frontend weiter (`IntervalsProblemHandler` in `cycling-web`). Schwellenwerte, Zonen und
Power-Curve sind fail-soft (leer statt Fehler).

Das Rohformat von intervals.icu (z. B. Streams als Array von `{type, data}` oder als Objekt) liest nur
`IntervalsParser` im intervals-service. Zum Prüfen der Zugangsdaten: `intervals-service/test-intervals-auth.ps1`.

---

## 🔀 Datenflüsse

Überblick und Abläufe (Analyse, Versand, Übersicht) mit Diagrammen: [`docs/datenfluesse.md`](docs/datenfluesse.md).
Alle MQTT-Topics und Payloads als AsyncAPI-Spec: [`docs/asyncapi.yaml`](docs/asyncapi.yaml).

---

## 🔌 API-Client (OpenAPI)

Die Angular-Models und -Services für die REST-APIs werden generiert, nicht von Hand geschrieben – ein Client pro Service:

```
report-service         (springdoc)  →  frontend/openapi/report-api.yaml          →  frontend/src/app/api/report/
fitness-service        (springdoc)  →  frontend/openapi/fitness-api.yaml         →  frontend/src/app/api/fitness/
recommendation-service (springdoc)  →  frontend/openapi/recommendation-api.yaml  →  frontend/src/app/api/recommendation/
```

- Die Specs sind eingecheckt. Der Test `OpenApiSpecTest` im jeweiligen Service schlägt fehl, sobald sie nicht mehr zur
  API passt. Nach einer API-Änderung aktualisieren mit (Modul anpassen):
  ```bash
  mvn -pl report-service test -Dtest=OpenApiSpecTest -Dopenapi.update=true
  ```
- `npm start` / `npm run build` erzeugen `src/app/api/` automatisch (`npm run generate:api`, alle drei Generatoren aus
  `openapitools.json`). Der Ordner ist nicht eingecheckt. Der Generator braucht Java (lokal vorhanden, im Docker-Build
  wird eine JRE installiert).
- Felder sind in der Spec immer vorhanden; Objekt-Typen sind `T | null`. Felder, die nie null sind, tragen `@ApiNonNull`.
  Diese Regeln, die ProblemDetail-Fehler und der Spec-Vergleich liegen gemeinsam im Modul `cycling-web`.
- Die Services in `frontend/src/app/services/` sind dünne Fassaden: `ActivityService` über `ReportsApi`
  (Datumsformat, Polling), `OverviewService` über `FitnessApi` und `RecommendationApi`.

---

## Projektstruktur

```
cycling/
├── cycling-messaging/          # MQTT-Topics, Nachrichten-Verträge, MQTT-Client (geteilt)
├── intervals-api/              # REST-Vertrag des intervals-service (Pfade + Records) und gemeinsamer IntervalsClient
├── cycling-web/                # gemeinsame Web-Bausteine: OpenAPI-Regeln, ProblemDetail-Fehler, Spec-Vergleich
├── intervals-service/          # einzige intervals.icu-Anbindung: REST mit Cache, meldet Aktivitäten/Fitness per MQTT
├── report-service/             # Analysen (KI-Auftrag, H2), REST /api/reports, Report -> DeliveryMessage (QuickChart)
├── fitness-service/            # REST /api/fitness (Wellness, Athlet), tägliche Fitness-Zusammenfassung, ohne Speicher
├── recommendation-service/     # Tagesempfehlung: REST /api/recommendation, morgens -> DeliveryMessage, ohne Speicher
├── ai-service/                 # Gemini: beantwortet Analyse-/Empfehlungs-Aufträge über MQTT
├── delivery-service/           # Mail + Discord für alle Quellen, eigene H2 für Nachrichten + Versandstatus
├── mosquitto/config/           # Broker-Konfiguration
├── docs/                       # Datenflüsse (Markdown + Mermaid) und AsyncAPI-Spec der MQTT-Topics
├── frontend/                   # Angular 17
│   ├── openapi/                # OpenAPI-Specs der Services (Quelle für die API-Clients)
│   └── src/app/
│       ├── api/                # generiert (OpenAPI Generator), je Service ein Unterordner, nicht eingecheckt
│       ├── pages/              # Aktivitäten, Report, Übersicht
│       └── services/           # Fassaden über die generierten Clients
├── docker-compose.yml
└── .env.example
```
