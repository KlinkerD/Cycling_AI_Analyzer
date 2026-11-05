package com.cycling.report.analysis;

import com.cycling.types.ActivityId;

import com.cycling.report.api.*;
import com.cycling.report.weather.RideWeatherPoint;
import com.cycling.report.weather.RideWeatherService;
import com.cycling.report.weather.WeatherSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final IntervalsData intervalsClient;
    private final ZoneCalculatorService zoneCalculator;
    private final ActivityAnalysisRepository repository;
    private final PowerCurveService powerCurveService;
    private final FtpResolver ftpResolver;
    private final RideWeatherService weather;
    private final ReportSnapshotStore snapshots;

    public AnalysisService(IntervalsData intervalsClient,
                           ZoneCalculatorService zoneCalculator,
                           ActivityAnalysisRepository repository,
                           PowerCurveService powerCurveService,
                           FtpResolver ftpResolver,
                           RideWeatherService weather,
                           ReportSnapshotStore snapshots) {
        this.ftpResolver = ftpResolver;
        this.weather = weather;
        this.snapshots = snapshots;
        this.intervalsClient = intervalsClient;
        this.zoneCalculator = zoneCalculator;
        this.repository = repository;
        this.powerCurveService = powerCurveService;
    }

    /**
     * Sammelt alles, was fuer die KI-Bewertung gebraucht wird (Streams, Zonen, FTP, Vergleichsfahrten)
     * und baut die Analyse ohne KI-Felder. Speichert noch nichts.
     */
    public PreparedAnalysis prepare(ActivityId activityId, ActivityDto activityDto) {
        log.info("Bereite Analyse vor für Aktivität: {} ({})", activityDto.name(), activityId);

        // Athleten-Schwellenwerte laden
        AthleteThresholdsDto thresholds = intervalsClient.getAthleteThresholds();
        int maxHr = thresholds.maxHrOrDefault(zoneCalculator.getMaxHr());

        // Streams laden
        StreamDataDto streams = intervalsClient.getStreams(activityId);

        // Zonen aus den von intervals.icu vorberechneten Zonen-Zeiten uebernehmen
        // (exakt wie auf der intervals.icu-Website; leere Liste -> Block wird im Report ausgeblendet)
        ActivityZonesDto zones = intervalsClient.getActivityZones(activityId);
        List<ZoneDistributionDto> powerZones = zones.powerZones();
        List<ZoneDistributionDto> hrZones = zones.hrZones();

        // Indoor-Kennung aus der geladenen Aktivitaet; schlug das Laden fehl, zaehlt der Typ
        boolean indoor = zones.indoor() != null
                ? zones.indoor()
                : FtpResolver.isIndoor(activityDto.type(), null);
        int ftp = ftpResolver.resolve(zones.icuFtp(), indoor, thresholds);
        log.info("Verwende Schwellenwerte: FTP={} W ({}), MaxHR={} bpm",
                ftp, indoor ? "indoor" : "outdoor", maxHr);

        // Basismetriken aus Streams ableiten
        double avgPower = average(streams.watts().stream().mapToDouble(w -> w != null ? w : 0).toArray());
        double maxPower = max(streams.watts().stream().mapToDouble(w -> w != null ? w : 0).toArray());
        double avgHr = average(streams.heartrate().stream().mapToDouble(h -> h != null ? h : 0).toArray());
        double maxHrStream = max(streams.heartrate().stream().mapToDouble(h -> h != null ? h : 0).toArray());
        double avgCadence = average(streams.cadence().stream().mapToDouble(c -> c != null ? c : 0).toArray());

        // ActivityAnalysis Entity erstellen (noch ohne KI-Felder)
        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setActivityId(activityId);
        analysis.setActivityName(activityDto.name());
        analysis.setActivityDate(activityDto.startDateLocal());
        analysis.setActivityType(activityDto.type());
        analysis.setStartDate(activityDto.startDate());
        analysis.setDistanceKm(activityDto.distance() != null ? activityDto.distance() / 1000.0 : 0);
        analysis.setDurationSeconds(activityDto.movingTime());
        analysis.setAvgPowerWatts(activityDto.averageWatts() != null ? activityDto.averageWatts() : avgPower);
        analysis.setMaxPowerWatts(activityDto.maxWatts() != null ? activityDto.maxWatts() : maxPower);
        analysis.setAvgHeartRate(activityDto.averageHeartrate() != null ? activityDto.averageHeartrate() : avgHr);
        analysis.setMaxHeartRate(activityDto.maxHeartrate() != null ? activityDto.maxHeartrate() : maxHrStream);
        analysis.setAvgSpeedKmh(activityDto.averageSpeed() != null ? activityDto.averageSpeed() * 3.6 : null);
        analysis.setElevationGainM(activityDto.totalElevationGain());
        analysis.setAvgCadenceRpm(avgCadence > 0 ? avgCadence : null);

        // NP aus intervals.icu bzw. Stream, IF / TSS mit der FTP der Fahrt
        enrichNormalizedPower(analysis, activityDto, streams);
        applyFtpContext(analysis, indoor, ftp);

        // Letzte 3 Analysen für Vergleich laden
        List<ActivityAnalysis> lastThree = repository.findTop3ByOrderByActivityDateDesc()
                .stream()
                .filter(a -> !a.getActivityId().equals(activityId))
                .limit(3)
                .collect(Collectors.toList());

        // Wetter nur fuer echte Outdoor-Fahrten: unbekannte Indoor-Kennung (zones.indoor() null) = kein Wetter
        List<RideWeatherPoint> rideWeather = weather.fetch(activityId, zones.indoor(), activityDto.type(),
                activityDto.startDate(), streams);
        Map<ActivityId, WeatherSummary> previousWeather = new LinkedHashMap<>();
        for (ActivityAnalysis prev : lastThree) {
            weather.summary(prev.getActivityId()).ifPresent(s -> previousWeather.put(prev.getActivityId(), s));
        }

        return new PreparedAnalysis(analysis, streams, powerZones, hrZones, lastThree, ftp, maxHr, rideWeather,
                previousWeather);
    }

    /**
     * Uebernimmt die KI-Bewertung, laedt die Power-Curve und speichert die Analyse (Upsert).
     * Liefert den fertigen Report.
     */
    public ReportDto complete(PreparedAnalysis prepared, AiAssessment aiResult) {
        ActivityAnalysis analysis = prepared.analysis();
        ActivityId activityId = analysis.getActivityId();

        analysis.setAnalyzedAt(LocalDateTime.now());
        analysis.setOverallScore(aiResult.score());
        analysis.setStrengths(aiResult.strengths());
        analysis.setAnomalies(aiResult.anomalies());
        analysis.setProgress(aiResult.progress());
        analysis.setSummary(aiResult.summary());

        // Power-Curve fuer aktuelle Aktivitaet laden + an Entity haengen (Cascade speichert Punkte mit)
        try {
            analysis.replacePowerCurve(powerCurveService.buildPointsForActivity(activityId));
        } catch (Exception e) {
            log.warn("Power-Curve fuer aktuelle Aktivitaet {} konnte nicht geladen werden: {}", activityId, e.getMessage());
        }

        // Speichern (upsert)
        repository.findByActivityId(activityId).ifPresent(existing -> analysis.setId(existing.getId()));
        ActivityAnalysis saved = repository.save(analysis);
        List<WeatherPointDto> weatherPoints = prepared.weather().stream().map(RideWeatherPoint::toDto).toList();
        try {
            weather.replace(activityId, prepared.weather());
        } catch (RuntimeException e) {
            log.warn("Wetter fuer {} konnte nicht gespeichert werden: {}", activityId, e.getMessage());
        }

        // Lazy Backfill: fuer die 3 vorherigen Aktivitaeten Power-Curve nachladen, falls noch nicht vorhanden
        powerCurveService.ensurePowerCurves(prepared.lastThree());

        List<TrendDataDto> trend = buildTrend();

        // Power-Curve-Set fuer Report (aktuell + vorherige + FTP-Reflinie)
        PowerCurveSetDto powerCurveSet = powerCurveService.buildReportSet(saved, prepared.lastThree(), prepared.ftp());

        log.info("Analyse für {} abgeschlossen. Score: {}/10", analysis.getActivityName(), aiResult.score());
        IntervalsData.ZoneBands bands = intervalsClient.getZoneBands(prepared.ftp());
        ReportData data = new ReportData(prepared.streams(), prepared.powerZones(), prepared.hrZones(),
                bands.power(), bands.hr());
        try {
            snapshots.save(activityId, data);
        } catch (RuntimeException e) {
            // Report bleibt nutzbar: ohne Snapshot wird er beim naechsten Oeffnen live geladen
            log.warn("Report-Daten fuer {} konnten nicht gespeichert werden: {}", activityId, e.getMessage());
        }
        return toReport(saved, data, trend, powerCurveSet, weatherPoints);
    }

    public List<ActivityId> getAnalyzedIds() {
        return repository.findAllActivityIds();
    }

    /**
     * Laedt einen gespeicherten Report aus der DB, ohne intervals-service. Nur Reports ohne gespeicherte
     * {@link ReportData} (Analysen aus alter Version) laden die Daten einmal live und speichern sie dann.
     */
    public ReportDto getReport(ActivityId activityId) {
        ActivityAnalysis analysis = findAnalysis(activityId);
        ReportData data = snapshots.load(activityId).orElseGet(() -> {
            log.info("Report {} ohne gespeicherte Daten - einmalig live laden", activityId);
            return fetchAndStore(analysis);
        });
        return buildReport(analysis, data);
    }

    /**
     * "Daten neu laden": holt Streams, Zonen, Kartenzonen, Power-Curve und Wetter frisch und speichert sie.
     * Die KI-Bewertung und die Kennzahlen der Analyse bleiben unveraendert. Scheitert der intervals-service,
     * bleibt alles wie es war ({@link com.cycling.intervals.client.IntervalsUnavailableException}).
     * Liefert der weather-service nichts, bleibt das bisherige Wetter stehen.
     */
    public ReportDto refresh(ActivityId activityId) {
        ActivityAnalysis analysis = findAnalysis(activityId);
        fillActivityInfo(analysis);
        ReportData data = fetchAndStore(analysis);

        List<PowerCurvePoint> curve = powerCurveService.buildPointsForActivity(activityId);
        if (curve != null && !curve.isEmpty()) {
            analysis.replacePowerCurve(curve);
        }
        repository.save(analysis);

        List<RideWeatherPoint> rideWeather = weather.fetch(activityId, analysis.getIndoor(),
                analysis.getActivityType(), analysis.getStartDate(), data.streams());
        if (!rideWeather.isEmpty()) {
            weather.replace(activityId, rideWeather);
        } else {
            log.info("Kein neues Wetter fuer {} - bisheriges bleibt", activityId);
        }
        log.info("Report-Daten fuer {} neu geladen", activityId);
        return buildReport(analysis, data);
    }

    private ActivityAnalysis findAnalysis(ActivityId activityId) {
        return repository.findByActivityId(activityId).orElseThrow(() -> new ReportNotFoundException(activityId));
    }

    /** Typ und UTC-Startzeit fuer das Wetter nachtragen (Analysen aus alter Version); fail-soft. */
    private void fillActivityInfo(ActivityAnalysis analysis) {
        if (analysis.getActivityType() != null && analysis.getStartDate() != null) return;
        LocalDate day = analysis.getActivityDate() != null ? analysis.getActivityDate().toLocalDate() : null;
        intervalsClient.findActivity(analysis.getActivityId(), day).ifPresent(a -> {
            if (analysis.getActivityType() == null) analysis.setActivityType(a.type());
            if (analysis.getStartDate() == null) analysis.setStartDate(a.startDate());
        });
    }

    /**
     * Laedt die Report-Daten live und speichert sie. Streams, Zonen und Kartenzonen werfen bei Fehlern, damit
     * keine leeren Daten gespeichert werden. Traegt bei Analysen aus alter Version Indoor-Kennung und FTP nach.
     */
    private ReportData fetchAndStore(ActivityAnalysis analysis) {
        ActivityId activityId = analysis.getActivityId();
        StreamDataDto streams = intervalsClient.getStreams(activityId);
        ActivityZonesDto zones = intervalsClient.getActivityZonesOrThrow(activityId);

        // Analysen aus alter Version: Indoor-Kennung + FTP nachtragen, IF/TSS damit neu rechnen
        if (analysis.getFtpUsed() == null) {
            AthleteThresholdsDto thresholds = intervalsClient.getAthleteThresholds();
            boolean indoor = Boolean.TRUE.equals(zones.indoor());
            int ftp = ftpResolver.resolve(zones.icuFtp(), indoor, thresholds);
            enrichNormalizedPower(analysis, null, streams);
            applyFtpContext(analysis, indoor, ftp);
            repository.save(analysis);
            log.info("Analyse {} nachgetragen: indoor={}, FTP={} W", activityId, indoor, ftp);
        }

        IntervalsData.ZoneBands bands = intervalsClient.getZoneBandsOrThrow(analysis.getFtpUsed());
        ReportData data = new ReportData(streams, zones.powerZones(), zones.hrZones(), bands.power(), bands.hr());
        snapshots.save(activityId, data);
        return data;
    }

    private ReportDto buildReport(ActivityAnalysis analysis, ReportData data) {
        ActivityId activityId = analysis.getActivityId();

        // Power-Curve sicherstellen (Backfill, falls aus alter Analyse-Version noch nicht vorhanden)
        powerCurveService.ensurePowerCurves(List.of(analysis));

        // Vorherige 3 fuer Vergleich laden + Power-Curves sicherstellen
        List<ActivityAnalysis> lastThree = repository.findTop3ByOrderByActivityDateDesc()
                .stream()
                .filter(a -> !a.getActivityId().equals(activityId))
                .limit(3)
                .collect(Collectors.toList());
        powerCurveService.ensurePowerCurves(lastThree);

        PowerCurveSetDto powerCurveSet = powerCurveService.buildReportSet(analysis, lastThree, analysis.getFtpUsed());
        return toReport(analysis, data, buildTrend(), powerCurveSet, weather.load(activityId));
    }

    private static ReportDto toReport(ActivityAnalysis analysis, ReportData data, List<TrendDataDto> trend,
                                      PowerCurveSetDto powerCurveSet, List<WeatherPointDto> weatherPoints) {
        return new ReportDto(AnalysisDto.from(analysis), data.streams(), data.powerZones(), data.hrZones(), trend,
                powerCurveSet, data.powerBands(), data.hrBands(), weatherPoints);
    }

    private List<TrendDataDto> buildTrend() {
        return repository.findTop10ByOrderByActivityDateDesc().stream()
                .map(a -> new TrendDataDto(
                        a.getActivityId(),
                        a.getActivityName(),
                        a.getActivityDate(),
                        a.getAvgPowerWatts(),
                        a.getAvgHeartRate(),
                        a.getDistanceKm(),
                        a.getAvgSpeedKmh(),
                        a.getOverallScore()
                ))
                .collect(Collectors.toList());
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null) return null;
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateStr.replace(" ", "T"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e2) {
                log.warn("Datum konnte nicht geparst werden: {}", dateStr);
                return null;
            }
        }
    }

    /**
     * Setzt die Normalized Power auf der Analyse (IF/TSS rechnet danach {@link #applyFtpContext}).
     * No-op, wenn die NP bereits gesetzt ist (idempotent) oder keine Leistungsdaten vorliegen.
     * NP wird bevorzugt aus intervals.icu uebernommen, sonst aus dem Watt-Stream berechnet.
     */
    private void enrichNormalizedPower(ActivityAnalysis analysis, ActivityDto dto, StreamDataDto streams) {
        if (analysis.getNormalizedPowerWatts() != null) return;

        Double np = (dto != null && dto.normalizedPower() != null && dto.normalizedPower() > 0)
                ? dto.normalizedPower()
                : computeNormalizedPower(streams != null ? streams.watts() : null);
        if (np == null || np <= 0) return;

        analysis.setNormalizedPowerWatts(Math.round(np * 10.0) / 10.0);
    }

    /**
     * Haengt Indoor-Kennung und verwendete FTP an die Analyse und berechnet IF/TSS aus der
     * gespeicherten NP mit dieser FTP neu. Ohne NP bleiben IF/TSS unveraendert.
     */
    static void applyFtpContext(ActivityAnalysis analysis, boolean indoor, int ftp) {
        analysis.setIndoor(indoor);
        analysis.setFtpUsed(ftp);
        Double np = analysis.getNormalizedPowerWatts();
        if (np != null && np > 0) {
            setIntensityAndTss(analysis, np, ftp);
        }
    }

    private static void setIntensityAndTss(ActivityAnalysis analysis, double np, int ftp) {
        if (ftp <= 0) return;
        double intensityFactor = np / ftp;
        analysis.setIntensityFactor(Math.round(intensityFactor * 100.0) / 100.0);

        Integer durationSec = analysis.getDurationSeconds();
        if (durationSec != null && durationSec > 0) {
            double tss = (durationSec * np * intensityFactor) / (ftp * 3600.0) * 100.0;
            analysis.setTrainingLoad((double) Math.round(tss));
        }
    }

    /**
     * Normalized Power nach Coggan: 30s-gleitender Mittelwert der Leistung, jeweils hoch 4,
     * davon der Mittelwert, dann die 4. Wurzel. Erwartet 1s-Sampling (intervals.icu-Standard).
     */
    private Double computeNormalizedPower(List<Integer> watts) {
        if (watts == null || watts.size() < 30) return null;
        final int window = 30;
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>(window);
        double rollingSum = 0;
        double sumPow4 = 0;
        int count = 0;
        for (Integer raw : watts) {
            int p = raw != null ? raw : 0;
            q.addLast(p);
            rollingSum += p;
            if (q.size() > window) rollingSum -= q.removeFirst();
            if (q.size() == window) {
                double avg = rollingSum / window;
                sumPow4 += avg * avg * avg * avg;
                count++;
            }
        }
        if (count == 0) return null;
        double np = Math.pow(sumPow4 / count, 0.25);
        return np > 0 ? np : null;
    }

    private double average(double[] values) {
        if (values.length == 0) return 0;
        OptionalDouble avg = java.util.Arrays.stream(values).filter(v -> v > 0).average();
        return avg.orElse(0);
    }

    private double max(double[] values) {
        if (values.length == 0) return 0;
        OptionalDouble max = java.util.Arrays.stream(values).max();
        return max.orElse(0);
    }
}

