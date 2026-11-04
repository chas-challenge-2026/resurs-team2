package se.comerit.resurs.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A mutable {@link Clock} so unit tests can advance "now" deterministically
 * without sleeping. Records the base epoch from construction and adds the
 * accumulated offset on read.
 */
public class MutableClock extends Clock {

    private Instant base;
    private long nanosOffset;

    public MutableClock() {
        this(Instant.parse("2026-01-01T00:00:00Z"));
    }

    public MutableClock(Instant base) {
        this.base = base;
    }

    /** Advance the clock by the given duration. */
    public void advance(java.time.Duration duration) {
        this.nanosOffset += duration.toNanos();
    }

    /** Reset to the original base instant. */
    public void reset() {
        this.nanosOffset = 0;
    }

    @Override
    public ZoneId getZone() {
        return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock copy = new MutableClock(base);
        copy.nanosOffset = this.nanosOffset;
        return copy;
    }

    @Override
    public Instant instant() {
        return base.plusNanos(nanosOffset);
    }
}
