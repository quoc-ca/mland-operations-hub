package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.MemberProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionWriteDtos.UpdateProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCommandService.CommandKey;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCommandService.CommandResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Edit, pause and resume of a programme (T043, T044, FR-006, FR-009, FR-010, US2.2-US2.8). The caller has already
 * authorized a current Manager. Every command takes the exclusive registry lock, then the programme lock, checks
 * {@code expectedVersion} and commits with its audit row and receipt.
 *
 * <ul>
 *   <li>An edit merges the sent fields into a new immutable revision, copies the targets unchanged and points the
 *       programme to it. Code, scope, kind and targets never change. The new terms apply only to transactions
 *       accepted afterwards; accepted transactions keep the revision they reference.</li>
 *   <li>A limit can never drop below what is used plus held, for the total and for any single Member, read under the
 *       programme lock. Holds are never removed to make an edit fit.</li>
 *   <li>Pause turns ENABLED into PAUSED, resume PAUSED into ENABLED, on the same programme and code; counters,
 *       history and window are unchanged, nothing is extended, and accepted holds are untouched.</li>
 * </ul>
 */
@Service
public class PromotionMaintenanceService {

    public static final String UPDATE = "promotion.update";
    public static final String PAUSE = "promotion.pause";
    public static final String RESUME = "promotion.resume";

    private final LoyaltyCommandService commands;
    private final LoyaltyLockCoordinator locks;
    private final PromotionRevisionRepository revisions;
    private final PromotionTargetRepository targets;
    private final MemberProgramRepository memberPrograms;
    private final Clock clock;

    public PromotionMaintenanceService(LoyaltyCommandService commands, LoyaltyLockCoordinator locks,
                                       PromotionRevisionRepository revisions, PromotionTargetRepository targets,
                                       MemberProgramRepository memberPrograms, ObjectProvider<Clock> clock) {
        this.commands = commands;
        this.locks = locks;
        this.revisions = revisions;
        this.targets = targets;
        this.memberPrograms = memberPrograms;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    public void update(String managerRef, UUID commandId, long programId, UpdateProgram request) {
        CommandKey key = new CommandKey(managerRef, UPDATE, commandId,
                LoyaltyCommandService.digest(request.canonical(programId)));
        commands.execute(key, () -> {
            PromotionProgram program = lockForChange(programId, request.expectedVersion());
            PromotionRevision current = revisions.findById(program.getCurrentRevisionId())
                    .orElseThrow(() -> new IllegalStateException("Programme " + programId + " has no current revision"));
            boolean auto = program.getKind() == ProgramKind.AUTO_DISCOUNT;
            if (auto && (request.totalLimit() != null || request.perMemberLimit() != null)) {
                throw LoyaltyException.invalidField("totalLimit", "An automatic discount has no quota.");
            }
            if (auto && Boolean.FALSE.equals(request.allowPoints())) {
                throw LoyaltyException.invalidField("allowPoints", "An automatic discount always allows points.");
            }
            DiscountTerms base = request.discount() != null ? request.discount() : current.terms();
            BigInteger minimum = request.minimumEligibleAmount() != null
                    ? request.minimumEligibleAmount() : current.getMinimumEligibleAmount();
            DiscountTerms terms = new DiscountTerms(base.percent(), base.fixedAmount(), base.maxDiscount(), minimum);
            Instant now = LoyaltyValues.truncateToMicros(clock.instant());
            long manager = Long.parseLong(managerRef);
            PromotionRevision next = revisions.saveAndFlush(PromotionRevision.create(programId,
                    revisions.findMaxRevisionNo(programId) + 1, program.getKind(),
                    request.name() != null ? request.name() : current.getName(),
                    request.startAt() != null ? request.startAt() : current.getStartAt(),
                    request.endAt() != null ? request.endAt() : current.getEndAt(), terms,
                    request.allowPoints() != null ? request.allowPoints() : current.isAllowPoints(), now, manager));
            for (PromotionTarget target : targets.findByRevisionIdOrderByDimensionAscTargetKeyAsc(current.getId())) {
                targets.save(PromotionTarget.create(next.getId(), program.getScope(), target.getDimension(),
                        target.getTargetKey()));
            }
            if (!auto && (request.totalLimit() != null || request.perMemberLimit() != null)) {
                long total = request.totalLimit() != null ? request.totalLimit() : program.getTotalLimit();
                long perMember = request.perMemberLimit() != null ? request.perMemberLimit() : program.getMemberLimit();
                if (perMember < memberPrograms.findMaxMemberUsage(programId)) {
                    throw new LoyaltyException(LoyaltyErrorCode.QUOTA_LIMIT_CONFLICT,
                            "The per-member limit cannot be lower than a Member's uses already made or held.");
                }
                program.changeLimits(total, perMember);
            }
            program.pointToRevision(next.getId());
            program.markConfigurationChanged();
            return result(program, "{\"fromRevision\":" + current.getRevisionNo() + ",\"toRevision\":"
                    + next.getRevisionNo() + "}");
        });
    }

    public void pause(String managerRef, UUID commandId, long programId, long expectedVersion) {
        transition(managerRef, PAUSE, commandId, programId, expectedVersion, PromotionProgram::pause);
    }

    public void resume(String managerRef, UUID commandId, long programId, long expectedVersion) {
        transition(managerRef, RESUME, commandId, programId, expectedVersion, PromotionProgram::resume);
    }

    private void transition(String managerRef, String action, UUID commandId, long programId, long expectedVersion,
                            Consumer<PromotionProgram> change) {
        CommandKey key = new CommandKey(managerRef, action, commandId,
                LoyaltyCommandService.digest(Map.of("programId", programId, "expectedVersion", expectedVersion)));
        commands.execute(key, () -> {
            PromotionProgram program = lockForChange(programId, expectedVersion);
            change.accept(program);
            return result(program, "{\"status\":\"" + program.getStatus() + "\"}");
        });
    }

    private PromotionProgram lockForChange(long programId, long expectedVersion) {
        locks.writeRegistry();
        PromotionProgram program = locks.lockPrograms(Set.of(programId)).get(programId);
        LoyaltyLockCoordinator.requireVersion(program.getConfigVersion(), expectedVersion);
        return program;
    }

    private static CommandResult result(PromotionProgram program, String detail) {
        return new CommandResult("program:" + program.getId(), detail, Map.of("programId", program.getId()));
    }
}
