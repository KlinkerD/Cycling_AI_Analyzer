package com.cycling.training.recommendation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Uhr, die der Test vorstellen kann. */
class MutableClock extends Clock {

    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private Instant now;

    MutableClock(LocalDateTime start) {
        this.now = start.atZone(BERLIN).toInstant();
    }

    void set(LocalDateTime time) {
        now = time.atZone(BERLIN).toInstant();
    }

    void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return BERLIN;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return now;
    }
}
