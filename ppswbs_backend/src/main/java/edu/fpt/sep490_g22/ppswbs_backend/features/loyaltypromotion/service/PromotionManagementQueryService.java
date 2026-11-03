package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyScalarCodec;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionReadDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.DiscountKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Management list and detail of programmes (T028, FR-008 read part). Pages default to 20 items and allow at most
 * 100, newest first by creation time then ID. The cursor is opaque (Base64 of creation time and ID) and carries no
 * personal data. Usage figures are added by US2 (getPromotionUsage), not here.
 */
@Service
public class PromotionManagementQueryService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final PromotionProgramRepository programs;
    private final PromotionRevisionRepository revisions;
    private final PromotionTargetRepository targets;

    public PromotionManagementQueryService(PromotionProgramRepository programs, PromotionRevisionRepository revisions,
                                           PromotionTargetRepository targets) {
        this.programs = programs;
        this.revisions = revisions;
        this.targets = targets;
    }

    @Transactional(readOnly = true)
    public PromotionReadDtos.ProgramPage list(Integer limit, String cursor, String scope, String status) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw LoyaltyException.invalidField("limit", "Must be between 1 and " + MAX_LIMIT + ".");
        }
        TargetScope scopeFilter = scope == null ? null : parseEnum(TargetScope.class, "scope", scope);
        ProgramStatus statusFilter = status == null ? null : parseEnum(ProgramStatus.class, "status", status);
        Instant afterCreatedAt = null;
        Long afterId = null;
        if (cursor != null) {
            String[] parts = decodeCursor(cursor);
            afterCreatedAt = parseCursorInstant(parts[0]);
            afterId = LoyaltyScalarCodec.parseId("cursor", parts[1]);
        }
        List<PromotionProgram> rows = programs.findPage(scopeFilter, statusFilter, afterCreatedAt, afterId,
                PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        List<PromotionProgram> page = more ? rows.subList(0, size) : rows;
        String next = more ? encodeCursor(page.get(page.size() - 1)) : null;
        return new PromotionReadDtos.ProgramPage(view(page), next);
    }

    @Transactional(readOnly = true)
    public PromotionReadDtos.Program get(long programId) {
        PromotionProgram program = programs.findById(programId).orElseThrow(PromotionManagementQueryService::notFound);
        return view(List.of(program)).get(0);
    }

    /** Batch-loads current revisions and their targets for the given programmes, keeping their order. */
    List<PromotionReadDtos.Program> view(List<PromotionProgram> page) {
        List<Long> revisionIds = page.stream().map(PromotionProgram::getCurrentRevisionId).toList();
        Map<Long, PromotionRevision> revisionById = revisions.findAllById(revisionIds).stream()
                .collect(Collectors.toMap(PromotionRevision::getId, Function.identity()));
        Map<Long, List<PromotionTarget>> targetsByRevision = targets.findByRevisionIdIn(revisionIds).stream()
                .collect(Collectors.groupingBy(PromotionTarget::getRevisionId));
        return page.stream().map(program -> {
            PromotionRevision revision = revisionById.get(program.getCurrentRevisionId());
            if (revision == null) {
                throw new IllegalStateException("Programme " + program.getId() + " has no current revision");
            }
            return toDto(program, revision, targetsByRevision.getOrDefault(revision.getId(), List.of()));
        }).toList();
    }

    static PromotionReadDtos.Program toDto(PromotionProgram program, PromotionRevision revision,
                                           List<PromotionTarget> revisionTargets) {
        PromotionReadDtos.Discount discount = revision.getDiscountKind() == DiscountKind.PERCENT
                ? new PromotionReadDtos.Discount("PERCENT", LoyaltyScalarCodec.format(revision.getPercent()), null,
                        revision.getMaxDiscountAmount().toString())
                : new PromotionReadDtos.Discount("FIXED", null, revision.getFixedAmount().toString(),
                        revision.getMaxDiscountAmount().toString());
        return new PromotionReadDtos.Program(program.getId().toString(), Long.toString(program.getConfigVersion()),
                Long.toString(revision.getRevisionNo()), program.getKind().name(), program.getScope().name(),
                program.getCodeOriginal(), program.getStatus().name(), revision.getName(),
                revision.getStartAt().toString(), revision.getEndAt().toString(), discount,
                revision.getMinimumEligibleAmount().toString(), revision.isAllowPoints(), targets(revisionTargets),
                program.getTotalLimit() == null ? null : program.getTotalLimit().toString(),
                program.getMemberLimit() == null ? null : program.getMemberLimit().toString(),
                program.getCreatedAt().toString());
    }

    private static PromotionReadDtos.Targets targets(List<PromotionTarget> rows) {
        Map<TargetDimension, List<String>> keys = rows.stream()
                .sorted(Comparator.comparing(PromotionTarget::getId))
                .collect(Collectors.groupingBy(PromotionTarget::getDimension,
                        Collectors.mapping(PromotionTarget::getTargetKey, Collectors.toList())));
        return new PromotionReadDtos.Targets(keys.get(TargetDimension.PRODUCT), keys.get(TargetDimension.VARIANT),
                keys.get(TargetDimension.PACKAGE), keys.get(TargetDimension.BRANCH),
                keys.get(TargetDimension.WORKSHOP_DATE), keys.get(TargetDimension.SESSION));
    }

    private static String encodeCursor(PromotionProgram last) {
        String raw = last.getCreatedAt().toString() + "|" + last.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String[] decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 2) {
                throw invalidCursor();
            }
            return parts;
        } catch (IllegalArgumentException e) {
            throw invalidCursor();
        }
    }

    private static Instant parseCursorInstant(String raw) {
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw invalidCursor();
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String field, String raw) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            throw LoyaltyException.invalidField(field, "Unknown value.");
        }
    }

    private static LoyaltyException invalidCursor() {
        return LoyaltyException.invalidField("cursor", "The cursor is not valid.");
    }

    static LoyaltyException notFound() {
        return new LoyaltyException(LoyaltyErrorCode.PROMOTION_NOT_FOUND, "The promotion was not found.");
    }
}
