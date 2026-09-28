package se.comerit.resurs.api.v1.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import jakarta.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * EtaService — computes an estimated resolution time from the configured SLA.
 *
 * <p>Two stages have different day semantics:
 * <ul>
 *   <li><b>Automated resolution</b> — a flat {@code 24}-hour window from
 *       submission; weekends count, no calendar adjustment.</li>
 *   <li><b>Manual review</b> — business days (skipping Saturdays and Sundays),
 *       so a review starting on a Friday lands on the following Monday.</li>
 * </ul>
 * Which calendar day an {@link Instant} falls on is decided in the bank's
 * timezone ({@code resurs.sla.timezone}, default {@code UTC}). All timestamps
 * are stored as UTC; the timezone only affects the day boundary used to count
 * business days.
 */
@Service
public class EtaService {

    private final ZoneId zone;

    public EtaService(@Value("${resurs.sla.timezone:UTC}") String timezone) {
        this.zone = ZoneId.of(timezone);
    }

    /**
     * Automated-resolution ETA: {@code from} plus exactly {@code hours}, any
     * calendar day. Plain time-of-day arithmetic, no weekday handling.
     */
    @Nonnull
    public Instant estimateWithinHours(@Nonnull Instant from, long hours) {
        Objects.requireNonNull(from, "from must not be null");
        requireNonNegative(hours, "hours");
        return from.plus(hours, ChronoUnit.HOURS);
    }

    /**
     * Manual-review ETA: steps forward {@code days} business days (skipping
     * Saturdays and Sundays), preserving the time-of-day across the weekend.
     * The weekday of each candidate day is evaluated in the bank's configured
     * timezone, so a timestamp that is a late Friday evening in UTC can
     * already be Saturday in the bank's local calendar.
     */
    @Nonnull
    public Instant estimateBusinessDays(@Nonnull Instant from, int days) {
        Objects.requireNonNull(from, "from must not be null");
        requireNonNegative(days, "days");
        Instant cursor = from;
        int remaining = days;
        while (remaining > 0) {
            cursor = cursor.plus(1, ChronoUnit.DAYS);
            DayOfWeek day = cursor.atZone(zone).getDayOfWeek();
            if (!day.equals(DayOfWeek.SATURDAY) && !day.equals(DayOfWeek.SUNDAY)) {
                remaining--;
            }
        }
        return cursor;
    }

    private static void requireNonNegative(long value, String unit) {
        if (value < 0) {
            throw new IllegalArgumentException(unit + " must not be negative: " + value);
        }
    }
}