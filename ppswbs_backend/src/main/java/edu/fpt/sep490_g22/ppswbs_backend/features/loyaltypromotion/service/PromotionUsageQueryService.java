package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionUsageDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * getPromotionUsage (T045, FR-008, US2.1, US2.9). Read in one read-only transaction.
 *
 * <ul>
 *   <li>Voucher: limits and the held and consumed counters come from the programme row. A valid workshop
 *       cancellation has already moved the use from consumed back to available (and the benefit to RESTORED), so it
 *       is out of the totals exactly once; the committed discount sums consumed uses only. Holds and previews are
 *       never counted as used.</li>
 *   <li>Automatic discount: only transactions the owner settled count.</li>
 *   <li>Expiry or pause never removes uncancelled settled usage; the consume/restore history stays on the benefits
 *       and in the audit trail.</li>
 * </ul>
 */
@Service
public class PromotionUsageQueryService {

    private final PromotionProgramRepository programs;
    private final AcceptedBenefitRepository benefits;

    public PromotionUsageQueryService(PromotionProgramRepository programs, AcceptedBenefitRepository benefits) {
        this.programs = programs;
        this.benefits = benefits;
    }

    @Transactional(readOnly = true)
    public PromotionUsageDtos.Summary usage(long programId) {
        PromotionProgram program = programs.findById(programId).orElseThrow(PromotionManagementQueryService::notFound);
        if (program.isVoucher()) {
            return new PromotionUsageDtos.ProgramUsage(program.getId().toString(), program.getTotalLimit().toString(),
                    program.getMemberLimit().toString(), program.getHeldCount().toString(),
                    program.getConsumedCount().toString(),
                    amount(benefits.sumConsumedVoucherDiscount(programId)));
        }
        return new PromotionUsageDtos.AutoUsage(program.getId().toString(),
                Long.toString(benefits.countSettled(programId)),
                amount(benefits.sumSettledDiscount(programId)));
    }

    /** SUM over no rows is NULL in SQL; report it as zero. */
    private static String amount(java.math.BigInteger sum) {
        return sum == null ? "0" : sum.toString();
    }
}
