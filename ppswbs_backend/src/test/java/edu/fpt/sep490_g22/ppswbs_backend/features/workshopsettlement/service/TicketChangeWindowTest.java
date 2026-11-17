package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TicketChangeWindowTest {

    private static Instant vn(String local) {
        return LocalDateTime.parse(local).atZone(TicketChangeWindow.ZONE).toInstant();
    }

    @Test
    void windowEndsAtStartOfEighthDayRegardlessOfSessionEndTime() {
        Instant limit = vn("2026-11-18T00:00:00");
        assertThat(TicketChangeWindow.firstInstantAfterWindow(vn("2026-11-10T23:00:00"))).isEqualTo(limit);
        assertThat(TicketChangeWindow.firstInstantAfterWindow(vn("2026-11-10T09:00:00"))).isEqualTo(limit);
    }

    @Test
    void lastMinuteOfDaySevenInsideAndStartOfDayEightOutside() {
        Instant limit = TicketChangeWindow.firstInstantAfterWindow(vn("2026-11-10T09:00:00"));
        assertThat(vn("2026-11-17T23:59:00").isBefore(limit)).isTrue();
        assertThat(vn("2026-11-18T00:00:00").isBefore(limit)).isFalse();
    }

    @Test
    void usesVietnamCalendarDateNotUtc() {
        // 2026-11-10 23:30 UTC is already 2026-11-11 06:30 in Vietnam
        Instant end = Instant.parse("2026-11-10T23:30:00Z");
        assertThat(TicketChangeWindow.firstInstantAfterWindow(end)).isEqualTo(vn("2026-11-19T00:00:00"));
    }
}
