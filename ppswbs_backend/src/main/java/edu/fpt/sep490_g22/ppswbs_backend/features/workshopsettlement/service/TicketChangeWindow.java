package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * FR-062 / SC-023: the customer change window closes at the end of the 7th calendar day (Asia/Ho_Chi_Minh) after the
 * day the replaced session ends, not 168 hours later. The first instant outside the window is the start of the 8th
 * day, so a target at 23:59 of day 7 is in and one at 00:00 of day 8 is out, whatever time the session ended.
 * Same rule as {@code TicketReplacementRules.checkLateArrivalWindow} in Operations (7 days, same zone).
 */
final class TicketChangeWindow {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final long WINDOW_DAYS = 7;

    private TicketChangeWindow() {
    }

    /** First instant after the window: start of the 8th day after the date of {@code sessionEnd}. */
    static Instant firstInstantAfterWindow(Instant sessionEnd) {
        LocalDate endDate = sessionEnd.atZone(ZONE).toLocalDate();
        return endDate.plusDays(WINDOW_DAYS + 1).atStartOfDay(ZONE).toInstant();
    }
}
