package com.cycling.ftp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FtpDetectorTest {

    private final FtpDetector detector = new FtpDetector(FtpDetectionSettings.DEFAULTS);

    /** Baut eine Fahrt mit 1-Hz-Samples aus Abschnitten (Dauer, Watt, Puls). */
    private static final class Ride {
        final List<Integer> time = new ArrayList<>();
        final List<Integer> watts = new ArrayList<>();
        final List<Integer> hr = new ArrayList<>();
        int t;

        Ride(int startTime) {
            this.t = startTime;
        }

        Ride() {
            this(0);
        }

        Ride segment(int seconds, Integer power, Integer heartRate) {
            for (int i = 0; i < seconds; i++) {
                time.add(t++);
                watts.add(power);
                hr.add(heartRate);
            }
            return this;
        }

        StreamData streams() {
            return new StreamData(time, watts, hr);
        }

        StreamData withoutHeartRate() {
            return new StreamData(time, watts, null);
        }
    }

    /** 20 min Einfahren, 20 min Test (letzte 5 min hoeherer Puls), 10 min Ausfahren. */
    private static Ride ftpTest(int startTime) {
        return new Ride(startTime)
                .segment(1200, 150, 130)
                .segment(900, 280, 170)
                .segment(300, 280, 178)
                .segment(600, 120, 120);
    }

    @Test
    void detectsClassicTwentyMinuteTest() {
        FtpTestResult result = detector.detect(ftpTest(0).streams());

        assertThat(result.detected()).isTrue();
        assertThat(result.avgPower20Min()).isEqualTo(280);
        assertThat(result.ftp()).isEqualTo(266);           // 280 * 0.95
        assertThat(result.lthr()).isEqualTo(172);          // (900*170 + 300*178) / 1200, ohne Abschlag
        assertThat(result.maxHr()).isEqualTo(178);
        assertThat(result.windowStartSeconds()).isEqualTo(1200);
    }

    @Test
    void windowStartFollowsTheTimeStreamNotTheIndex() {
        FtpTestResult result = detector.detect(ftpTest(100).streams());

        assertThat(result.windowStartSeconds()).isEqualTo(1300);
    }

    @Test
    void steadyEnduranceRideIsNoTest() {
        FtpTestResult result = detector.detect(new Ride().segment(7200, 180, 140).streams());

        assertThat(result.detected()).isFalse();
        assertThat(result.ftp()).isNull();
    }

    @Test
    void rideWithoutHeartRateIsNoTest() {
        FtpTestResult result = detector.detect(ftpTest(0).withoutHeartRate());

        assertThat(result.detected()).isFalse();
        assertThat(result.reason()).contains("Herzfrequenz");
    }

    @Test
    void hardBlockWithoutExhaustionIsNoTest() {
        // Sprint am Ende treibt die MaxHR auf 190, im Block bleibt der Puls bei 150 (< 90 % von 190)
        Ride ride = new Ride()
                .segment(1200, 150, 130)
                .segment(1200, 280, 150)
                .segment(600, 120, 120)
                .segment(30, 600, 190)
                .segment(300, 100, 120);

        FtpTestResult result = detector.detect(ride.streams());

        assertThat(result.detected()).isFalse();
        assertThat(result.reason()).contains("Ausbelastung");
    }

    @Test
    void rideShorterThanTwentyMinutesIsNoTest() {
        FtpTestResult result = detector.detect(new Ride().segment(900, 300, 175).streams());

        assertThat(result.detected()).isFalse();
    }

    @Test
    void blockWithTooManyRecordingGapsIsNoTest() {
        Ride ride = ftpTest(0);
        // jede 5. Sekunde im Testblock fehlt (20 % Luecke statt erlaubter 5 %)
        for (int i = 2400 - 5; i >= 1200; i -= 5) {
            ride.time.remove(i);
            ride.watts.remove(i);
            ride.hr.remove(i);
        }

        FtpTestResult result = detector.detect(ride.streams());

        assertThat(result.detected()).isFalse();
    }

    @Test
    void fewNullPowerValuesAreTolerated() {
        Ride ride = ftpTest(0);
        for (int i = 1300; i < 1330; i++) {
            ride.watts.set(i, null);  // 30 s = 2.5 % des Blocks
        }

        FtpTestResult result = detector.detect(ride.streams());

        assertThat(result.detected()).isTrue();
        assertThat(result.avgPower20Min()).isEqualTo(280);
    }

    @Test
    void singleHeartRateSpikeDoesNotBecomeMaxHr() {
        Ride ride = ftpTest(0);
        ride.hr.set(600, 230);  // Sensor-Ausreisser beim Einfahren

        FtpTestResult result = detector.detect(ride.streams());

        assertThat(result.detected()).isTrue();
        assertThat(result.maxHr()).isEqualTo(178);
    }

    @Test
    void testRideWithoutAnyOtherRidingIsNoTest() {
        FtpTestResult result = detector.detect(new Ride().segment(1200, 280, 175).streams());

        assertThat(result.detected()).isFalse();
    }

    @Test
    void missingStreamsAreNoTest() {
        assertThat(detector.detect(new StreamData(null, null, null)).detected()).isFalse();
        assertThat(detector.detect(null).detected()).isFalse();
    }
}
