package com.asdasfa.jbs2bg.workbench;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * A {@link Clock} that measures elapsed time on a monotonic nanosecond ticker instead of the wall clock.
 *
 * <p>Type-ahead only asks how long ago the previous key arrived. A wall clock ({@link Clock#systemUTC()}) can jump
 * when the system time is adjusted, which would expire or keep alive a prefix the user never paused on; before the
 * features took an injected {@code Clock}, the shell timed type-ahead with {@link System#nanoTime()} for the same
 * reason. The instants this clock returns start at {@link Instant#EPOCH} when it is created and are meaningful only as
 * differences: they never denote a calendar time.</p>
 */
public final class MonotonicClock extends Clock {

    private final LongSupplier ticker;
    private final long origin;
    private final ZoneId zone;

    /** Shares one ticker and origin so a zone change keeps the same timeline. */
    private MonotonicClock(LongSupplier ticker, long origin, ZoneId zone) {
        this.ticker = ticker;
        this.origin = origin;
        this.zone = zone;
    }

    /**
     * @return a UTC clock backed by {@link System#nanoTime()}, for production type-ahead timing
     */
    public static MonotonicClock system() {
        return of(System::nanoTime);
    }

    /**
     * Creates a clock over the given ticker, starting at {@link Instant#EPOCH} at the ticker's current reading.
     *
     * @param ticker monotonic nanosecond source with {@link System#nanoTime()} semantics: only differences between
     *               readings are meaningful, and the value may be negative or wrap past {@code Long.MAX_VALUE}
     * @return a UTC clock whose instants advance by the ticker's elapsed nanoseconds
     * @throws NullPointerException when {@code ticker} is null
     */
    public static MonotonicClock of(LongSupplier ticker) {
        Objects.requireNonNull(ticker, "ticker");
        return new MonotonicClock(ticker, ticker.getAsLong(), ZoneOffset.UTC);
    }

    /** {@inheritDoc} */
    @Override
    public ZoneId getZone() {
        return zone;
    }

    /** {@inheritDoc} The returned clock reads the same ticker from the same origin. */
    @Override
    public Clock withZone(ZoneId zone) {
        return new MonotonicClock(ticker, origin, Objects.requireNonNull(zone, "zone"));
    }

    /**
     * @return {@link Instant#EPOCH} plus the nanoseconds elapsed on the ticker since this clock was created
     */
    @Override
    public Instant instant() {
        // Subtracting the origin first keeps the elapsed time right when nanoTime-style tickers wrap around.
        return Instant.EPOCH.plusNanos(ticker.getAsLong() - origin);
    }
}
