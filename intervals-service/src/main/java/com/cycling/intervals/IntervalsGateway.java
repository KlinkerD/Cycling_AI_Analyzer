package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsErrorCode;
import com.cycling.intervals.api.IntervalsPowerCurve;
import com.cycling.intervals.api.IntervalsStreams;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Die einzelnen Abrufe bei intervals.icu, ohne Cache. */
public class IntervalsGateway {

    private static final Logger log = LoggerFactory.getLogger(IntervalsGateway.class);

    private final IntervalsHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public IntervalsGateway(IntervalsHttpClient http) {
        this.http = http;
    }

    /** {@code GET /athlete/{id}/activities?oldest&newest}, nur Rad-Aktivitaeten. */
    public List<IntervalsActivity> activities(LocalDate from, LocalDate to) {
        List<IntervalsActivity> rides = IntervalsParser.rideActivities(
                json(athletePath() + "/activities?oldest=" + from + "&newest=" + to));
        log.info("{} Rad-Aktivitaeten von {} bis {} geladen", rides.size(), from, to);
        return rides;
    }

    public IntervalsStreams streams(String activityId) {
        return IntervalsParser.streams(json(activityPath(activityId) + "/streams"));
    }

    /** {@code GET /athlete/{id}}: Schwellenwerte und Zonen. */
    public IntervalsAthleteZones athleteZones() {
        return IntervalsParser.athleteZones(json(athletePath()));
    }

    /** Zonenzeiten aus dem Aktivitaets-Detail; Namen/Farben aus {@code athleteZones}. */
    public IntervalsActivityZones activityZones(String activityId, IntervalsAthleteZones athleteZones) {
        return IntervalsParser.activityZones(json(activityPath(activityId)), athleteZones);
    }

    /**
     * Erst {@code /activity/{id}/power-curve}, bei Fehler oder leerer Antwort die {@code icu_pm_*}-Felder
     * des Aktivitaets-Details. Fehler beim zweiten Abruf werden geworfen.
     */
    public IntervalsPowerCurve powerCurve(String activityId) {
        try {
            IntervalsPowerCurve curve = IntervalsParser.powerCurve(json(activityPath(activityId) + "/power-curve"));
            if (!curve.points().isEmpty()) return curve;
        } catch (IntervalsApiException e) {
            if (e.code() == IntervalsErrorCode.INTERVALS_AUTH || e.code() == IntervalsErrorCode.INTERVALS_NOT_CONFIGURED) {
                throw e;
            }
            log.debug("Power-Curve-Endpoint fuer {} nicht nutzbar ({}), nehme Aktivitaets-Detail",
                    activityId, e.getMessage());
        }
        return IntervalsParser.powerCurveFromActivity(json(activityPath(activityId)));
    }

    /** {@code GET /athlete/{id}/wellness?oldest&newest}, aufsteigend nach Datum. */
    public List<IntervalsWellnessPoint> wellness(LocalDate from, LocalDate to) {
        return IntervalsParser.wellness(json(athletePath() + "/wellness?oldest=" + from + "&newest=" + to));
    }

    private String athletePath() {
        return "/athlete/" + UriUtils.encodePathSegment(http.athleteId(), StandardCharsets.UTF_8);
    }

    private static String activityPath(String activityId) {
        return "/activity/" + UriUtils.encodePathSegment(activityId, StandardCharsets.UTF_8);
    }

    private JsonNode json(String path) {
        String body = http.get(path);
        try {
            return body.isBlank() ? mapper.missingNode() : mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IntervalsApiException(IntervalsErrorCode.INTERVALS_UNAVAILABLE,
                    "intervals.icu lieferte kein gueltiges JSON fuer " + path, null, e);
        }
    }
}
