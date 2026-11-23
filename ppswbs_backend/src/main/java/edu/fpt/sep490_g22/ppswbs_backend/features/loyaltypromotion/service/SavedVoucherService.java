package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.MemberProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyScalarCodec;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PublicPromotionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitReasonCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCommandService.CommandKey;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCommandService.CommandResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Member's voucher wallet (T068, FR-012, FR-013, US3.3-US3.6). Saving is a shortcut only: it records the time the
 * Member saved a voucher on the Member's programme row, holds no quota or points, grants no entitlement and is
 * idempotent (the first save time is kept). The row is touched under the programme lock, in the agreed lock order,
 * because checkout may create the same row to count uses. Listing shows each saved voucher with its current terms and
 * availability; being in the list never means it can be used.
 *
 * <p>Session 2026-11-22 (FR-046): saving a {@code CLAIM_REQUIRED} voucher is not a shortcut but a claim. The first save
 * holds one use of the total for the Member ({@code PromotionProgram.claim()}) under the programme and member locks,
 * so two Members racing for the last use cannot both get it; a repeated save holds nothing more. The Member's saved
 * use stays {@code ACTIVE} in the wallet while unused, even when the voucher has no use left for anyone else.
 */
@Service
public class SavedVoucherService {

    public static final String ACTION = "voucher.save";
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final LoyaltyCommandService commands;
    private final LoyaltyLockCoordinator locks;
    private final PromotionProgramRepository programs;
    private final MemberProgramRepository memberPrograms;
    private final PublicPromotionQueryService cards;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public SavedVoucherService(LoyaltyCommandService commands, LoyaltyLockCoordinator locks,
                               PromotionProgramRepository programs, MemberProgramRepository memberPrograms,
                               PublicPromotionQueryService cards, ObjectProvider<Clock> clock) {
        this.commands = commands;
        this.locks = locks;
        this.programs = programs;
        this.memberPrograms = memberPrograms;
        this.cards = cards;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    public void save(long memberId, UUID commandId, long programId) {
        CommandKey key = new CommandKey("member:" + memberId, ACTION, commandId,
                LoyaltyCommandService.digest(Map.of("programId", programId)));
        commands.execute(key, () -> {
            // Checked before the loyalty locks (the product facts are read from 002). A voucher already saved stays
            // saveable, so a repeated save after it was paused still answers as before.
            boolean alreadySaved = memberPrograms.findByProgramIdAndMemberId(programId, memberId)
                    .map(MemberProgram::isSaved).orElse(false);
            if (!alreadySaved) {
                cards.requirePublic(programId);
            }
            // The checks above loaded the programme and the Member row before their locks. A locking read of an
            // entity already in the persistence context fails with a stale-state error when another Member saved in
            // between (the race for the last use, FR-046), so both are detached and the locks read them afresh.
            programs.findById(programId).ifPresent(entityManager::detach);
            memberPrograms.findByProgramIdAndMemberId(programId, memberId).ifPresent(entityManager::detach);
            locks.readRegistry();
            PromotionProgram program = locks.lockPrograms(Set.of(programId)).get(programId);
            if (!program.isVoucher()) {
                throw PromotionManagementQueryService.notFound();
            }
            MemberProgram row = locks.lockOrCreateMemberProgram(programId, memberId);
            if (program.isClaimRequired() && !row.isSaved()) {
                if (row.getHeldCount() + row.getConsumedCount() >= program.getMemberLimit()) {
                    throw claimRefused(BenefitReasonCode.VOUCHER_MEMBER_LIMIT_REACHED);
                }
                if (program.availableTotal() < 1) {
                    throw claimRefused(BenefitReasonCode.VOUCHER_NO_USES_LEFT);
                }
                program.claim();
            }
            row.save(LoyaltyValues.truncateToMicros(clock.instant()));
            return new CommandResult("program:" + programId, "{\"saved\":true}", Map.of("programId", programId));
        });
    }

    /**
     * T131: a refused claim is 422 PROMOTION_INELIGIBLE like before, and the reason travels as the field error
     * {@code reason} with the stable code as its message, so the screen can say "Đã hết lượt lưu" or "Bạn đã dùng
     * hết lượt" instead of the generic text of the error code.
     */
    private static LoyaltyException claimRefused(BenefitReasonCode reason) {
        return new LoyaltyException(LoyaltyErrorCode.PROMOTION_INELIGIBLE, reason.template(),
                List.of(new LoyaltyException.FieldIssue("reason", reason.name())));
    }

    @Transactional(readOnly = true)
    public PublicPromotionDtos.SavedVoucher saved(long memberId, long programId) {
        MemberProgram row = memberPrograms.findByProgramIdAndMemberId(programId, memberId)
                .filter(MemberProgram::isSaved).orElseThrow(PromotionManagementQueryService::notFound);
        return view(row);
    }

    @Transactional(readOnly = true)
    public PublicPromotionDtos.SavedVoucherPage list(long memberId, Integer limit, String cursor) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw LoyaltyException.invalidField("limit", "Must be between 1 and " + MAX_LIMIT + ".");
        }
        Instant afterSavedAt = null;
        Long afterProgramId = null;
        if (cursor != null) {
            String[] parts = decode(cursor);
            afterSavedAt = parseInstant(parts[0]);
            afterProgramId = LoyaltyScalarCodec.parseId("cursor", parts[1]);
        }
        List<MemberProgram> rows = memberPrograms.findSavedPage(memberId, afterSavedAt, afterProgramId,
                PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        List<MemberProgram> page = more ? rows.subList(0, size) : rows;
        String next = null;
        if (more) {
            MemberProgram last = page.get(page.size() - 1);
            String raw = last.getSavedAt().toString() + "|" + last.getProgramId();
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
        return new PublicPromotionDtos.SavedVoucherPage(page.stream().map(this::view).toList(), next);
    }

    private PublicPromotionDtos.SavedVoucher view(MemberProgram row) {
        PromotionProgram program = programs.findById(row.getProgramId())
                .orElseThrow(() -> new IllegalStateException("Saved programme " + row.getProgramId() + " is missing"));
        PublicPromotionQueryService.Projection projection = cards.project(program);
        Instant now = clock.instant();
        String availability;
        if (program.getStatus() == ProgramStatus.PAUSED) {
            availability = "PAUSED";
        } else if (now.isBefore(projection.revision().getStartAt())) {
            availability = "SCHEDULED";
        } else if (!now.isBefore(projection.revision().getEndAt())) {
            availability = "EXPIRED";
        } else if (!projection.hasPublicTarget()) {
            availability = "NO_PUBLIC_TARGET";
        } else if (program.isClaimRequired()) {
            // The Member's own saved use (FR-046): used or held means the one use per Member is taken.
            availability = row.getHeldCount() + row.getConsumedCount() >= program.getMemberLimit()
                    ? "MEMBER_LIMIT_REACHED" : "ACTIVE";
        } else if (program.availableTotal() < 1) {
            // US3.5 (review 2026-11-10, L2): no use left in total; held uses count until released.
            availability = "NO_USES_LEFT";
        } else if (row.getHeldCount() + row.getConsumedCount() >= program.getMemberLimit()) {
            availability = "MEMBER_LIMIT_REACHED";
        } else {
            availability = "ACTIVE";
        }
        return new PublicPromotionDtos.SavedVoucher(projection.card(), row.getSavedAt().toString(), availability);
    }

    private static String[] decode(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2) {
                throw invalidCursor();
            }
            return parts;
        } catch (IllegalArgumentException e) {
            throw invalidCursor();
        }
    }

    private static Instant parseInstant(String raw) {
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw invalidCursor();
        }
    }

    private static LoyaltyException invalidCursor() {
        return LoyaltyException.invalidField("cursor", "The cursor is not valid.");
    }
}
