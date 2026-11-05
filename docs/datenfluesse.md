# Datenflüsse

Wie die Services zusammenspielen. Die Verträge selbst stehen in drei Quellen:

| Was | Wo |
|---|---|
| MQTT zwischen den Services | [`asyncapi.yaml`](asyncapi.yaml) (AsyncAPI 3.0), im Code `cycling-messaging` |
| REST Frontend → Services | `frontend/openapi/report-api.yaml`, `training-api.yaml` (aus springdoc erzeugt) |
| REST Services → intervals-service | Modul `intervals-api` (`IntervalsApi`, Records, gemeinsamer `IntervalsClient`) |
| REST report-service → weather-service | Modul `weather-api` (`WeatherApi`, Records, `WeatherClient`) |

Faustregel: **REST nur dort, wo das Frontend fragt** (und für Abrufe beim intervals-service), **MQTT zwischen den
Services**. Jeder Service besitzt seine Daten selbst; nur der report-service und der delivery-service haben eine
Datenbank (je eine eigene H2).

## Überblick

```mermaid
flowchart LR
    subgraph Browser
        FE[Frontend<br/>Angular]
    end
    NG[nginx / Dev-Proxy]
    FE -->|/api/...| NG
    NG -->|/api/reports| RS[report-service<br/>H2 report.mv.db]
    NG -->|/api/fitness<br/>/api/recommendation| TS[training-service]

    RS -->|REST| IS[intervals-service<br/>Cache]
    TS -->|REST| IS
    IS -->|HTTPS| ICU[(intervals.icu)]

    IS -. activity/detected .-> RS
    IS -. wellness/updated .-> TS
    RS -. ai/request/report .-> AI[ai-service<br/>Gemini]
    AI -. ai/result/report .-> RS
    TS -. ai/request/training .-> AI
    AI -. ai/result/training .-> TS
    RS -. delivery/request/report .-> DS[delivery-service<br/>H2 delivery.mv.db]
    TS -. delivery/request/fitness .-> DS
    TS -. delivery/request/recommendation .-> DS
    DS -. delivery/status .-> RS
    DS -->|Mail / Discord| OUT[(Empfänger)]
    RS -. detect/ftp/request .-> FT[ftp-service]
    FT -. detect/ftp/response .-> RS
    FT -->|REST Streams| IS
    RS -->|REST Wetter| WS[weather-service<br/>Cache]
    WS -->|HTTPS| OM[(Open-Meteo)]
```

Durchgezogen = REST/HTTP, gestrichelt = MQTT über Mosquitto (Topics ohne Präfix `cycling/`).

## Analyse einer Fahrt

Automatisch, wenn der intervals-service eine neue Fahrt meldet, oder per Button im Kalender (Fahrt anklicken → „Mit KI analysieren“).
Beide Wege landen im selben Analyse-Job.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend
    participant RS as report-service
    participant IS as intervals-service
    participant WS as weather-service
    participant AI as ai-service
    participant DS as delivery-service

    alt automatisch
        IS-)RS: activity/detected (ActivityDetected)
        Note over RS: übersprungen, wenn Report vorhanden<br/>oder Job läuft
    else Button
        FE->>RS: POST /api/reports/{id}/analysis
        RS-->>FE: 202 AnalysisJobDto (PENDING)
    end
    RS->>IS: Schwellen, Streams, Zonen (REST)
    opt Outdoor-Fahrt mit GPS
        RS->>WS: POST /api/weather/points (alle 10 min, UTC)
        Note over RS: ohne Antwort weiter ohne Wetter
    end
    Note over RS: Job speichern (H2), letzte 3 Fahrten als Vergleich
    Note over RS: Prompt bauen (AnalysisPrompt)
    RS-)AI: ai/request/report (AiRequest, requestId = Job-ID)
    loop alle 3 s, nur beim Button
        FE->>RS: GET /api/reports/analysis-jobs/{jobId}
    end
    AI-)RS: ai/result/report (AiResult, JSON-Text)
    Note over RS: JSON lesen (unlesbar = Job FAILED)
    RS->>IS: Streams, Zonen, Power-Curve (REST)
    RS->>WS: Wetter erneut (aus dem Cache, gleiche Werte wie im Prompt)
    Note over RS: Analyse, Wetter und Report-Daten<br/>(Streams, Zonen) speichern (Upsert), Job DONE
    RS-)DS: delivery/request/report (DeliveryMessage, messageId = Job-ID)
    FE->>RS: GET /api/reports/{id}
    RS-->>FE: ReportDto
```

- Ohne Antwort nach `ANALYSIS_JOB_TIMEOUT` (15 min) wird der Job FAILED; ein spätes Ergebnis wird trotzdem übernommen.
- Rendern (QuickChart) oder Senden scheitert → Wiederholung nach 1, 5 und 15 Minuten, danach „Erneut senden“.

## Report-Seite, „Daten neu laden“ und „Erneut senden“

Ein Report kommt komplett aus der H2 (Analyse, Streams, Zonen, Kartenzonen, Power-Curve, Wetter). Anzeigen und
erneut senden klappt also auch ohne intervals-service. Nur Reports aus alter Version ohne gespeicherte Daten laden
diese beim ersten Öffnen einmal live nach.

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant RS as report-service
    participant IS as intervals-service
    participant WS as weather-service
    participant DS as delivery-service

    DS-)RS: delivery/status (retained: online, Kanäle)
    FE->>RS: GET /api/reports/{id}
    Note over RS: alles aus der H2
    RS-->>FE: ReportDto (404 ohne Analyse)
    opt Button „Daten neu laden“
        FE->>RS: POST /api/reports/{id}/refresh
        RS->>IS: Streams, Zonen, Kartenzonen, Power-Curve (REST)
        RS->>WS: Wetter (nur Outdoor mit GPS)
        Note over RS: speichern, KI-Bewertung bleibt
        RS-->>FE: ReportDto (502/503: gespeicherter Stand bleibt)
    end
    FE->>RS: POST /api/reports/{id}/resend
    alt kein Kanal aktiv
        RS-->>FE: 409 disabled
    else Status unbekannt / Report nicht ladbar
        RS-->>FE: 503 unavailable
    else
        RS-)DS: delivery/request/report (neue messageId)
        RS-->>FE: 202 queued + Kanäle
    end
```

## FTP-Test-Erkennung (ftp-service)

Nur manuell: Button „FTP-Test auswerten“ auf der Report-Seite. Das Ergebnis hängt an der Fahrt (ein Ergebnis pro
Aktivität, ein neuer Lauf überschreibt es) und wird nicht nach intervals.icu übernommen.

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant RS as report-service
    participant FT as ftp-service
    participant IS as intervals-service

    FE->>RS: POST /api/reports/{id}/ftp-detection
    Note over RS: läuft schon ein Lauf → dessen Status zurück
    RS-)FT: detect/ftp/request (DetectFtpRequest, neue requestId)
    RS-->>FE: 202 FtpDetectionDto (PENDING)
    FT->>IS: Streams (REST)
    Note over FT: bester 20-min-Block, Kriterien prüfen
    FT-)RS: detect/ftp/response (DetectFtpResponse)
    Note over RS: fremde requestId = alter Lauf → verwerfen
    loop alle 2 s, solange PENDING
        FE->>RS: GET /api/reports/{id}/ftp-detection
    end
```

- Ein Block gilt als FTP-Test, wenn alle Kriterien passen: höchstens 5 % fehlende Leistungs-/Pulswerte, Leistung
  mindestens 25 % über dem Rest der Fahrt (ohne Rollen), Puls der letzten 5 min mindestens 90 % der MaxHR.
  Ohne Puls kein Test.
- FTP = 20-min-Mittel × 0,95, LTHR = mittlerer Puls im Block, MaxHR = höchstes 5-s-Mittel der ganzen Fahrt.
- Ohne Antwort nach `FTP_DETECTION_TIMEOUT` (2 min) wird der Lauf beim nächsten Abruf FAILED.

## Übersicht: Fitness und Athlet (training-service)

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant FS as training-service
    participant IS as intervals-service
    participant DS as delivery-service

    FE->>FS: GET /api/fitness/athlete (Kopfzeile, Zonen)
    FE->>FS: GET /api/fitness/wellness?from&to
    opt Aktualisieren-Button (refresh=true)
        FS->>IS: POST cache/evict
    end
    FS->>IS: athlete/zones, wellness (REST)
    FS-->>FE: AthleteZonesDto, WellnessPointDto[]

    IS-)FS: wellness/updated (retained, bei Änderung)
    Note over FS: ab FITNESS_SUMMARY_TIME (21:00) einmal am Tag
    FS-)DS: delivery/request/fitness (messageId fitness-daily-DATUM)
```

## Übersicht: Tagesempfehlung (training-service)

Kategorie aus der Form-Zone (dieselbe Skala wie die Fitness-Anzeige), eine Stufe lockerer bei Ramp > +6.

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant RCS as training-service
    participant IS as intervals-service
    participant AI as ai-service
    participant DS as delivery-service

    FE->>RCS: GET /api/recommendation (oder POST /refresh)
    alt noch kein Stand oder Refresh
        RCS->>IS: wellness (21 Tage), activities (7 Tage)
        RCS-)AI: ai/request/training (Prompt aus RecommendationPrompt)
    end
    RCS-->>FE: DailyRecommendationDto (textPending=true, fester Text)
    AI-)RCS: ai/result/training (≤ 90 s, sonst bleibt der feste Text)
    loop solange textPending
        FE->>RCS: GET /api/recommendation
    end
    Note over RCS: zusätzlich alle 4 h neu und<br/>morgens ab RECOMMENDATION_DAILY_TIME
    RCS-)DS: delivery/request/recommendation (messageId recommendation-daily-DATUM)
```

## REST-Endpunkte fürs Frontend

| Service | Endpoint | Zweck |
|---|---|---|
| report-service | `GET /api/reports/activities?from&to` | Aktivitätsliste mit `analyzed` |
| | `POST /api/reports/{id}/analysis` | Analyse starten (202 + Job) |
| | `GET /api/reports/analysis-jobs/{jobId}` · `GET /api/reports/{id}/analysis-job` | Job-Status |
| | `GET /api/reports/{id}` | Report |
| | `POST /api/reports/{id}/refresh` | Streams, Zonen und Wetter neu laden (ohne KI) |
| | `POST /api/reports/{id}/resend` | Versand anstoßen |
| | `POST /api/reports/{id}/ftp-detection` · `GET /api/reports/{id}/ftp-detection` | FTP-Test-Erkennung starten (202) / Ergebnis |
| training-service | `GET /api/fitness/athlete[?refresh]` | Schwellen + Zonen |
| training-service | `PUT /api/fitness/athlete/thresholds` | Schwellen + Gewicht nach intervals.icu schreiben |
| | `GET /api/fitness/wellness?from&to[&refresh]` | CTL/ATL/Form |
| | `GET /api/recommendation` · `POST /api/recommendation/refresh` | Tagesempfehlung |

Fehler vom intervals-service kommen bei beiden als ProblemDetail mit `code` an (502/503/404, ggf. `Retry-After`).

## MQTT-Topics auf einen Blick

| Topic | Von → an | Payload | Retained |
|---|---|---|---|
| `cycling/intervals/activity/detected` | intervals → report | `ActivityDetected` | nein |
| `cycling/intervals/wellness/updated` | intervals → training | `WellnessUpdated` | ja |
| `cycling/ai/request/{report,training}` | Absender → ai | `AiRequest` (fertiger Prompt) | nein |
| `cycling/ai/result/{report,training}` | ai → Absender | `AiResult` (Antworttext) | nein |
| `cycling/delivery/request/{report,fitness,recommendation}` | Absender → delivery | `DeliveryMessage` | nein |
| `cycling/delivery/status` | delivery → report | `DeliveryServiceStatus` (Last Will: offline) | ja |
| `cycling/detect/ftp/request` | report → ftp | `DetectFtpRequest` | nein |
| `cycling/detect/ftp/response` | ftp → report | `DetectFtpResponse` | nein |

Details zu Feldern, QoS und Korrelation: [`asyncapi.yaml`](asyncapi.yaml). Ansehen z. B. im
[AsyncAPI Studio](https://studio.asyncapi.com/) (Datei hineinkopieren) oder mit der AsyncAPI-CLI:

```bash
npx @asyncapi/cli validate docs/asyncapi.yaml
```

**Pflege:** Wer ein Topic oder einen Vertrag in `cycling-messaging` ändert, passt `asyncapi.yaml` und diese Seite
im selben Commit an.
