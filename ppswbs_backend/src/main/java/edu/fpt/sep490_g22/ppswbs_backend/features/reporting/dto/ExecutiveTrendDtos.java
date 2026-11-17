package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto;

import java.util.List;

/**
 * FE-10 trends (Session 2026-11-16). A null value means the source could not be read, never zero. Money is whole VND
 * as a string. Workshop places count a ticket once per session it is entitled to.
 */
public final class ExecutiveTrendDtos {
    private ExecutiveTrendDtos() { }

    public record TrendValues(
        Long retailPlaced, Long retailPaid, Long retailCancelled, Long retailExpired, String retailRevenue,
        Long workshopBooked, Long workshopConfirmed, Long workshopCancelled, Long workshopAbandoned,
        Long placesRegistered, Long placesCheckedIn, Long placesNoShow, Long placesCancelled, String workshopRevenue) { }

    /** One bucket; {@code startAt} is the bucket start as an instant, {@code label} its Vietnam-time label. */
    public record TrendPoint(String startAt, String label, TrendValues values) { }

    /**
     * @param granularity HOUR for a day, DAY for a week or month, MONTH for a year
     * @param previous    the same metrics for the previous period of the same length, for the change indicator
     * @param unavailable sources that could not be read (RETAIL_ORDERS, RETAIL_REVENUE, WORKSHOP_BOOKINGS,
     *                    ATTENDANCE, WORKSHOP_REVENUE)
     */
    public record ExecutiveTrendResponse(
        ExecutiveReportDtos.ReportScope scope, String granularity, List<TrendPoint> points,
        TrendValues current, TrendValues previous, String previousStartAt, String previousEndAt,
        List<String> unavailable, String lastUpdatedAt) { }
}
