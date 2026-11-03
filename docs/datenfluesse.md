# Datenflüsse

Wie die Services zusammenspielen. Die Verträge selbst stehen in drei Quellen:

| Was | Wo |
|---|---|
| MQTT zwischen den Services | [`asyncapi.yaml`](asyncapi.yaml) (AsyncAPI 3.0), im Code `cycling-messaging` |
| REST Frontend → Services | `frontend/openapi/report-api.yaml`, `fitness-api.yaml`, `recommendation-api.yaml` (aus springdoc erzeugt) |
| REST Services → intervals-service | Modul `intervals-api` (`IntervalsApi`, Records, gemeinsamer `IntervalsClient`) |

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
    NG -->|/api/fitness| FS[fitness-service]
    NG -->|/api/recommendation| RCS[recommendation-service]

    RS -->|REST| IS[intervals-service<br/>Cache]
    FS -->|REST| IS
    RCS -->|REST| IS
    IS -->|HTTPS| ICU[(intervals.icu)]

    IS -. activity/detected .-> RS
    IS -. wellness/updated .-> FS
    RS -. ai/analysis/request .-> AI[ai-service<br/>Gemini]
    AI -. ai/analysis/result .-> RS
    RCS -. ai/recommendation/request .-> AI
    AI -. ai/recommendation/result .-> RCS
    RS -. delivery/request/report .-> DS[delivery-service<br/>H2 delivery.mv.db]
    FS -. delivery/request/fitness .-> DS
    RCS -. delivery/request/recommendation .-> DS
    DS -. delivery/status .-> RS
    DS -->|Mail / Discord| OUT[(Empfänger)]
```

Durchgezogen = REST/HTTP, gestrichelt = MQTT über Mosquitto (Topics ohne Präfix `cycling/`).

## Analyse einer Fahrt

Automatisch, wenn der intervals-service eine neue Fahrt meldet, oder per Button in der Aktivitätsliste.
Beide Wege landen im selben Analyse-Job.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend
    participant RS as report-service
    participant IS as intervals-service
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
    Note over RS: Job speichern (H2), letzte 3 Fahrten als Vergleich
    RS-)AI: ai/analysis/request (requestId = Job-ID)
    loop alle 3 s, nur beim Button
        FE->>RS: GET /api/reports/analysis-jobs/{jobId}
    end
    AI-)RS: ai/analysis/result
    RS->>IS: Streams, Zonen, Power-Curve (REST)
    Note over RS: Analyse speichern (Upsert), Job DONE
    RS-)DS: delivery/request/report (DeliveryMessage, messageId = Job-ID)
    FE->>RS: GET /api/reports/{id}
    RS-->>FE: ReportDto
```

- Ohne Antwort nach `ANALYSIS_JOB_TIMEOUT` (15 min) wird der Job FAILED; ein spätes Ergebnis wird trotzdem übernommen.
- Rendern (QuickChart) oder Senden scheitert → Wiederholung nach 1, 5 und 15 Minuten, danach „Erneut senden“.

## Report-Seite und „Erneut senden“

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant RS as report-service
    participant IS as intervals-service
    participant DS as delivery-service

    DS-)RS: delivery/status (retained: online, Kanäle)
    FE->>RS: GET /api/reports/{id}
    RS->>IS: Streams, Schwellen, Zonen (REST)
    RS-->>FE: ReportDto (404 ohne Analyse)
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

## Übersicht: Fitness und Athlet

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant FS as fitness-service
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

## Übersicht: Tagesempfehlung

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant RCS as recommendation-service
    participant IS as intervals-service
    participant AI as ai-service
    participant DS as delivery-service

    FE->>RCS: GET /api/recommendation (oder POST /refresh)
    alt noch kein Stand oder Refresh
        RCS->>IS: wellness (21 Tage), activities (7 Tage)
        RCS-)AI: ai/recommendation/request
    end
    RCS-->>FE: DailyRecommendationDto (textPending=true, fester Text)
    AI-)RCS: ai/recommendation/result (≤ 90 s, sonst bleibt der feste Text)
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
| | `POST /api/reports/{id}/resend` | Versand anstoßen |
| fitness-service | `GET /api/fitness/athlete[?refresh]` | Schwellen + Zonen |
| | `GET /api/fitness/wellness?from&to[&refresh]` | CTL/ATL/Form |
| recommendation-service | `GET /api/recommendation` · `POST /api/recommendation/refresh` | Tagesempfehlung |

Fehler vom intervals-service kommen bei allen dreien als ProblemDetail mit `code` an (502/503/404, ggf. `Retry-After`).

## MQTT-Topics auf einen Blick

| Topic | Von → an | Payload | Retained |
|---|---|---|---|
| `cycling/intervals/activity/detected` | intervals → report | `ActivityDetected` | nein |
| `cycling/intervals/wellness/updated` | intervals → fitness | `WellnessUpdated` | ja |
| `cycling/ai/analysis/request` · `/result` | report ↔ ai | `AnalysisRequest` · `AnalysisResult` | nein |
| `cycling/ai/recommendation/request` · `/result` | recommendation ↔ ai | `RecommendationTextRequest` · `RecommendationTextResult` | nein |
| `cycling/delivery/request/{report,fitness,recommendation}` | Absender → delivery | `DeliveryMessage` | nein |
| `cycling/delivery/status` | delivery → report | `DeliveryServiceStatus` (Last Will: offline) | ja |

Details zu Feldern, QoS und Korrelation: [`asyncapi.yaml`](asyncapi.yaml). Ansehen z. B. im
[AsyncAPI Studio](https://studio.asyncapi.com/) (Datei hineinkopieren) oder mit der AsyncAPI-CLI:

```bash
npx @asyncapi/cli validate docs/asyncapi.yaml
```

**Pflege:** Wer ein Topic oder einen Vertrag in `cycling-messaging` ändert, passt `asyncapi.yaml` und diese Seite
im selben Commit an.
