package com.cycling.service;

import com.cycling.dto.PowerCurveDto;
import com.cycling.dto.PowerCurveSetDto;
import com.cycling.dto.ReportDto;
import com.cycling.dto.StreamDataDto;
import com.cycling.dto.TrendDataDto;
import com.cycling.dto.ZoneDistributionDto;
import com.cycling.messaging.contract.ReportContent;
import com.cycling.model.ActivityAnalysis;

import java.util.List;

/** Wandelt den Report der REST-API in den MQTT-Vertrag fuer den report-service um. */
final class ReportContentMapper {

    private ReportContentMapper() {
    }

    static ReportContent toContent(ReportDto report) {
        return new ReportContent(
                toAnalysis(report.analysis()),
                toStreams(report.streams()),
                toZones(report.powerZones()),
                toZones(report.hrZones()),
                report.trend() == null ? List.of() : report.trend().stream().map(ReportContentMapper::toTrend).toList(),
                toPowerCurveSet(report.powerCurveSet()));
    }

    private static ReportContent.Analysis toAnalysis(ActivityAnalysis a) {
        return new ReportContent.Analysis(
                a.getActivityId(),
                a.getActivityName(),
                a.getActivityDate(),
                a.getDistanceKm(),
                a.getDurationSeconds(),
                a.getAvgPowerWatts(),
                a.getNormalizedPowerWatts(),
                a.getTrainingLoad(),
                a.getIntensityFactor(),
                a.getAvgHeartRate(),
                a.getAvgSpeedKmh(),
                a.getElevationGainM(),
                a.getAvgCadenceRpm(),
                a.getOverallScore(),
                a.getStrengths(),
                a.getAnomalies(),
                a.getProgress(),
                a.getSummary(),
                a.getAnalyzedAt());
    }

    private static ReportContent.Streams toStreams(StreamDataDto s) {
        if (s == null) return new ReportContent.Streams(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        return new ReportContent.Streams(s.distance(), s.watts(), s.heartrate(), s.velocity(), s.altitude(), s.time(), s.cadence());
    }

    private static List<ReportContent.Zone> toZones(List<ZoneDistributionDto> zones) {
        if (zones == null) return List.of();
        return zones.stream()
                .map(z -> new ReportContent.Zone(z.zoneName(), z.zoneLabel(), z.seconds(), z.percentage(), z.color()))
                .toList();
    }

    private static ReportContent.TrendPoint toTrend(TrendDataDto t) {
        return new ReportContent.TrendPoint(t.activityId(), t.activityName(), t.activityDate(),
                t.avgPowerWatts(), t.avgHeartRate(), t.distanceKm(), t.avgSpeedKmh(), t.overallScore());
    }

    private static ReportContent.PowerCurveSet toPowerCurveSet(PowerCurveSetDto set) {
        if (set == null) return null;
        List<ReportContent.PowerCurve> previous = set.previous() == null
                ? List.of()
                : set.previous().stream().map(ReportContentMapper::toPowerCurve).toList();
        return new ReportContent.PowerCurveSet(toPowerCurve(set.current()), previous, set.ftpWatts());
    }

    private static ReportContent.PowerCurve toPowerCurve(PowerCurveDto curve) {
        if (curve == null) return null;
        List<ReportContent.PowerCurvePoint> points = curve.points() == null
                ? List.of()
                : curve.points().stream()
                        .map(p -> new ReportContent.PowerCurvePoint(p.durationSeconds(), p.watts()))
                        .toList();
        return new ReportContent.PowerCurve(curve.activityId(), curve.activityName(), curve.activityDate(), points);
    }
}
