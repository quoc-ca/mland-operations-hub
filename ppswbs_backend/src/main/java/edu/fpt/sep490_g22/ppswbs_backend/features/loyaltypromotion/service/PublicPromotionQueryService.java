package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyScalarCodec;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionReadDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PublicPromotionDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Public promotion cards (T068, FR-011, US3.1, US3.2, spec clarification 2026-10-26). Only enabled programmes whose
 * window is active now are shown. A product programme lists only the products and sizes that are public now (facts of
 * the owning module); when none is left the programme is hidden from public lists and its detail answers 404, while a
 * Member who saved it still sees it as NO_PUBLIC_TARGET. Pages default to 20 and allow at most 100, newest first.
 */
@Service
public class PublicPromotionQueryService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    /** A card plus whether any target of a product programme is public now. */
    record Projection(PublicPromotionDtos.PublicPromotion card, boolean hasPublicTarget, PromotionRevision revision) {
    }

    private final PromotionProgramRepository programs;
    private final PromotionRevisionRepository revisions;
    private final PromotionTargetRepository targets;
    private final PromotionTargetValidationService targetFacts;
    private final Clock clock;

    public PublicPromotionQueryService(PromotionProgramRepository programs, PromotionRevisionRepository revisions,
                                       PromotionTargetRepository targets, PromotionTargetValidationService targetFacts,
                                       ObjectProvider<Clock> clock) {
        this.programs = programs;
        this.revisions = revisions;
        this.targets = targets;
        this.targetFacts = targetFacts;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    @Transactional(readOnly = true)
    public PublicPromotionDtos.PromotionPage list(Integer limit, String cursor, String scope) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw LoyaltyException.invalidField("limit", "Must be between 1 and " + MAX_LIMIT + ".");
        }
        TargetScope scopeFilter = null;
        if (scope != null) {
            try {
                scopeFilter = TargetScope.valueOf(scope);
            } catch (IllegalArgumentException e) {
                throw LoyaltyException.invalidField("scope", "Must be PRODUCT or WORKSHOP.");
            }
        }
        Instant now = clock.instant();
        Instant afterCreatedAt = null;
        Long afterId = null;
        if (cursor != null) {
            String[] parts = decode(cursor);
            afterCreatedAt = parseInstant(parts[0]);
            afterId = LoyaltyScalarCodec.parseId("cursor", parts[1]);
        }
        // Hidden programmes are skipped, so keep reading raw rows until the page is full or no rows are left.
        List<PublicPromotionDtos.PublicPromotion> cards = new ArrayList<>();
        PromotionProgram lastShown = null;
        boolean more = false;
        while (true) {
            List<PromotionProgram> rows = programs.findPublicPage(scopeFilter, now, afterCreatedAt, afterId,
                    PageRequest.of(0, size + 1));
            for (PromotionProgram row : rows) {
                Projection projection = project(row);
                if (!projection.hasPublicTarget()) {
                    continue;
                }
                if (cards.size() == size) {
                    more = true;
                    break;
                }
                cards.add(projection.card());
                lastShown = row;
            }
            if (more || rows.size() <= size) {
                break;
            }
            PromotionProgram lastRow = rows.get(rows.size() - 1);
            afterCreatedAt = lastRow.getCreatedAt();
            afterId = lastRow.getId();
        }
        return new PublicPromotionDtos.PromotionPage(cards, more ? encode(lastShown) : null);
    }

    @Transactional(readOnly = true)
    public PublicPromotionDtos.PublicPromotion get(long programId) {
        PromotionProgram program = programs.findById(programId).orElseThrow(PromotionManagementQueryService::notFound);
        Projection projection = project(program);
        if (program.getStatus() != ProgramStatus.ENABLED || !projection.revision().isActiveAt(clock.instant())
                || !projection.hasPublicTarget()) {
            throw PromotionManagementQueryService.notFound();
        }
        return projection.card();
    }

    /** Card of the current revision, with product targets reduced to those public now. */
    Projection project(PromotionProgram program) {
        PromotionRevision revision = revisions.findById(program.getCurrentRevisionId())
                .orElseThrow(() -> new IllegalStateException("Programme " + program.getId() + " has no revision"));
        List<PromotionTarget> rows = targets.findByRevisionIdOrderByDimensionAscTargetKeyAsc(revision.getId());
        List<String> productIds = keys(rows, TargetDimension.PRODUCT);
        List<String> variantIds = keys(rows, TargetDimension.VARIANT);
        boolean hasPublic = true;
        if (program.getScope() == TargetScope.PRODUCT) {
            List<TargetRef> refs = new ArrayList<>();
            productIds.forEach(key -> refs.add(new TargetRef(TargetDimension.PRODUCT, key)));
            variantIds.forEach(key -> refs.add(new TargetRef(TargetDimension.VARIANT, key)));
            List<TargetFact> facts = targetFacts.facts(TargetScope.PRODUCT, refs);
            productIds = publicKeys(facts, TargetDimension.PRODUCT);
            variantIds = publicKeys(facts, TargetDimension.VARIANT);
            hasPublic = !productIds.isEmpty() || !variantIds.isEmpty();
        }
        PromotionReadDtos.Program management = PromotionManagementQueryService.toDto(program, revision, rows);
        PromotionReadDtos.Targets shown = program.getScope() == TargetScope.PRODUCT
                ? new PromotionReadDtos.Targets(productIds, variantIds, null, null, null, null)
                : management.targets();
        PublicPromotionDtos.PublicPromotion card = new PublicPromotionDtos.PublicPromotion(management.id(),
                management.kind(), management.scope(), management.code(), management.name(), management.startAt(),
                management.endAt(), management.discount(), management.minimumEligibleAmount(), management.allowPoints(),
                shown, management.totalLimit(), management.perMemberLimit());
        return new Projection(card, hasPublic, revision);
    }

    private static List<String> keys(List<PromotionTarget> rows, TargetDimension dimension) {
        return rows.stream().filter(row -> row.getDimension() == dimension).map(PromotionTarget::getTargetKey).toList();
    }

    private static List<String> publicKeys(List<TargetFact> facts, TargetDimension dimension) {
        return facts.stream()
                .filter(fact -> fact.dimension() == dimension && fact.exists() && !fact.softDeleted() && fact.publicVisible())
                .map(TargetFact::targetKey).toList();
    }

    private static String encode(PromotionProgram last) {
        String raw = last.getCreatedAt().toString() + "|" + last.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
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
