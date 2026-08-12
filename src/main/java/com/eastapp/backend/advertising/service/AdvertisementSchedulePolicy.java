package com.eastapp.backend.advertising.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class AdvertisementSchedulePolicy {
    private AdvertisementSchedulePolicy() {
    }

    static boolean exceedsConcurrentLimit(
            List<Window> existingActiveAdvertisements,
            Instant candidateStartsAt,
            Instant candidateEndsAt,
            int maximumConcurrent
    ) {
        List<Boundary> boundaries = new ArrayList<>();
        boundaries.add(new Boundary(candidateStartsAt, 1));
        boundaries.add(new Boundary(candidateEndsAt, -1));

        for (Window advertisement : existingActiveAdvertisements) {
            Instant clippedStart = laterOf(advertisement.startsAt(), candidateStartsAt);
            Instant clippedEnd = earlierOf(advertisement.endsAt(), candidateEndsAt);
            if (!clippedStart.isBefore(clippedEnd)) continue;
            boundaries.add(new Boundary(clippedStart, 1));
            boundaries.add(new Boundary(clippedEnd, -1));
        }

        boundaries.sort(Comparator
                .comparing(Boundary::at)
                .thenComparingInt(Boundary::delta));

        int concurrent = 0;
        for (Boundary boundary : boundaries) {
            concurrent += boundary.delta();
            if (concurrent > maximumConcurrent) return true;
        }
        return false;
    }

    private static Instant laterOf(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private static Instant earlierOf(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    record Window(Instant startsAt, Instant endsAt) {
    }

    private record Boundary(Instant at, int delta) {
    }
}
