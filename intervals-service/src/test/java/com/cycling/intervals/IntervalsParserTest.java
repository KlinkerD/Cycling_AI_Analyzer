package com.cycling.intervals;

import com.cycling.intervals.api.IntervalsActivity;
import com.cycling.intervals.api.IntervalsActivityZones;
import com.cycling.intervals.api.IntervalsAthleteThresholds;
import com.cycling.intervals.api.IntervalsAthleteZones;
import com.cycling.intervals.api.IntervalsPowerCurve;
import com.cycling.intervals.api.IntervalsStreams;
import com.cycling.intervals.api.IntervalsWellnessPoint;
import com.cycling.intervals.api.IntervalsZone;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Beispiel-JSON im Format der intervals.icu-API. */
class IntervalsParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    private static final String ATHLETE = """
            {"name":"Dan","icu_weight":72.5,"sportSettings":[
              {"types":["Run"],"ftp":null},
              {"types":["Ride","VirtualRide"],"ftp":250,"indoor_ftp":235,"lthr":170,"max_hr":190,
               "power_zones":[55,75,90,105,120,150,999],
               "power_zone_names":["Recovery","Endurance","Tempo","Threshold","VO2 Max","Anaerobic","Neuromuscular"],
               "hr_zones":[140,155,162,173,178,183,190],
               "hr_zone_names":["Z1","Z2","Z3","Z4","Z5a","Z5b","Z5c"],
               "hr_zones_max_hr":false}
            ]}""";

    // ---- Aktivitaeten -------------------------------------------------------------------------

    @Test
    void keepsOnlyRidesAndPrefersIcuValues() throws Exception {
        JsonNode root = json("""
                [
                  {"id":"i1","name":"Morgenrunde","type":"Ride","start_date_local":"2026-11-01T08:30:00",
                   "distance":42000.0,"moving_time":5400,"elapsed_time":5600,
                   "average_watts":170.0,"icu_average_watts":180.0,"icu_weighted_avg_watts":195.0,
                   "max_watts":600,"average_heartrate":139,"icu_average_heartrate":140.0,
                   "average_speed":7.7,"total_elevation_gain":340.0,"icu_elevation_gain":350.0},
                  {"id":"i2","name":"Lauf","type":"Run"},
                  {"id":"i3","name":"Zwift","type":"VirtualRide","average_watts":200.0}
                ]""");

        List<IntervalsActivity> rides = IntervalsParser.rideActivities(root);

        assertThat(rides).extracting(IntervalsActivity::id).containsExactly("i1", "i3");
        IntervalsActivity ride = rides.getFirst();
        assertThat(ride.startDateLocal()).isEqualTo("2026-11-01T08:30:00");
        assertThat(ride.movingTime()).isEqualTo(5400);
        assertThat(ride.averageWatts()).isEqualTo(180.0);
        assertThat(ride.normalizedPower()).isEqualTo(195.0);
        assertThat(ride.maxWatts()).isEqualTo(600.0);
        assertThat(ride.averageHeartrate()).isEqualTo(140.0);
        assertThat(ride.averageSpeed()).isEqualTo(7.7);
        assertThat(ride.totalElevationGain()).isEqualTo(350.0);
        assertThat(rides.get(1).averageWatts()).isEqualTo(200.0);
        assertThat(rides.get(1).normalizedPower()).isNull();
    }

    @Test
    void averageWattsFallsBackToNormalizedPower() throws Exception {
        IntervalsActivity ride = IntervalsParser.activity(json("""
                {"id":"i1","type":"Ride","icu_weighted_avg_watts":195.0}"""));

        assertThat(ride.averageWatts()).isEqualTo(195.0);
    }

    // ---- Streams -------------------------------------------------------------------------------

    @Test
    void readsStreamArrayWithAliasesAndNulls() throws Exception {
        IntervalsStreams streams = IntervalsParser.streams(json("""
                [{"type":"watts","data":[150,null,160]},
                 {"type":"heartrate","data":[120,121,122]},
                 {"type":"velocity_smooth","data":[8.0,8.1,8.2]},
                 {"type":"time","data":[0,1,2]}]"""));

        assertThat(streams.watts()).containsExactly(150, null, 160);
        assertThat(streams.velocity()).containsExactly(8.0, 8.1, 8.2);
        assertThat(streams.distance()).isEmpty();
        assertThat(streams.cadence()).isEmpty();
    }

    @Test
    void readsStreamObjectFormat() throws Exception {
        IntervalsStreams streams = IntervalsParser.streams(json("""
                {"power":[100,110],"rpm":[85,90],"altitude":[100.5,101.0]}"""));

        assertThat(streams.watts()).containsExactly(100, 110);
        assertThat(streams.cadence()).containsExactly(85, 90);
        assertThat(streams.altitude()).containsExactly(100.5, 101.0);
    }

    // ---- Wellness ------------------------------------------------------------------------------

    @Test
    void wellnessIsSortedAndCarriesFormAndRamp() throws Exception {
        List<IntervalsWellnessPoint> points = IntervalsParser.wellness(json("""
                [{"id":"2026-11-03","ctl":55.0,"atl":60.5,"rampRate":3.2},
                 {"id":"2026-11-02","ctl":54.5,"atl":null,"rampRate":null},
                 {"ctl":1.0}]"""));

        assertThat(points).extracting(IntervalsWellnessPoint::date).containsExactly("2026-11-02", "2026-11-03");
        IntervalsWellnessPoint today = points.get(1);
        assertThat(today.form()).isEqualTo(-5.5);
        assertThat(today.rampRate()).isEqualTo(3.2);
        assertThat(points.getFirst().form()).isNull();
    }

    // ---- Athlet ------------------------------------------------------------------------------

    @Test
    void readsOutdoorAndIndoorFtpFromRideSettings() throws Exception {
        IntervalsAthleteThresholds t = IntervalsParser.thresholds(json(ATHLETE));

        assertThat(t.name()).isEqualTo("Dan");
        assertThat(t.ftp()).isEqualTo(250);
        assertThat(t.indoorFtp()).isEqualTo(235);
        assertThat(t.lthr()).isEqualTo(170);
        assertThat(t.maxHr()).isEqualTo(190);
        assertThat(t.weight()).isEqualTo(72.5);
    }

    @Test
    void missingOrNullValuesAreNull() throws Exception {
        IntervalsAthleteThresholds t = IntervalsParser.thresholds(json("""
                {"name":"Dan","sportSettings":[{"types":["Ride"],"ftp":250,"indoor_ftp":null}]}"""));

        assertThat(t.ftp()).isEqualTo(250);
        assertThat(t.indoorFtp()).isNull();
        assertThat(t.lthr()).isNull();
        assertThat(t.weight()).isNull();
    }

    @Test
    void buildsPowerZonesWithSweetSpotAndHrZonesFromBpm() throws Exception {
        IntervalsAthleteZones zones = IntervalsParser.athleteZones(json(ATHLETE));

        assertThat(zones.powerZones()).hasSize(8);
        IntervalsZone z2 = zones.powerZones().get(1);
        assertThat(z2.name()).isEqualTo("Endurance");
        assertThat(z2.lowerValue()).isEqualTo(138);
        assertThat(z2.upperValue()).isEqualTo(188);
        assertThat(zones.powerZones().get(6).upperValue()).isNull();
        assertThat(zones.powerZones().get(7).name()).isEqualTo("Sweet Spot");

        assertThat(zones.hrReference()).isEqualTo("lthr");
        assertThat(zones.hrZones()).hasSize(7);
        assertThat(zones.hrZones().get(1).lowerValue()).isEqualTo(141);
        assertThat(zones.hrZones().get(1).upperValue()).isEqualTo(155);
        assertThat(zones.hrZones().get(6).upperValue()).isNull();
    }

    @Test
    void noZonesWithoutFtpOrReference() throws Exception {
        IntervalsAthleteZones zones = IntervalsParser.athleteZones(json("""
                {"sportSettings":[{"types":["Ride"],"power_zones":[55,75],"hr_zones":[140]}]}"""));

        assertThat(zones.powerZones()).isEmpty();
        assertThat(zones.hrZones()).isEmpty();
    }

    // ---- Zonenzeiten ---------------------------------------------------------------------------

    @Test
    void activityZonesDropSweetSpotAndUseAthleteNames() throws Exception {
        IntervalsAthleteZones athlete = IntervalsParser.athleteZones(json(ATHLETE));
        JsonNode activity = json("""
                {"id":"i1","type":"VirtualRide","trainer":true,"icu_ftp":235,
                 "icu_zone_times":[{"id":"Z1","secs":600},{"id":"Z2","secs":1800},{"id":"SS","secs":900}],
                 "icu_hr_zone_times":[300,900]}""");

        IntervalsActivityZones zones = IntervalsParser.activityZones(activity, athlete);

        assertThat(zones.powerZones()).extracting(z -> z.zoneName()).containsExactly("Z1", "Z2");
        assertThat(zones.powerZones().get(1).zoneLabel()).isEqualTo("Endurance");
        assertThat(zones.powerZones().get(1).percentage()).isEqualTo(75.0);
        assertThat(zones.hrZones().get(0).percentage()).isEqualTo(25.0);
        assertThat(zones.indoor()).isTrue();
        assertThat(zones.icuFtp()).isEqualTo(235);
    }

    @Test
    void outdoorRideWithoutZoneTimes() throws Exception {
        IntervalsActivityZones zones = IntervalsParser.activityZones(json("""
                {"id":"i2","type":"Ride"}"""), IntervalsParser.athleteZones(json(ATHLETE)));

        assertThat(zones.powerZones()).isEmpty();
        assertThat(zones.hrZones()).isEmpty();
        assertThat(zones.indoor()).isFalse();
        assertThat(zones.icuFtp()).isNull();
    }

    @Test
    void rideOnTrainerIsIndoor() throws Exception {
        IntervalsActivityZones zones = IntervalsParser.activityZones(json("""
                {"id":"i3","type":"Ride","trainer":true}"""), IntervalsParser.athleteZones(json("{}")));

        assertThat(zones.indoor()).isTrue();
    }

    // ---- Power-Curve -------------------------------------------------------------------------

    @Test
    void powerCurveFromArrayParallelArraysAndMap() throws Exception {
        assertThat(IntervalsParser.powerCurve(json("""
                [{"secs":60,"watts":300},{"secs":5,"value":600},{"secs":0,"watts":1}]""")).points())
                .containsExactly(new IntervalsPowerCurve.Point(5, 600), new IntervalsPowerCurve.Point(60, 300));
        assertThat(IntervalsParser.powerCurve(json("""
                {"secs":[5,60],"values":[600,300]}""")).points()).hasSize(2);
        assertThat(IntervalsParser.powerCurve(json("""
                {"5":600,"60":300,"name":"x"}""")).points()).hasSize(2);
    }

    @Test
    void powerCurveFromActivityDetail() throws Exception {
        IntervalsPowerCurve curve = IntervalsParser.powerCurveFromActivity(json("""
                {"icu_pm_p_5":610,"icu_pm_p_60":0,"icu_pm":{"60":305,"5":999}}"""));

        assertThat(curve.points())
                .containsExactly(new IntervalsPowerCurve.Point(5, 610), new IntervalsPowerCurve.Point(60, 305));
    }
}
