package com.cycling.intervals.client;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsPowerCurve;
import com.cycling.intervals.api.IntervalsStreams;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** JSON wie es der intervals-service liefert. */
class IntervalsClientTest {

    private static final String BASE = "http://intervals-service";
    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_2 = LocalDate.of(2026, 11, 2);
    private static final LocalDate NOV_3 = LocalDate.of(2026, 11, 3);

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final IntervalsClient client = new IntervalsClient(builder.build());

    private static String problem(String code, String detail) {
        return "{\"type\":\"about:blank\",\"status\":502,\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}";
    }

    @Test
    void readsActivities() {
        server.expect(requestTo(BASE + "/api/intervals/activities?from=2026-11-01&to=2026-11-03"))
                .andRespond(withSuccess("""
                        [{"id":"i1","name":"Runde","startDateLocal":"2026-11-01T08:30:00","type":"Ride",
                          "distance":42000.0,"movingTime":5400,"elapsedTime":5600,"averageWatts":180.0,
                          "normalizedPower":195.0,"maxWatts":600.0,"averageHeartrate":140.0,"maxHeartrate":175.0,
                          "averageSpeed":7.8,"totalElevationGain":350.0}]""", MediaType.APPLICATION_JSON));

        List<IntervalsActivity> activities = client.activities(NOV_1, NOV_3);

        assertThat(activities).hasSize(1);
        IntervalsActivity a = activities.getFirst();
        assertThat(a.id()).isEqualTo("i1");
        assertThat(a.normalizedPower()).isEqualTo(195.0);
        assertThat(a.movingTime()).isEqualTo(5400);
    }

    @Test
    void readsStreamsAndWellness() {
        server.expect(requestTo(BASE + "/api/intervals/activities/i1/streams"))
                .andRespond(withSuccess("""
                        {"distance":[0.0,10.0],"watts":[150,null],"heartrate":[120,121],"velocity":[8.0,8.1],
                         "altitude":[],"time":[0,1],"cadence":null}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/intervals/wellness?from=2026-11-02&to=2026-11-03"))
                .andRespond(withSuccess("""
                        [{"date":"2026-11-03","ctl":55.0,"atl":60.0,"form":-5.0,"rampRate":3.2}]""",
                        MediaType.APPLICATION_JSON));

        IntervalsStreams streams = client.streams("i1");
        List<IntervalsWellnessPoint> wellness = client.wellness(NOV_2, NOV_3);

        assertThat(streams.watts()).containsExactly(150, null);
        assertThat(wellness).containsExactly(new IntervalsWellnessPoint("2026-11-03", 55.0, 60.0, -5.0, 3.2));
    }

    @Test
    void readsZonesAndPowerCurve() {
        server.expect(requestTo(BASE + "/api/intervals/athlete/zones"))
                .andRespond(withSuccess("""
                        {"thresholds":{"name":"Dan","ftp":250,"indoorFtp":235,"lthr":170,"maxHr":190,"weight":72.5},
                         "powerZones":[{"index":2,"name":"Endurance","lowerPct":0.55,"upperPct":0.75,
                                        "lowerValue":138,"upperValue":188,"color":"#42a5f5"}],
                         "hrZones":[],"hrReference":"lthr"}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/intervals/activities/i1/zones"))
                .andRespond(withSuccess("""
                        {"powerZones":[{"zoneName":"Z2","zoneLabel":"Endurance","seconds":1800,"percentage":75.0,
                                        "color":"#42a5f5"}],"hrZones":[],"indoor":true,"icuFtp":235}""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/intervals/activities/i1/power-curve"))
                .andRespond(withSuccess("""
                        {"points":[{"seconds":5,"watts":600},{"seconds":60,"watts":300}]}""",
                        MediaType.APPLICATION_JSON));

        IntervalsAthleteZones zones = client.athleteZonesOrEmpty();
        IntervalsActivityZones activityZones = client.activityZonesIfAvailable("i1").orElseThrow();
        IntervalsPowerCurve curve = client.powerCurveOrEmpty("i1");

        assertThat(zones.thresholds().indoorFtp()).isEqualTo(235);
        assertThat(zones.powerZones().getFirst().upperValue()).isEqualTo(188);
        assertThat(activityZones.indoor()).isTrue();
        assertThat(activityZones.icuFtp()).isEqualTo(235);
        assertThat(curve.points()).containsExactly(new IntervalsPowerCurve.Point(5, 600),
                new IntervalsPowerCurve.Point(60, 300));
    }

    @Test
    void errorBecomesIntervalsUnavailableWithCode() {
        server.expect(requestTo(BASE + "/api/intervals/activities?from=2026-11-01&to=2026-11-03"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problem("INTERVALS_AUTH", "API-Key pruefen")));

        assertThatThrownBy(() -> client.activities(NOV_1, NOV_3))
                .isInstanceOfSatisfying(IntervalsUnavailableException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.code()).isEqualTo("INTERVALS_AUTH");
                    assertThat(e.getMessage()).isEqualTo("API-Key pruefen");
                });
    }

    @Test
    void rateLimitKeepsStatusAndRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "30");
        server.expect(requestTo(BASE + "/api/intervals/activities/i1/streams"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).headers(headers)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problem("INTERVALS_RATE_LIMIT", "Rate-Limit")));

        assertThatThrownBy(() -> client.streams("i1"))
                .isInstanceOfSatisfying(IntervalsUnavailableException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.code()).isEqualTo("INTERVALS_RATE_LIMIT");
                    assertThat(e.retryAfterSeconds()).isEqualTo(30L);
                });
    }

    @Test
    void unreachableServiceIsServiceUnavailable() {
        server.expect(requestTo(BASE + "/api/intervals/wellness?from=2026-11-02&to=2026-11-03"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> client.wellness(NOV_2, NOV_3))
                .isInstanceOfSatisfying(IntervalsUnavailableException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.code()).isEqualTo(IntervalsUnavailableException.SERVICE_UNREACHABLE);
                });
    }

    @Test
    void failSoftVariantsReturnEmptyValues() {
        server.expect(requestTo(BASE + "/api/intervals/athlete/thresholds"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(BASE + "/api/intervals/athlete/zones"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(BASE + "/api/intervals/activities/i1/zones"))
                .andRespond(withException(new IOException("down")));
        server.expect(requestTo(BASE + "/api/intervals/activities/i1/power-curve"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE + "/api/intervals/cache/evict"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        IntervalsAthleteThresholds thresholds = client.athleteThresholdsOrEmpty();
        IntervalsAthleteZones zones = client.athleteZonesOrEmpty();

        assertThat(thresholds.ftp()).isNull();
        assertThat(zones.powerZones()).isEmpty();
        assertThat(zones.hrReference()).isEqualTo("lthr");
        assertThat(client.activityZonesIfAvailable("i1")).isEmpty();
        assertThat(client.powerCurveOrEmpty("i1").points()).isEmpty();
        client.evictCachesQuietly();
        server.verify();
    }

    @Test
    void updateThresholdsPutsUpdateAndReadsFreshValues() {
        server.expect(requestTo(BASE + "/api/intervals/athlete/thresholds"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .json("{\"ftp\":280,\"indoorFtp\":null,\"lthr\":168,\"maxHr\":null,\"weight\":null}"))
                .andRespond(withSuccess("""
                        {"name":"Dan","ftp":280,"indoorFtp":235,"lthr":168,"maxHr":190,"weight":72.5}""",
                        MediaType.APPLICATION_JSON));

        IntervalsAthleteThresholds fresh = client.updateThresholds(
                new com.cycling.intervals.api.IntervalsThresholdsUpdate(280, null, 168, null, null));

        assertThat(fresh.ftp()).isEqualTo(280);
        assertThat(fresh.lthr()).isEqualTo(168);
        server.verify();
    }

    @Test
    void badRequestFromServiceStaysBadRequest() {
        server.expect(requestTo(BASE + "/api/intervals/athlete/thresholds"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"detail\":\"LTHR muss unter HFmax liegen.\",\"code\":\"BAD_REQUEST\"}"));

        assertThatThrownBy(() -> client.updateThresholds(
                new com.cycling.intervals.api.IntervalsThresholdsUpdate(null, null, 190, 185, null)))
                .isInstanceOfSatisfying(IntervalsUnavailableException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.code()).isEqualTo("BAD_REQUEST");
                    assertThat(e.getMessage()).isEqualTo("LTHR muss unter HFmax liegen.");
                });
    }

    @Test
    void evictPostsToService() {
        server.expect(requestTo(BASE + "/api/intervals/cache/evict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withNoContent());

        client.evictCaches();

        server.verify();
    }
}
