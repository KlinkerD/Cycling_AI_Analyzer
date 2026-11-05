# 🚴 Cycling Analysis – KI-gestützte Workout-Auswertung

Analysiere deine Radtrainings aus **intervals.icu** mit **Google Gemini AI** und erhalte strukturierte Reports mit Diagrammen.

## Tech-Stack

| Schicht | Technologie |
|---|---|
| Backend | Spring Boot 4 + Java 25, aufgeteilt in intervals-service, report-service, training-service, ai-service, delivery-service, ftp-service, weather-service |
| Messaging | MQTT (Eclipse Mosquitto, Paho-v5-Client) |
| API-Vertrag | OpenAPI (springdoc) je Service → OpenAPI Generator (typescript-angular), ein Client pro Service |
| Frontend | Angular 21 + Angular Material 3, Chart.js (mit Zoom-Plugin), Leaflet (Karte, OpenStreetMap-Kacheln) |
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

**intervals-service, report-service, training-service, ai-service, delivery-service, ftp-service, weather-service** (jeweils in eigenem Terminal):
```bash
mvn install -DskipTests                       # einmalig, baut auch cycling-messaging, intervals-api, weather-api und cycling-web
mvn -pl intervals-service spring-boot:run     # http://localhost:8081, braucht INTERVALS_API_KEY/INTERVALS_ATHLETE_ID
mvn -pl report-service spring-boot:run        # http://localhost:8082 (/api/reports), Swagger-UI: /swagger-ui.html
mvn -pl training-service spring-boot:run      # http://localhost:8083 (/api/fitness, /api/recommendation)
mvn -pl ai-service spring-boot:run            # braucht GEMINI_API_KEY
mvn -pl delivery-service spring-boot:run
mvn -pl ftp-service spring-boot:run           # FTP-Test-Erkennung, braucht den intervals-service
mvn -pl weather-service spring-boot:run       # http://localhost:8085, Wetter fuer Outdoor-Fahrten (Open-Meteo, ohne Key)
```

**Frontend** (braucht Node 20.19+ oder 22.12+):
```bash
cd frontend
npm install
npm start          # erzeugt vorher die API-Clients, öffnet http://localhost:4200
```
Der Dev-Proxy (`proxy.conf.json`) leitet `/api/reports` an 8082, `/api/fitness` und `/api/recommendation` an 8083
weiter; im Docker-Betrieb macht das nginx (`frontend/nginx.conf`).

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
Dazu kommen `cycling-intervals`, `cycling-training`, `cycling-ai-service`, `cycling-ftp` und
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
- **KI:** baut den Prompt selbst (`AnalysisPrompt`), schickt ihn an den ai-service (`cycling/ai/request/report`) und
  liest die JSON-Antwort (`cycling/ai/result/report`); Abbruch nach `ANALYSIS_JOB_TIMEOUT` (15 min).
- **Speicher:** eigene H2 (`report.mv.db`) mit Analysen, Power-Curves, Wetter, Report-Daten (Streams, Zonen) und
  Analyse-Jobs.
- **Report-Seite:** `GET /api/reports/{id}` kommt komplett aus der H2, auch ohne intervals-service. Der Button
  „Daten neu laden“ (`POST /api/reports/{id}/refresh`) holt Streams, Zonen, Power-Curve und Wetter frisch, ohne
  neue KI-Bewertung.
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

### Karte (Fahrtverlauf)
- Tab **Karte** im Report: Route auf OpenStreetMap, einfärbbar nach Leistungszonen, HF-Zonen, Tempo oder einfarbig;
  Hover zeigt km, Fahrzeit, Watt, Puls und Tempo. Ohne GPS (z. B. Rolle ohne Zwift) ist der Tab ausgegraut.
- Leistungszonen mit der FTP der Fahrt (`ftpUsed`, wie IF/TSS), HF-Zonen mit den aktuellen Grenzen. Die Grenzen
  liefert der Report mit (`powerZoneBands`, `hrZoneBands`), damit Karte und Versandbild gleich gefärbt sind.
  Werte werden über ±15 Punkte geglättet, Tempo in sechs Stufen zwischen 5. und 95. Perzentil.
- Zwift-Fahrten liefern Watopia-Koordinaten; ihre Route liegt auf der Karte an diesem (realen) Ort.

### Versand (Mail / Discord)

Absender bauen den Inhalt, der delivery-service stellt zu:

- **report-service** rendert nach jeder Analyse (und bei „Erneut senden“) das Kartenbild (erstes Bild, 800×500,
  nach Leistungszonen, sonst Tempo; selbst gezeichnet auf OSM-Kacheln) und die Charts und baut daraus eine neutrale
  `DeliveryMessage` (Titel, Felder, Abschnitte, PNG-Anhänge) auf `cycling/delivery/request/report`. Welche Kanäle
  aktiv sind, weiß er aus `cycling/delivery/status`. Fehlschläge beim Rendern oder Senden werden nach 1, 5 und
  15 Minuten wiederholt, danach hilft „Erneut senden“.
- **training-service** schickt abends die Fitness-Zusammenfassung auf `cycling/delivery/request/fitness` und
  morgens die Tagesempfehlung auf `cycling/delivery/request/recommendation` (siehe unten).
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
| `REPORT_MAP_TILE_URL` (report-service) | Kachel-URL für das Kartenbild (`{z}/{x}/{y}`), Default OpenStreetMap; leer = Route ohne Kartenhintergrund. Laden die Kacheln nicht, kommt die Route ebenfalls auf neutralen Grund |
| `REPORT_TARGETS` (report-service) | wohin Reports gehen, z. B. `DISCORD:reports,MAIL:default`; leer = `default` jedes aktiven Kanals |
| `FITNESS_TARGETS` (training-service) | wohin die Fitness-Zusammenfassung geht; Default `DISCORD:fitness` (braucht `DELIVERY_DISCORD_DESTINATIONS_FITNESS`) |
| `RECOMMENDATION_TARGETS` (training-service) | wohin die Tagesempfehlung geht; Default `DISCORD:recommendation` (braucht `DELIVERY_DISCORD_DESTINATIONS_RECOMMENDATION`) |

Unbekannte Ziele werden sofort als fehlgeschlagen markiert.

### Training: Fitness, Form und Tagesempfehlung (training-service)

Der **training-service** besitzt alles rund um den Zustand des Athleten: Fitness-Chart, Athleten-Profil, Form-Zonen
und die Tagesempfehlung. Beide nutzen dieselbe Einordnung von Form und Ramp (`FormZone`, `RampAssessment`), damit
Übersicht, Abend-Zusammenfassung und Empfehlung sich nicht widersprechen. Kein eigener Speicher.

#### Fitness und Athlet

Fitness-Chart und Athleten-Profil kommen vom intervals-service; mit `refresh=true` werden dessen Caches vorher geleert:

| Endpoint | Liefert |
|---|---|
| `GET /api/fitness/wellness?from&to[&refresh]` | Fitness (CTL), Fatigue (ATL), Form pro Tag |
| `GET /api/fitness/athlete[?refresh]` | Schwellenwerte (Seitenleiste) und Power-/HF-Zonen (Übersicht); leer statt Fehler |
| `PUT /api/fitness/athlete/thresholds` | FTP outdoor/indoor, LTHR, HFmax, Gewicht nach intervals.icu schreiben (null = unverändert); liefert neue Zonen, 400 bei unplausiblen Werten |

**Tageszusammenfassung:** Der training-service abonniert `cycling/intervals/wellness/updated` (retained, vom intervals-service) und schickt
**einmal am Tag ab 21:00** (`FITNESS_SUMMARY_TIME`, Zeitzone `TRAINING_ZONE`, Default `Europe/Berlin`)
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

Der letzte Stand liegt im Speicher, nach einem Start liefert der Broker die retained Nachricht.
Liegt um 21:00 noch kein Stand von heute vor, wird gewartet und bis Mitternacht nachgeholt (Prüfung jede Minute).
Die messageId `fitness-daily-<datum>` sorgt dafür, dass der delivery-service eine zweite Nachricht für denselben Tag
(z. B. nach einem Neustart) verwirft.

#### Tagesempfehlung

Die Trainingsempfehlung für die Overview-Karte und für Discord; der letzte Stand liegt im Speicher.

- **Regeln:** Kategorie aus der Form-Zone des jüngsten Wellness-Punkts (dieselben Zonen wie oben):

  | Form-Zone | Empfehlung |
  |---|---|
  | ⚪ Übergang, 🔵 Frisch | Harte Intervalle |
  | ⚫ Grauzone | Tempo / Sweet Spot |
  | 🟢 Optimal | Grundlage |
  | 🔴 Hohes Risiko | Ruhetag |

  Liegt die Ramp über +6 (zügiger oder zu schneller Aufbau, wie in der Zusammenfassung), wird eine Stufe lockerer
  empfohlen; „Locker / Regeneration“ entsteht so aus „Grundlage“. Die Ramp kommt von intervals.icu, fehlt sie, wird
  CTL heute minus CTL vor 7 Tagen genommen. Daten: Wellness (21 Tage) und Fahrten (7 Tage, mit TSS/IF) per REST vom
  intervals-service.
- **Text:** sofort ein fester Text pro Kategorie. Den formulierten Text liefert der ai-service nach: Der
  training-service baut den Prompt (`RecommendationPrompt`) und schickt ihn auf `cycling/ai/request/training`, die
  Antwort kommt auf `cycling/ai/result/training` (höchstens 90 s Wartezeit).
- **Stand:** pro Berechnung zweimal mit derselben `requestId`: erst mit festem Text (`textPending=true`), dann mit
  KI-Text – oder, wenn die KI ausfällt, noch einmal mit festem Text (`textPending=false`). Ist der intervals-service
  nicht erreichbar, bleibt der letzte Stand.
- **Wann:** alle 4 Stunden (`RECOMMENDATION_REFRESH_CRON`), per Refresh-Button und morgens für Discord.

**Morgen-Nachricht:** ab 09:00 (`RECOMMENDATION_DAILY_TIME`, Zeitzone `TRAINING_ZONE`, Default `Europe/Berlin`)
wird frisch gerechnet und mit KI-Text verschickt, spätestens nach 2 Minuten mit festem Text (Fußzeile
„KI-Text nicht verfügbar“). Scheitert die Berechnung oder die Übergabe, folgt der nächste Versuch eine Minute später.
Nach einem Neustart wird nur bis 12:00 (`RECOMMENDATION_DAILY_CATCH_UP_UNTIL`) nachgeholt. Die messageId
`recommendation-daily-<datum>` verhindert doppelte Nachrichten.

**REST:** `GET /api/recommendation` liefert den letzten Stand (direkt nach dem Start wird einmal gerechnet),
`POST /api/recommendation/refresh` rechnet sofort neu und liefert den ersten Stand; solange `textPending=true` ist,
fragt die Overview erneut ab. Scheitert die Berechnung, bleibt der letzte Stand; gibt es noch keinen, kommt 503
(ProblemDetail mit `code`).

---

## 🤖 KI (ai-service)

Der **ai-service** kennt nur Gemini, keine Fachlichkeit: Prompt rein, Antworttext raus. Die Absender bauen den Prompt
und werten die Antwort selbst aus (report-service: `AnalysisPrompt`, training-service: `RecommendationPrompt`).

| Topic | Inhalt |
|---|---|
| `cycling/ai/request/<quelle>` | `AiRequest` – `requestId`, fertiger `prompt`, `temperature`, `json` (true = reines JSON anfordern) |
| `cycling/ai/result/<quelle>` | `AiResult` – dieselbe `requestId`, `success`, `error`, `text` (unverändert) |

Jeder Auftrag bekommt genau eine Antwort, auch bei Fehlern (leerer Prompt, Temperatur außerhalb 0 … 2, Gemini-Fehler).
Zwischen zwei Gemini-Aufrufen liegen mindestens `GEMINI_MIN_INTERVAL` (Default 5 s). Neue Anwendungsfälle brauchen nur
eine neue Quelle – am ai-service ändert sich nichts.

---

## 🌦️ Wetter für Outdoor-Fahrten (weather-service)

Bei der Analyse einer **Outdoor-Fahrt** (Indoor-Kennung `false`, Typ `Ride`, mit GPS) wählt der report-service
alle 10 Minuten einen Punkt der Fahrt (plus das Ende) und fragt beim **weather-service** Temperatur, Wind, Böen,
Windrichtung und Niederschlag ab (`POST /api/weather/points`, Vertrag im Modul `weather-api`, Zeiten in UTC aus
`start_date` von intervals.icu). Der weather-service holt die Werte ohne API-Key bei **Open-Meteo**: Fahrten der
letzten 90 Tage aus der Forecast-API im 15-Minuten-Takt, ältere aus der Archive-API (stündlich), jeweils zeitlich
interpoliert. Punkte in derselben Rasterzelle (~2 km) teilen sich einen Wert, alle Orte gehen in einem Aufruf.

- Das Wetter wird mit der Analyse gespeichert (Tabelle `weather_point`), Report und „Erneut senden“ lesen nur noch daraus.
- Gemini bekommt einen Abschnitt **WETTER** mit Kennzahlen und dem Verlauf, inkl. Gegen-/Rücken-/Seitenwind
  (aus der Fahrtrichtung über ~500 m berechnet), und bei den Vergleichsfahrten deren Temperatur und Wind.
- Report (Mail/Discord) und Frontend (Tab „Verläufe“) zeigen einen Graphen **Temperatur & Wind** über die Distanz;
  Regen erscheint als Hinweis mit der Gesamtmenge.
- Fällt der weather-service aus, läuft die Analyse ohne Wetter weiter (Timeout 10 s, eine Wiederholung).
  `WEATHER_SERVICE_URL=` (leer) schaltet das Wetter ab. Alte Analysen bekommen Wetter erst bei einer Neuanalyse.

---

## 🔗 intervals.icu-Anbindung (intervals-service)

Nur der **intervals-service** spricht mit intervals.icu und kennt `INTERVALS_API_KEY` / `INTERVALS_ATHLETE_ID`.
Er speichert nichts dauerhaft, hält die Antworten aber im Speicher (Caffeine):
Aktivitätsliste 2 min, Wellness 15 min, Zonen/Schwellenwerte 1 h, Streams/Zonenzeiten/Power-Curve einer Aktivität 6 h.

**REST für report- und training-service** (Vertrag als Records im Modul `intervals-api`, Fehler als
ProblemDetail mit `code`; alle nutzen den gemeinsamen `IntervalsClient` aus demselben Modul):

| Endpoint | Liefert |
|---|---|
| `GET /api/intervals/activities?from&to` | Rad-Aktivitäten (Ride, VirtualRide) |
| `GET /api/intervals/activities/{id}/streams` | Zeitreihen (Leistung, HF, Tempo, Höhe, Trittfrequenz, GPS lat/lng) |
| `GET /api/intervals/activities/{id}/zones` | Zonenzeiten, Indoor-Kennung, `icu_ftp` |
| `GET /api/intervals/activities/{id}/power-curve` | Power-Curve |
| `GET /api/intervals/athlete/thresholds` · `/athlete/zones` | FTP, LTHR, Max-HF, Gewicht · Power-/HF-Zonen |
| `PUT /api/intervals/athlete/thresholds` | schreibt die Rad-Einstellungen (`PUT /athlete/{id}/sport-settings/{id}`, HF-Zonen neu bei LTHR/HFmax) und `icu_weight` (`PUT /athlete/{id}`), leert danach die Caches |
| `GET /api/intervals/wellness?from&to` | Fitness (CTL), Fatigue (ATL), Form, Ramp pro Tag |
| `POST /api/intervals/cache/evict` | leert die Caches (z. B. nach FTP-Änderung; „Aktualisieren“ in der Übersicht) |

**MQTT** (Abfrage alle `INTERVALS_POLL_INTERVAL`, Default 15 min):

| Topic | Inhalt |
|---|---|
| `cycling/intervals/activity/detected` | `ActivityDetected` – bei jedem Lauf alle Rad-Fahrten der letzten 2 Tage. Der report-service startet die Analyse für Fahrten ohne Report und ohne laufenden Job, so werden auch fehlgeschlagene Analysen erneut versucht. |
| `cycling/intervals/wellness/updated` | `WellnessUpdated` (retained) – heutige CTL/ATL/Form/Ramp plus Vortag, sobald sich ein Wert (auf eine Nachkommastelle) ändert. Der training-service macht daraus abends eine `DeliveryMessage` auf `cycling/delivery/request/fitness`. |

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
report-service   (springdoc)  →  frontend/openapi/report-api.yaml    →  frontend/src/app/api/report/
training-service (springdoc)  →  frontend/openapi/training-api.yaml  →  frontend/src/app/api/training/
```

- Die Specs sind eingecheckt. Der Test `OpenApiSpecTest` im jeweiligen Service schlägt fehl, sobald sie nicht mehr zur
  API passt. Nach einer API-Änderung aktualisieren mit (Modul anpassen):
  ```bash
  mvn -pl report-service test -Dtest=OpenApiSpecTest -Dopenapi.update=true
  ```
- `npm start` / `npm run build` erzeugen `src/app/api/` automatisch (`npm run generate:api`, beide Generatoren aus
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
├── training-service/           # Fitness, Form-Zonen, Athlet und Tagesempfehlung: REST /api/fitness + /api/recommendation,
│                               #   abends Fitness-, morgens Empfehlungs-Nachricht, ohne Speicher
├── ai-service/                 # Gemini: generische KI-Aufträge (Prompt rein, Text raus) über MQTT
├── delivery-service/           # Mail + Discord für alle Quellen, eigene H2 für Nachrichten + Versandstatus
├── ftp-service/                # erkennt 20-Minuten-FTP-Tests (FTP, LTHR, MaxHR) über MQTT, Streams per REST vom intervals-service, ohne Speicher
├── weather-api/                # REST-Vertrag des weather-service (Pfad + Records) und WeatherClient
├── weather-service/            # Temperatur, Wind, Regen zu Ort + Zeit von Open-Meteo (REST, ohne Speicher, kurzer Cache)
├── mosquitto/config/           # Broker-Konfiguration
├── docs/                       # Datenflüsse (Markdown + Mermaid) und AsyncAPI-Spec der MQTT-Topics
├── frontend/                   # Angular 21
│   ├── openapi/                # OpenAPI-Specs der Services (Quelle für die API-Clients)
│   └── src/app/
│       ├── api/                # generiert (OpenAPI Generator), je Service ein Unterordner, nicht eingecheckt
│       ├── pages/              # Aktivitäten, Report, Übersicht
│       └── services/           # Fassaden über die generierten Clients
├── docker-compose.yml
└── .env.example
```
