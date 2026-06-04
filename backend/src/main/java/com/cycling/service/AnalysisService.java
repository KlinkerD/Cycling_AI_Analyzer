package com.cycling.service;

import com.cycling.dto.*;
import com.cycling.model.ActivityAnalysis;import com.cycling.repository.ActivityAnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final IntervalsService intervalsService;
    private final GeminiService geminiService;
    private final ZoneCalculatorService zoneCalculator;
    private final ActivityAnalysisRepository repository;
    private final ReportMailService reportMailService;
    private final PowerCurveService powerCurveService;

    public AnalysisService(IntervalsService intervalsService,
                           GeminiService geminiService,
                           ZoneCalculatorService zoneCalculator,
                           ActivityAnalysisRepository repository,
                           ReportMailService reportMailService,
                           PowerCurveService powerCurveService) {
        this.intervalsService = intervalsService;
        this.geminiService = geminiService;
        this.zoneCalculator = zoneCalculator;
        this.repository = repository;
        this.reportMailService = reportMailService;
        this.powerCurveService = powerCurveService;
    }

    /**
     * Analysiert eine Aktivität vollständig:
     * 1. Streams laden
     * 2. Zonen berechnen
     * 3. Gemini-Analyse
     * 4. Ergebnis speichern
     */
    public ReportDto analyzeActivity(String activityId, ActivityDto activityDto) {
        log.info("Starte Analyse für Aktivität: {} ({})", activityDto.name(), activityId);

        // Athleten-Schwellenwerte laden
        AthleteThresholdsDto thresholds = intervalsService.getAthleteThresholds();
        int ftp = thresholds.ftpOrDefault(zoneCalculator.getFtp());
        int maxHr = thresholds.maxHrOrDefault(zoneCalculator.getMaxHr());
        log.info("Verwende Schwellenwerte: FTP={} W, MaxHR={} bpm", ftp, maxHr);

        // Streams laden
        StreamDataDto streams = intervalsService.getStreams(activityId);

        // Zonen berechnen
        List<ZoneDistributionDto> powerZones = zoneCalculator.calculatePowerZones(streams.watts(), ftp);
        List<ZoneDistributionDto> hrZones = zoneCalculator.calculateHrZones(streams.heartrate(), maxHr);

        // Basismetriken aus Streams ableiten
        double avgPower = average(streams.watts().stream().mapToDouble(w -> w != null ? w : 0).toArray());
        double maxPower = max(streams.watts().stream().mapToDouble(w -> w != null ? w : 0).toArray());
        double avgHr = average(streams.heartrate().stream().mapToDouble(h -> h != null ? h : 0).toArray());
        double maxHrStream = max(streams.heartrate().stream().mapToDouble(h -> h != null ? h : 0).toArray());
        double avgCadence = average(streams.cadence().stream().mapToDouble(c -> c != null ? c : 0).toArray());

        // ActivityAnalysis Entity erstellen (noch ohne Gemini-Felder)
        ActivityAnalysis analysis = new ActivityAnalysis();
        analysis.setActivityId(activityId);
        analysis.setActivityName(activityDto.name());
        analysis.setActivityDate(parseDate(activityDto.startDateLocal()));
        analysis.setDistanceKm(activityDto.distance() != null ? activityDto.distance() / 1000.0 : 0);
        analysis.setDurationSeconds(activityDto.movingTime());
        analysis.setAvgPowerWatts(activityDto.averageWatts() != null ? activityDto.averageWatts() : avgPower);
        analysis.setMaxPowerWatts(activityDto.maxWatts() != null ? activityDto.maxWatts() : maxPower);
        analysis.setAvgHeartRate(activityDto.averageHeartrate() != null ? activityDto.averageHeartrate() : avgHr);
        analysis.setMaxHeartRate(activityDto.maxHeartrate() != null ? activityDto.maxHeartrate() : maxHrStream);
        analysis.setAvgSpeedKmh(activityDto.averageSpeed() != null ? activityDto.averageSpeed() * 3.6 : null);
        analysis.setElevationGainM(activityDto.totalElevationGain());
        analysis.setAvgCadenceRpm(avgCadence > 0 ? avgCadence : null);
        analysis.setAnalyzedAt(LocalDateTime.now());

        // Letzte 3 Analysen für Vergleich laden
        List<ActivityAnalysis> lastThree = repository.findTop3ByOrderByActivityDateDesc()
                .stream()
                .filter(a -> !a.getActivityId().equals(activityId))
                .limit(3)
                .collect(Collectors.toList());

        // Gemini Analyse
        GeminiService.AnalysisResult geminiResult = geminiService.analyzeWorkout(
                analysis, powerZones, hrZones, lastThree, ftp, maxHr);

        analysis.setOverallScore(geminiResult.score());
        analysis.setStrengths(geminiResult.strengths());
        analysis.setAnomalies(geminiResult.anomalies());
        analysis.setProgress(geminiResult.progress());
        analysis.setSummary(geminiResult.summary());

        // Power-Curve fuer aktuelle Aktivitaet laden + an Entity haengen (Cascade speichert Punkte mit)
        try {
            analysis.replacePowerCurve(powerCurveService.buildPointsForActivity(activityId));
        } catch (Exception e) {
            log.warn("Power-Curve fuer aktuelle Aktivitaet {} konnte nicht geladen werden: {}", activityId, e.getMessage());
        }

        // Speichern (upsert)
        repository.findByActivityId(activityId).ifPresent(existing -> analysis.setId(existing.getId()));
        ActivityAnalysis saved = repository.save(analysis);

        // Lazy Backfill: fuer die 3 vorherigen Aktivitaeten Power-Curve nachladen, falls noch nicht vorhanden
        powerCurveService.ensurePowerCurves(lastThree);

        // Trend-Daten
        List<TrendDataDto> trend = buildTrend();

        // Power-Curve-Set fuer Report (aktuell + vorherige + FTP-Reflinie)
        PowerCurveSetDto powerCurveSet = powerCurveService.buildReportSet(saved, lastThree, ftp);

        log.info("Analyse für {} abgeschlossen. Score: {}/10", activityDto.name(), geminiResult.score());
        ReportDto report = new ReportDto(saved, streams, powerZones, hrZones, trend, powerCurveSet);

        // Async Mail-Versand (fail-soft, blockt nicht).
        try {
            reportMailService.sendReportEmail(report);
        } catch (Exception mailEx) {
            log.warn("Mail-Trigger fehlgeschlagen: {}", mailEx.getMessage());
        }

        return report;
    }

    public List<String> getAnalyzedIds() {
        return repository.findAllActivityIds();
    }

    /**
     * Lädt einen gespeicherten Report aus der DB und erstellt Streams + Zonen neu.
     */
    public ReportDto getReport(String activityId) {
        ActivityAnalysis analysis = repository.findByActivityId(activityId)
                .orElseThrow(() -> new RuntimeException("Kein Report für Aktivität " + activityId + " gefunden. Bitte zuerst analysieren."));

        StreamDataDto streams = intervalsService.getStreams(activityId);

        AthleteThresholdsDto thresholds = intervalsService.getAthleteThresholds();
        int ftp = thresholds.ftpOrDefault(zoneCalculator.getFtp());
        int maxHr = thresholds.maxHrOrDefault(zoneCalculator.getMaxHr());

        List<ZoneDistributionDto> powerZones = zoneCalculator.calculatePowerZones(streams.watts(), ftp);
        List<ZoneDistributionDto> hrZones = zoneCalculator.calculateHrZones(streams.heartrate(), maxHr);
        List<TrendDataDto> trend = buildTrend();

        // Power-Curve sicherstellen (Backfill, falls aus alter Analyse-Version noch nicht vorhanden)
        powerCurveService.ensurePowerCurves(List.of(analysis));

        // Vorherige 3 fuer Vergleich laden + Power-Curves sicherstellen
        List<ActivityAnalysis> lastThree = repository.findTop3ByOrderByActivityDateDesc()
                .stream()
                .filter(a -> !a.getActivityId().equals(activityId))
                .limit(3)
                .collect(Collectors.toList());
        powerCurveService.ensurePowerCurves(lastThree);

        PowerCurveSetDto powerCurveSet = powerCurveService.buildReportSet(analysis, lastThree, ftp);

        return new ReportDto(analysis, streams, powerZones, hrZones, trend, powerCurveSet);
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

