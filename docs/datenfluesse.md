# Datenflüsse

Wie die Services zusammenspielen. Die Verträge selbst stehen in drei Quellen:

| Was | Wo |
|---|---|
| MQTT zwischen den Services | [`asyncapi.yaml`](asyncapi.yaml) (AsyncAPI 3.0), im Code `cycling-messaging` |
| REST Frontend → Services | `frontend/openapi/report-api.yaml`, `training-api.yaml` (aus springdoc erzeugt) |
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
    Note over RS: Prompt bauen (AnalysisPrompt)
    RS-)AI: ai/request/report (AiRequest, requestId = Job-ID)
    loop alle 3 s, nur beim Button
        FE->>RS: GET /api/reports/analysis-jobs/{jobId}
    end
    AI-)RS: ai/result/report (AiResult, JSON-Text)
    Note over RS: JSON lesen (unlesbar = Job FAILED)
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
| | `POST /api/reports/{id}/resend` | Versand anstoßen |
| training-service | `GET /api/fitness/athlete[?refresh]` | Schwellen + Zonen |
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

Details zu Feldern, QoS und Korrelation: [`asyncapi.yaml`](asyncapi.yaml). Ansehen z. B. im
[AsyncAPI Studio](https://studio.asyncapi.com/) (Datei hineinkopieren) oder mit der AsyncAPI-CLI:

```bash
npx @asyncapi/cli validate docs/asyncapi.yaml
```

**Pflege:** Wer ein Topic oder einen Vertrag in `cycling-messaging` ändert, passt `asyncapi.yaml` und diese Seite
im selben Commit an.
