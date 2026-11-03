package com.cycling.delivery;

import java.time.Duration;

/** Wartet eine Zeitspanne. Eigenes Interface, damit Tests nicht wirklich schlafen. */
@FunctionalInterface
public interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
}
