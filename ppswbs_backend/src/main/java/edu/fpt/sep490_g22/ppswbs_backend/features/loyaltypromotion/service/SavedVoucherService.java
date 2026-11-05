package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.MemberProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyScalarCodec;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PublicPromotionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCommandService.CommandKey;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCommandService.CommandResult;
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
            locks.readRegistry();
            PromotionProgram program = locks.lockPrograms(Set.of(programId)).get(programId);
            if (!program.isVoucher()) {
                throw PromotionManagementQueryService.notFound();
            }
            MemberProgram row = locks.lockOrCreateMemberProgram(programId, memberId);
            row.save(LoyaltyValues.truncateToMicros(clock.instant()));
            return new CommandResult("program:" + programId, "{\"saved\":true}", Map.of("programId", programId));
        });
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
