package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionWriteDtos.CreateProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCommandService.CommandKey;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCommandService.CommandResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * createPromotionProgram (T027, FR-002-FR-007, US1.1-US1.8). The caller has already authorized a current Manager.
 *
 * <ol>
 *   <li>Targets are checked through the owning module's provider before any loyalty lock (T025).</li>
 *   <li>Under the exclusive registry lock the code is checked against all history (trimmed, upper-cased with
 *       {@link java.util.Locale#ROOT}; an expired or paused programme still owns its code, codes are never
 *       reused), then the programme (ENABLED), its first immutable revision, its targets and, for a voucher, its
 *       quota are written together with the audit row and the command receipt.</li>
 *   <li>A programme with a future start is stored but has no effect until it starts. A holiday programme is an
 *       ordinary product or workshop programme with its own dates, not an extra voucher layer.</li>
 * </ol>
 * Nothing is partially inserted: any failure rolls the whole command back, and a lost response is recovered by
 * replaying the same command key.
 */
@Service
public class PromotionCreationService {

    public static final String ACTION = "promotion.create";

    private final LoyaltyCommandService commands;
    private final PromotionTargetValidationService targetValidation;
    private final LoyaltyLockCoordinator locks;
    private final PromotionProgramRepository programs;
    private final PromotionRevisionRepository revisions;
    private final PromotionTargetRepository targets;
    private final Clock clock;

    public PromotionCreationService(LoyaltyCommandService commands, PromotionTargetValidationService targetValidation,
                                    LoyaltyLockCoordinator locks, PromotionProgramRepository programs,
                                    PromotionRevisionRepository revisions, PromotionTargetRepository targets,
                                    ObjectProvider<Clock> clock) {
        this.commands = commands;
        this.targetValidation = targetValidation;
        this.locks = locks;
        this.programs = programs;
        this.revisions = revisions;
        this.targets = targets;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    /** Returns the id of the created programme, or of the programme created earlier with the same command key. */
    public long create(String managerRef, UUID commandId, CreateProgram request) {
        CommandKey key = new CommandKey(managerRef, ACTION, commandId, LoyaltyCommandService.digest(request.canonical()));
        LoyaltyCommandService.Outcome outcome = commands.execute(key, () -> write(managerRef, request));
        return outcome.result().path("programId").asLong();
    }

    private CommandResult write(String managerRef, CreateProgram request) {
        targetValidation.validate(request.scope(), request.targets());
        locks.writeRegistry();
        long manager = Long.parseLong(managerRef);
        Instant now = LoyaltyValues.truncateToMicros(clock.instant());

        PromotionProgram program;
        if (request.kind() == ProgramKind.VOUCHER) {
            if (programs.existsByCodeKey(request.code().key())) {
                throw new LoyaltyException(LoyaltyErrorCode.CODE_EXISTS, "This voucher code has already been issued.");
            }
            program = PromotionProgram.voucher(request.scope(), request.code().original(), request.code().key(),
                    request.totalLimit(), request.perMemberLimit(), now, manager);
        } else {
            program = PromotionProgram.autoDiscount(now, manager);
        }
        program = programs.saveAndFlush(program);
        PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                request.kind(), request.name(), request.startAt(), request.endAt(), request.terms(),
                request.allowPoints(), now, manager));
        program.pointToRevision(revision.getId());
        for (TargetRef target : request.targets()) {
            targets.save(PromotionTarget.create(revision.getId(), request.scope(), target.dimension(),
                    target.targetKey()));
        }
        programs.flush();
        String detail = "{\"kind\":\"" + request.kind() + "\",\"scope\":\"" + request.scope() + "\",\"revision\":1}";
        return new CommandResult("program:" + program.getId(), detail, Map.of("programId", program.getId()));
    }
}
