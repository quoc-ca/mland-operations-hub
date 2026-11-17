package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Shared time rule for the per-ticket attendance-correction closure (003.2 FR-032): the latest finalization among one ticket's own sessions plus 24 hours. */
public final class AttendanceCorrectionWindowPolicy {
    public static final Duration WINDOW = Duration.ofHours(24);

    private AttendanceCorrectionWindowPolicy() {}

    /** Empty means the source timestamps are incomplete, so callers must fail closed. */
    public static Optional<Instant> closesAt(Collection<Instant> finalizationTimes) {
        if (finalizationTimes == null || finalizationTimes.isEmpty()
                || finalizationTimes.stream().anyMatch(Objects::isNull)) {
            return Optional.empty();
        }
        return finalizationTimes.stream().max(Instant::compareTo).map(latest -> latest.plus(WINDOW));
    }

    public static boolean isClosed(Collection<Instant> finalizationTimes, Instant now) {
        return closesAt(finalizationTimes).map(deadline -> !now.isBefore(deadline)).orElse(false);
    }
}
