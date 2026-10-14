package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** Verifies that the production type-ahead clock follows elapsed ticker time and never the wall clock. */
class MonotonicClockTest {

    /** Instants move by exactly the ticker's elapsed nanoseconds, whatever the ticker's absolute value is. */
    @Test
    void instantsAdvanceByTheElapsedTickerTime() {
        AtomicLong ticker = new AtomicLong(-42_000L);
        Clock clock = MonotonicClock.of(ticker::get);
        Instant first = clock.instant();

        ticker.addAndGet(Duration.ofMillis(750).toNanos());
        Instant second = clock.instant();
        ticker.addAndGet(1L);
        Instant third = clock.instant();

        assertEquals(Duration.ofMillis(750), Duration.between(first, second));
        assertEquals(Duration.ofNanos(1L), Duration.between(second, third));
    }

    /** A ticker that wraps past {@code Long.MAX_VALUE} still reads as time moving forward. */
    @Test
    void tickerOverflowStillMovesForward() {
        AtomicLong ticker = new AtomicLong(Long.MAX_VALUE - 10L);
        Clock clock = MonotonicClock.of(ticker::get);
        Instant before = clock.instant();

        ticker.set(Long.MIN_VALUE + 5L);
        Instant after = clock.instant();

        assertEquals(Duration.ofNanos(16L), Duration.between(before, after));
    }

    /** A zone change only relabels the clock; it keeps reading the same ticker from the same origin. */
    @Test
    void withZoneKeepsTheSameTimeline() {
        AtomicLong ticker = new AtomicLong(0L);
        Clock clock = MonotonicClock.of(ticker::get);
        ticker.addAndGet(Duration.ofSeconds(2).toNanos());
        Clock zoned = clock.withZone(ZoneOffset.ofHours(5));
        ticker.addAndGet(Duration.ofSeconds(3).toNanos());

        assertEquals(ZoneOffset.UTC, clock.getZone());
        assertEquals(ZoneOffset.ofHours(5), zoned.getZone());
        assertEquals(clock.instant(), zoned.instant());
    }

    /** The system clock reads {@link System#nanoTime()}, so successive readings never go backwards. */
    @Test
    void systemClockNeverGoesBackwards() {
        Clock clock = MonotonicClock.system();
        Instant previous = clock.instant();
        for (int reading = 0; reading < 1_000; reading++) {
            Instant next = clock.instant();
            assertFalse(next.isBefore(previous), "reading " + reading + " went backwards");
            previous = next;
        }
    }
}
