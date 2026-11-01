package com.cycling.service;

import com.cycling.dto.ActivityZonesDto;
import com.cycling.dto.AthleteThresholdsDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalsServiceParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    @Test
    void readsOutdoorAndIndoorFtpFromRideSettings() throws Exception {
        JsonNode athlete = json("""
                {"name":"Dan","icu_weight":72.5,"sportSettings":[
                  {"types":["Run"],"ftp":null,"indoor_ftp":null},
                  {"types":["Ride","VirtualRide"],"ftp":250,"indoor_ftp":235,"lthr":170,"max_hr":190}
                ]}""");

        AthleteThresholdsDto t = IntervalsService.parseThresholds(athlete);

        assertThat(t.name()).isEqualTo("Dan");
        assertThat(t.ftp()).isEqualTo(250);
        assertThat(t.indoorFtp()).isEqualTo(235);
        assertThat(t.lthr()).isEqualTo(170);
        assertThat(t.maxHr()).isEqualTo(190);
        assertThat(t.weight()).isEqualTo(72.5);
    }

    @Test
    void missingIndoorFtpIsNull() throws Exception {
        JsonNode athlete = json("""
                {"name":"Dan","sportSettings":[{"types":["Ride"],"ftp":250}]}""");

        AthleteThresholdsDto t = IntervalsService.parseThresholds(athlete);

        assertThat(t.ftp()).isEqualTo(250);
        assertThat(t.indoorFtp()).isNull();
        assertThat(t.weight()).isNull();
    }

    @Test
    void activityZonesCarryIndoorFlagAndIcuFtp() throws Exception {
        JsonNode activity = json("""
                {"id":"i1","type":"VirtualRide","trainer":true,"icu_ftp":235}""");

        ActivityZonesDto zones = IntervalsService.toActivityZones(activity, List.of(), List.of());

        assertThat(zones.indoor()).isTrue();
        assertThat(zones.icuFtp()).isEqualTo(235);
    }

    @Test
    void outdoorActivityWithoutIcuFtp() throws Exception {
        JsonNode activity = json("""
                {"id":"i2","type":"Ride"}""");

        ActivityZonesDto zones = IntervalsService.toActivityZones(activity, List.of(), List.of());

        assertThat(zones.indoor()).isFalse();
        assertThat(zones.icuFtp()).isNull();
    }

    @Test
    void rideOnTrainerIsIndoor() throws Exception {
        JsonNode activity = json("""
                {"id":"i3","type":"Ride","trainer":true,"icu_ftp":250}""");

        assertThat(IntervalsService.toActivityZones(activity, List.of(), List.of()).indoor()).isTrue();
    }

    @Test
    void explicitNullIndoorFtpIsNull() throws Exception {
        JsonNode athlete = json("""
                {"sportSettings":[{"types":["Ride"],"ftp":250,"indoor_ftp":null}]}""");

        assertThat(IntervalsService.parseThresholds(athlete).indoorFtp()).isNull();
    }
}
