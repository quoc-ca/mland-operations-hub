package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.MediaReference;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ReviewState;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T009–T014 entity invariants that do not need a database. */
class CatalogueEntityRulesTests {

    private static final Instant NOW = Instant.parse("2026-10-26T03:00:00.123456Z");

    @Nested
    class Categories {

        @Test
        void newCategoryIsActiveTrimmedAndKeyedCaseInsensitively() {
            ProductCategory category = ProductCategory.create("  Nhẫn Cưới ", "   ");
            assertThat(category.getName()).isEqualTo("Nhẫn Cưới");
            assertThat(category.getDescription()).isNull();
            assertThat(category.isActive()).isTrue();
            assertThat(Arrays.equals(category.getNameKey(), ProductCategory.create("NHẪN CƯỚI", null).getNameKey()))
                    .isTrue();
        }

        @Test
        void sameActivationIsNoOp() {
            ProductCategory category = ProductCategory.create("Nhẫn bạc", null);
            assertThat(category.setActive(true)).isFalse();
            assertThat(category.setActive(false)).isTrue();
            assertThat(category.isActive()).isFalse();
        }

        @Test
        void invalidNameIsRejected() {
            assertThatThrownBy(() -> ProductCategory.create("x".repeat(201), null))
                    .isInstanceOf(CatalogueException.class);
        }
    }

    @Nested
    class PriceProposals {

        private PriceProposal draft() {
            return PriceProposal.draft(10L, null, 1, BigInteger.valueOf(100_000), 0, "a".repeat(64), null);
        }

        @Test
        void draftIsEffectiveVndWithoutDecision() {
            PriceProposal proposal = draft();
            assertThat(proposal.getState()).isEqualTo(ReviewState.DRAFT);
            assertThat(proposal.isEffective()).isTrue();
            assertThat(proposal.getCurrency()).isEqualTo("VND");
            assertThat(proposal.getApprovedAmount()).isNull();
            assertThat(proposal.getSubmittedAt()).isNull();
        }

        @Test
        void managerMayAdjustTheFinalAmount() {
            PriceProposal proposal = draft();
            proposal.submit("staff-1", NOW);
            assertThat(proposal.isCurrentPending()).isTrue();
            proposal.approve("manager-1", NOW, BigInteger.valueOf(95_000));
            assertThat(proposal.getState()).isEqualTo(ReviewState.APPROVED);
            assertThat(proposal.getApprovedAmount()).isEqualTo(BigInteger.valueOf(95_000));
            assertThat(proposal.getReason()).isNull();
        }

        @Test
        void selfApprovalIsAllowed() {
            PriceProposal proposal = draft();
            proposal.submit("manager-1", NOW);
            proposal.approve("manager-1", NOW, BigInteger.valueOf(100_000));
            assertThat(proposal.getDecidedBy()).isEqualTo("manager-1");
        }

        @Test
        void rejectionNeedsAReasonAndRecordsNoAmount() {
            PriceProposal proposal = draft();
            proposal.submit("staff-1", NOW);
            assertThatThrownBy(() -> proposal.reject("manager-1", NOW, "  "))
                    .isInstanceOf(CatalogueException.class);
            proposal.reject("manager-1", NOW, " Giá quá thấp ");
            assertThat(proposal.getState()).isEqualTo(ReviewState.REJECTED);
            assertThat(proposal.getReason()).isEqualTo("Giá quá thấp");
            assertThat(proposal.getApprovedAmount()).isNull();
        }

        @Test
        void onlyCurrentPendingCanBeDecided() {
            PriceProposal proposal = draft();
            assertCode(() -> proposal.approve("manager-1", NOW, BigInteger.TEN), CatalogueErrorCode.STALE_VERSION);
            proposal.submit("staff-1", NOW);
            proposal.makeIneffective();
            assertCode(() -> proposal.approve("manager-1", NOW, BigInteger.TEN), CatalogueErrorCode.STALE_VERSION);
        }

        @Test
        void amountMustBePositive() {
            assertThatThrownBy(() -> PriceProposal.draft(10L, null, 1, BigInteger.ZERO, 0, "d", null))
                    .isInstanceOf(CatalogueException.class);
        }
    }

    @Nested
    class Reviews {

        @Test
        void draftHasNoSnapshotUntilSubmitted() {
            ProductReview review = ProductReview.draft(1L, 1, 0);
            assertThat(review.getSnapshot()).isNull();
            review.submit("staff-1", NOW, "{\"name\":\"Nhẫn\"}");
            assertThat(review.getState()).isEqualTo(ReviewState.PENDING);
            assertThat(review.getSnapshot()).contains("Nhẫn");
        }

        @Test
        void rejectionReasonUpToTwoThousand() {
            ProductReview review = ProductReview.draft(1L, 1, 0);
            review.submit("staff-1", NOW, "{}");
            assertThatThrownBy(() -> review.reject("manager-1", NOW, "x".repeat(2001)))
                    .isInstanceOf(CatalogueException.class);
            review.reject("manager-1", NOW, "x".repeat(2000));
            assertThat(review.getState()).isEqualTo(ReviewState.REJECTED);
        }

        @Test
        void ineffectiveRevisionKeepsDecisionButIsNotCurrent() {
            ProductReview review = ProductReview.draft(1L, 1, 0);
            review.submit("staff-1", NOW, "{}");
            review.approve("manager-1", NOW);
            assertThat(review.isEffectiveApproval()).isTrue();
            review.makeIneffective();
            assertThat(review.getState()).isEqualTo(ReviewState.APPROVED);
            assertThat(review.isEffectiveApproval()).isFalse();
            assertCode(() -> review.approve("manager-1", NOW), CatalogueErrorCode.REVIEW_NOT_CURRENT);
        }
    }

    @Nested
    class Media {

        private CatalogueMediaObject staged() {
            return CatalogueMediaObject.stage(UUID.randomUUID(), "catalogue/x", "c".repeat(64), "image/webp",
                    5_000_000L, "staff-1", UUID.randomUUID(), NOW);
        }

        @Test
        void onlyJpegPngWebpUpToFiveMillionBytes() {
            assertCode(() -> CatalogueMediaObject.stage(UUID.randomUUID(), "k", "c", "image/gif", 10, "a",
                    UUID.randomUUID(), NOW), CatalogueErrorCode.MEDIA_TYPE_UNSUPPORTED);
            assertCode(() -> CatalogueMediaObject.stage(UUID.randomUUID(), "k", "c", "image/png", 5_000_001L, "a",
                    UUID.randomUUID(), NOW), CatalogueErrorCode.MEDIA_TOO_LARGE);
            assertThat(staged().getState()).isEqualTo(CatalogueMediaObject.State.STAGED);
        }

        @Test
        void providerFieldsRequiredBeforeReady() {
            CatalogueMediaObject media = staged();
            assertThat(media.canBeAttached()).isFalse();
            assertThatThrownBy(() -> media.markReady("asset", null, "webp", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            media.markReady("asset", "1", "webp", NOW);
            assertThat(media.canBeAttached()).isTrue();
        }

        @Test
        void firstAttachTimeIsKeptAndDeletePendingBlocksAttach() {
            CatalogueMediaObject media = staged();
            media.markReady("asset", "1", "webp", NOW);
            media.recordAttached(NOW);
            media.recordAttached(NOW.plusSeconds(60));
            assertThat(media.getFirstAttachedAt()).isEqualTo(NOW);
            media.requestDelete(NOW.plusSeconds(120));
            assertThat(media.getState()).isEqualTo(CatalogueMediaObject.State.DELETE_PENDING);
            assertCode(() -> media.recordAttached(NOW), CatalogueErrorCode.MEDIA_NOT_FOUND);
            media.markDeleted(NOW.plusSeconds(180));
            assertThat(media.getState()).isEqualTo(CatalogueMediaObject.State.DELETED);
        }

        @Test
        void imagePositionIsZeroToNineWithRequiredAltText() {
            assertThat(ProductImage.attach(1L, UUID.randomUUID(), " Mặt trước ", 9).getAltText()).isEqualTo("Mặt trước");
            assertThatThrownBy(() -> ProductImage.attach(1L, UUID.randomUUID(), "a", 10))
                    .isInstanceOf(CatalogueException.class);
            assertThatThrownBy(() -> ProductImage.attach(1L, UUID.randomUUID(), " ", 0))
                    .isInstanceOf(CatalogueException.class);
        }

        @Test
        void releasingAReferenceIsIdempotent() {
            MediaReference reference = MediaReference.link(UUID.randomUUID(), "productmanagement", "product:1", NOW);
            assertThat(reference.isActive()).isTrue();
            reference.release(NOW.plusSeconds(1));
            reference.release(NOW.plusSeconds(99));
            assertThat(reference.getReleasedAt()).isEqualTo(NOW.plusSeconds(1));
        }
    }

    @Nested
    class Audit {

        @Test
        void safeChangesMayHoldNullValuesAndAreCopied() {
            Map<String, Object> changes = new HashMap<>();
            changes.put("description", null);
            CatalogueAuditEvent event = new CatalogueAuditEvent("staff-1", "category.update", "category", "5", 2L,
                    null, List.of("description"), changes, CatalogueAuditEvent.Outcome.SUCCESS, "corr", NOW);
            changes.put("name", "changed later");
            assertThat(event.getSafeChanges()).containsOnlyKeys("description");
            assertThat(event.getOutcome()).isEqualTo(CatalogueAuditEvent.Outcome.SUCCESS);
        }
    }

    private static void assertCode(Runnable action, CatalogueErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CatalogueException.class, error -> assertThat(error.code()).isEqualTo(code));
    }
}
