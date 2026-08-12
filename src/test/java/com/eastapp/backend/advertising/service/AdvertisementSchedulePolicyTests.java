package com.eastapp.backend.advertising.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvertisementSchedulePolicyTests {
    @Test
    void allowsFourAdvertisementsAtTheSameTime() {
        Instant start = Instant.parse("2026-08-04T00:00:00Z");
        Instant end = Instant.parse("2026-08-05T00:00:00Z");
        List<AdvertisementSchedulePolicy.Window> existing = List.of(
                window(start, end),
                window(start, end),
                window(start, end)
        );

        assertFalse(AdvertisementSchedulePolicy.exceedsConcurrentLimit(existing, start, end, 4));
    }

    @Test
    void rejectsFiveAdvertisementsAtTheSameTime() {
        Instant start = Instant.parse("2026-08-04T00:00:00Z");
        Instant end = Instant.parse("2026-08-05T00:00:00Z");
        List<AdvertisementSchedulePolicy.Window> existing = List.of(
                window(start, end),
                window(start, end),
                window(start, end),
                window(start, end)
        );

        assertTrue(AdvertisementSchedulePolicy.exceedsConcurrentLimit(existing, start, end, 4));
    }

    @Test
    void doesNotTreatTouchingEndAndStartAsOverlap() {
        Instant candidateStart = Instant.parse("2026-08-04T12:00:00Z");
        Instant candidateEnd = Instant.parse("2026-08-04T18:00:00Z");
        List<AdvertisementSchedulePolicy.Window> existing = List.of(
                window(Instant.parse("2026-08-04T06:00:00Z"), candidateStart),
                window(candidateEnd, Instant.parse("2026-08-05T00:00:00Z"))
        );

        assertFalse(AdvertisementSchedulePolicy.exceedsConcurrentLimit(
                existing,
                candidateStart,
                candidateEnd,
                1
        ));
    }

    @Test
    void countsOnlySimultaneousOverlapNotEveryAdvertisementTouchedByTheCandidate() {
        Instant candidateStart = Instant.parse("2026-08-04T00:00:00Z");
        Instant candidateEnd = Instant.parse("2026-08-05T00:00:00Z");
        List<AdvertisementSchedulePolicy.Window> existing = List.of(
                window(
                        Instant.parse("2026-08-04T00:00:00Z"),
                        Instant.parse("2026-08-04T06:00:00Z")
                ),
                window(
                        Instant.parse("2026-08-04T06:00:00Z"),
                        Instant.parse("2026-08-04T12:00:00Z")
                ),
                window(
                        Instant.parse("2026-08-04T12:00:00Z"),
                        Instant.parse("2026-08-04T18:00:00Z")
                ),
                window(
                        Instant.parse("2026-08-04T18:00:00Z"),
                        Instant.parse("2026-08-05T00:00:00Z")
                )
        );

        assertFalse(AdvertisementSchedulePolicy.exceedsConcurrentLimit(
                existing,
                candidateStart,
                candidateEnd,
                2
        ));
    }

    private AdvertisementSchedulePolicy.Window window(Instant startsAt, Instant endsAt) {
        return new AdvertisementSchedulePolicy.Window(startsAt, endsAt);
    }
}
