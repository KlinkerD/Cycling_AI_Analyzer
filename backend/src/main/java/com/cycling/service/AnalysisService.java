package com.cycling.service;

import com.cycling.dto.*;
import com.cycling.messaging.contract.AnalysisResult;
import com.cycling.model.ActivityAnalysis;
import com.cycling.repository.ActivityAnalysisRepository;
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
    private final ZoneCalculatorService zoneCalculator;
    private final ActivityAnalysisRepository repository;
    private final PowerCurveService powerCurveService;
    private final FtpResolver ftpResolver;

    public AnalysisService(IntervalsService intervalsService,
                           ZoneCalculatorService zoneCalculator,
                           ActivityAnalysisRepository repository,
                           PowerCurveService powerCurveService,
                           FtpResolver ftpResolver) {
        this.ftpResolver = ftpResolver;
        this.intervalsService = intervalsService;
        this.zoneCalculator = zoneCalculator;
        this.repository = repository;
        this.powerCurveService = powerCurveService;
    }

    /**
     * Sammelt alles, was fuer die KI-Bewertung gebraucht wird (Streams, Zonen, FTP, Vergleichsfahrten)
     * und baut die Analyse ohne KI-Felder. Speichert noch nichts.
     */
    public PreparedAnalysis prepare(String activityId, ActivityDto activityDto) {
        log.info("Bereite Analyse vor für Aktivität: {} ({})", activityDto.name(), activityId);

        // Athleten-Schwellenwerte laden
        AthleteThresholdsDto thresholds = intervalsService.getAthleteThresholds();
        int maxHr = thresholds.maxHrOrDefault(zoneCalculator.getMaxHr());

        // Streams laden
        StreamDataDto streams = intervalsService.getStreams(activityId);

        // Zonen aus den von intervals.icu vorberechneten Zonen-Zeiten uebernehmen
        // (exakt wie auf der intervals.icu-Website; leere Liste -> Block wird im Report ausgeblendet)
        ActivityZonesDto zones = intervalsService.getActivityZones(activityId);
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

        // NP aus intervals.icu bzw. Stream, IF / TSS mit der FTP der Fahrt
        enrichNormalizedPower(analysis, activityDto, streams);
        applyFtpContext(analysis, indoor, ftp);

        // Letzte 3 Analysen für Vergleich laden
        List<ActivityAnalysis> lastThree = repository.findTop3ByOrderByActivityDateDesc()
                .stream()
                .filter(a -> !a.getActivityId().equals(activityId))
                .limit(3)
                .collect(Collectors.toList());

        return new PreparedAnalysis(analysis, streams, powerZones, hrZones, lastThree, ftp, maxHr);
    }

    /**
     * Uebernimmt die KI-Bewertung, laedt die Power-Curve und speichert die Analyse (Upsert).
     * Liefert den fertigen Report.
     */
    public ReportDto complete(PreparedAnalysis prepared, AnalysisResult aiResult) {
        ActivityAnalysis analysis = prepared.analysis();
        String activityId = analysis.getActivityId();

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

        // Lazy Backfill: fuer die 3 vorherigen Aktivitaeten Power-Curve nachladen, falls noch nicht vorhanden
        powerCurveService.ensurePowerCurves(prepared.lastThree());

        List<TrendDataDto> trend = buildTrend();

        // Power-Curve-Set fuer Report (aktuell + vorherige + FTP-Reflinie)
        PowerCurveSetDto powerCurveSet = powerCurveService.buildReportSet(saved, prepared.lastThree(), prepared.ftp());

        log.info("Analyse für {} abgeschlossen. Score: {}/10", analysis.getActivityName(), aiResult.score());
        return new ReportDto(saved, prepared.streams(), prepared.powerZones(), prepared.hrZones(), trend, powerCurveSet);
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

        ActivityZonesDto zones = intervalsService.getActivityZones(activityId);
        List<ZoneDistributionDto> powerZones = zones.powerZones();
        List<ZoneDistributionDto> hrZones = zones.hrZones();
        List<TrendDataDto> trend = buildTrend();

        // Analysen aus alter Version: Indoor-Kennung + FTP nachtragen, IF/TSS damit neu rechnen
        if (analysis.getFtpUsed() == null) {
            boolean indoor = Boolean.TRUE.equals(zones.indoor());
            int ftp = ftpResolver.resolve(zones.icuFtp(), indoor, thresholds);
            enrichNormalizedPower(analysis, null, streams);
            applyFtpContext(analysis, indoor, ftp);
            repository.save(analysis);
            log.info("Analyse {} nachgetragen: indoor={}, FTP={} W", activityId, indoor, ftp);
        }
        int ftp = analysis.getFtpUsed();

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

