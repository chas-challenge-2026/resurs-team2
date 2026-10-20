package se.comerit.resurs.api.v1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EtaService}: the flat 24-hour automated window, the
 * business-day review math across weekends and the bank-timezone day boundary,
 * and input validation.
 */
class EtaServiceTest {

    // 2026-09-25 is a Friday (UTC).
    private static final Instant FRIDAY = Instant.parse("2026-09-25T10:00:00Z");
    private static final Instant SATURDAY = Instant.parse("2026-09-26T10:00:00Z");
    private static final Instant MONDAY = Instant.parse("2026-09-28T10:00:00Z");

    @Nested
    @DisplayName("Automated resolution — flat hours")
    class WithinHours {

        private final EtaService service = new EtaService("UTC");

        @Test
        @DisplayName("24 hours is a flat window that crosses into the weekend")
        void addsExactHours() {
            // Friday 10:00 UTC + 24 h = Saturday 10:00 UTC — weekends count.
            assertThat(service.estimateWithinHours(FRIDAY, 24))
                    .isEqualTo(Instant.parse("2026-09-26T10:00:00Z"));
        }

        @Test
        @DisplayName("Zero hours returns the input unchanged")
        void zeroHoursAreNoop() {
            assertThat(service.estimateWithinHours(FRIDAY, 0)).isEqualTo(FRIDAY);
        }

        @Test
        @DisplayName("Rejects a negative number of hours")
        void negativeHoursAreRejected() {
            assertThatThrownBy(() -> service.estimateWithinHours(FRIDAY, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Manual review — business days")
    class BusinessDays {

        private final EtaService service = new EtaService("UTC");

        @Test
        @DisplayName("Friday plus one business day lands on the following Monday")
        void fridaySkipsWeekend() {
            assertThat(service.estimateBusinessDays(FRIDAY, 1)).isEqualTo(MONDAY);
        }

        @Test
        @DisplayName("Friday plus two business days lands on Tuesday")
        void fridayPlusTwo() {
            assertThat(service.estimateBusinessDays(FRIDAY, 2))
                    .isEqualTo(Instant.parse("2026-09-29T10:00:00Z"));
        }

        @Test
        @DisplayName("Saturday plus one business day lands on the following Monday")
        void saturdaySkipsToMonday() {
            assertThat(service.estimateBusinessDays(SATURDAY, 1)).isEqualTo(MONDAY);
        }

        @Test
        @DisplayName("Mid-week plus three business days stays within the week")
        void midWeekAddition() {
            Instant wednesday = Instant.parse("2026-09-23T10:00:00Z");
            assertThat(service.estimateBusinessDays(wednesday, 3))
                    .isEqualTo(Instant.parse("2026-09-28T10:00:00Z"));
        }

        @Test
        @DisplayName("Zero business days returns the input unchanged")
        void zeroDaysAreNoop() {
            assertThat(service.estimateBusinessDays(FRIDAY, 0)).isEqualTo(FRIDAY);
        }

        @Test
        @DisplayName("Rejects a negative number of days")
        void negativeDaysAreRejected() {
            assertThatThrownBy(() -> service.estimateBusinessDays(FRIDAY, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Bank local timezone")
    class BankTimezone {

        private final EtaService utc = new EtaService("UTC");
        // Mirrors resurs.sla.timezone=+02:00 in application.properties.
        private final EtaService bank = new EtaService("+02:00");

        @Test
        @DisplayName("Same instant can be Friday in UTC but Saturday in UTC+2, shifting the review estimate")
        void zoneShiftsTheDayBoundary() {
            // 22:30 UTC on Friday 2026-09-25 = 00:30 already Saturday in UTC+2.
            Instant lateFridayEvening = Instant.parse("2026-09-25T22:30:00Z");

            // UTC calendar: still Friday → next business day is Monday.
            assertThat(utc.estimateBusinessDays(lateFridayEvening, 1))
                    .isEqualTo(Instant.parse("2026-09-28T22:30:00Z"));

            // Bank calendar (UTC+2): already Saturday → also lands on Monday,
            // but one local day earlier in UTC terms (Sunday 22:30 UTC).
            assertThat(bank.estimateBusinessDays(lateFridayEvening, 1))
                    .isEqualTo(Instant.parse("2026-09-27T22:30:00Z"));
        }

        @Test
        @DisplayName("Early morning instants resolve to the same business day in both zones")
        void zonesAgreeDuringTheWorkingDay() {
            // 09:00 UTC Friday = 11:00 in UTC+2 — still Friday in both.
            Instant fridayMorning = Instant.parse("2026-09-25T09:00:00Z");
            Instant mondayMorning = Instant.parse("2026-09-28T09:00:00Z");

            assertThat(utc.estimateBusinessDays(fridayMorning, 1)).isEqualTo(mondayMorning);
            assertThat(bank.estimateBusinessDays(fridayMorning, 1)).isEqualTo(mondayMorning);
        }
    }
}