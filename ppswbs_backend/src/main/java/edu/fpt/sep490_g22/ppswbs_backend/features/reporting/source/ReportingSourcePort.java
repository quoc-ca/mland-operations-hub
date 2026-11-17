package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregates FE-10 needs from the source owners, one call per source so one failing source never hides
 * the others. Every period is the half-open interval {@code [from, to)}.
 */
public interface ReportingSourcePort {

    /** FE-08: branch orders that became PAID in the period; revenue is product value after discount, no shipping. */
    List<RetailBranchRevenue> retailRevenue(Collection<Long> locationIds, Instant from, Instant to);

    /** FE-03: folios that became FINALIZED (fully paid) in the period, by the branch of the booked session. */
    WorkshopRevenue workshopRevenue(Collection<Long> locationIds, Instant from, Instant to);

    /** FE-03: sessions with attendance finalized whose scheduled start is in the period, with their check-ins. */
    List<SessionCapacity> completedSessions(Collection<Long> locationIds, Instant from, Instant to);

    /** Display names of branches; a name is presentation only. */
    Map<Long, String> branchNames(Collection<Long> locationIds);

    record RetailBranchRevenue(long locationId, String currency, BigInteger revenue, long paidOrderCount) { }

    /**
     * @param branches         VND revenue of folios whose branch is in scope
     * @param unresolvedFolios finalized folios in the period whose session branch could not be found
     * @param nonVndFolios     finalized folios in scope that are not in VND and were left out
     */
    record WorkshopRevenue(List<WorkshopBranchRevenue> branches, long unresolvedFolios, long nonVndFolios) { }

    record WorkshopBranchRevenue(long locationId, BigInteger revenue, long folioCount) { }

    record SessionCapacity(long locationId, long completedSessions, long capacity, long checkedIn) { }
}
