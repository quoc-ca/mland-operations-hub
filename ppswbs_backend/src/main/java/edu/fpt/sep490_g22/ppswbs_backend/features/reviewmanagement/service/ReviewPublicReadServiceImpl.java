package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewAggregateProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewReplyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.Review;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewReply;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.PublicReviewPageDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.PublicReviewQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReplyDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewAggregateDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewDto;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewPublicReadServiceImpl implements ReviewPublicReadFacade {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ReviewRepository reviewRepository;
    private final ReviewReplyRepository replyRepository;
    private final ReviewApplicationService applicationService;

    @Override
    @Transactional(readOnly = true)
    public PublicReviewPageDto getPublicReviews(PublicReviewQuery query) {
        PublicReviewQuery q = query == null ? new PublicReviewQuery(null, null, null, null, null, null) : query;
        if (q.rating() != null && (q.rating() < 1 || q.rating() > 5)) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "rating phải từ 1 đến 5.");
        }
        int limit = q.limit() == null ? PublicReviewQuery.DEFAULT_LIMIT
                : Math.max(1, Math.min(q.limit(), PublicReviewQuery.MAX_LIMIT));
        String targetRef = blankToNull(q.targetReference());
        Cursor cursor = decodeCursor(q.cursor());

        // The aggregate is the target scope across all ratings, so the headline average does not move with the rating filter.
        ReviewAggregateDto aggregate = aggregate(q.targetType(), targetRef, q.packageId());

        Specification<Review> filtered = scope(q.targetType(), targetRef, q.packageId(), q.rating());
        long total = reviewRepository.count(filtered);

        final Sort finalSort;
        if ("best_to_worse".equalsIgnoreCase(q.sort())) {
            finalSort = Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        } else if ("worse_to_best".equalsIgnoreCase(q.sort())) {
            finalSort = Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        } else {
            finalSort = NEWEST_FIRST;
        }

        // Keyset paging on (createdAt, id): a review published while the customer scrolls cannot repeat or skip a row.
        Specification<Review> pageSpec = cursor == null ? filtered : filtered.and(after(cursor));
        List<Review> rows = reviewRepository.findBy(pageSpec,
                fluent -> fluent.sortBy(finalSort).limit(limit + 1).all());
        boolean hasNext = rows.size() > limit;
        List<Review> pageRows = hasNext ? rows.subList(0, limit) : rows;

        List<ReviewDto> items = toDtos(pageRows);
        String nextCursor = hasNext ? encodeCursor(pageRows.get(pageRows.size() - 1)) : null;
        return PublicReviewPageDto.builder()
                .aggregate(aggregate)
                .items(items)
                .nextCursor(nextCursor)
                .page(0)
                .size(limit)
                .totalElements(total)
                .totalPages((int) Math.ceil(total / (double) limit))
                .hasNext(hasNext)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewAggregateDto getRealtimeAggregate(TargetType targetType, String targetReference) {
        return aggregate(targetType, blankToNull(targetReference), null);
    }

    private ReviewAggregateDto aggregate(TargetType targetType, String targetRef, Long packageId) {
        ReviewAggregateProjection proj = reviewRepository.getAggregateByOptionalTarget(targetType, targetRef, packageId,
                ReviewStatus.PUBLIC);
        long count = proj != null && proj.getTotalCount() != null ? proj.getTotalCount() : 0L;
        double avg = 0.0;
        if (proj != null && proj.getAverageRating() != null && count > 0) {
            avg = Math.round(proj.getAverageRating() * 10.0) / 10.0;
        }
        return ReviewAggregateDto.builder()
                .targetType(targetType)
                .targetReference(targetRef)
                .count(count)
                .averageRating(avg)
                .build();
    }

    private List<ReviewDto> toDtos(List<Review> reviews) {
        if (reviews.isEmpty())
            return Collections.emptyList();
        List<String> reviewIds = reviews.stream().map(Review::getId).toList();
        List<ReviewReply> replies = replyRepository.findByReviewIdIn(reviewIds);
        Map<String, ReplyDto> replyMap = replies.stream()
                .map(reply -> applicationService.mapToReplyDto(reply, false))
                .collect(Collectors.toMap(ReplyDto::getReviewId, Function.identity(), (a, b) -> a));
        return reviews.stream()
                .map(r -> applicationService.mapToReviewDto(r, replyMap.get(r.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Only PUBLIC reviews, and only those matching the optional target and rating
     * filters.
     */
    private static Specification<Review> scope(TargetType targetType, String targetReference, Long packageId,
            Integer rating) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("status"), ReviewStatus.PUBLIC));
            if (targetType != null)
                all.add(cb.equal(root.get("targetType"), targetType));
            if (targetReference != null)
                all.add(cb.equal(root.get("targetReference"), targetReference));
            if (packageId != null)
                all.add(cb.equal(root.get("packageId"), packageId));
            if (rating != null)
                all.add(cb.equal(root.get("rating"), rating));
            return cb.and(all.toArray(new Predicate[0]));
        };
    }

    /** Rows strictly after the cursor in newest-first order. */
    private static Specification<Review> after(Cursor cursor) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.<Instant>get("createdAt"), cursor.createdAt()),
                cb.and(cb.equal(root.<Instant>get("createdAt"), cursor.createdAt()),
                        cb.lessThan(root.<String>get("id"), cursor.id())));
    }

    private record Cursor(Instant createdAt, String id) {
    }

    static String encodeCursor(Review last) {
        String raw = last.getCreatedAt().toString() + "|" + last.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String value) {
        if (value == null || value.isBlank())
            return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value.trim()), StandardCharsets.UTF_8);
            int bar = raw.indexOf('|');
            if (bar <= 0 || bar == raw.length() - 1)
                throw new IllegalArgumentException("malformed");
            return new Cursor(Instant.parse(raw.substring(0, bar)), raw.substring(bar + 1));
        } catch (IllegalArgumentException | DateTimeParseException invalid) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "cursor không hợp lệ.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
