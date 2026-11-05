package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewAggregateProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewReplyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.Review;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewReply;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.PublicReviewPageDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReplyDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewAggregateDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.ReviewDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewPublicReadServiceImpl implements ReviewPublicReadFacade {

    private final ReviewRepository reviewRepository;
    private final ReviewReplyRepository replyRepository;
    private final ReviewApplicationService applicationService;

    @Override
    @Transactional(readOnly = true)
    public PublicReviewPageDto getPublicReviews(TargetType targetType, String targetReference, Pageable pageable) {
        if (targetType == null || targetReference == null || targetReference.isBlank()) {
            throw ReviewManagementException.badRequest("REVIEW_INPUT_INVALID", "targetType và targetReference là bắt buộc.");
        }

        String targetRef = targetReference.trim();
        ReviewAggregateDto aggregate = getRealtimeAggregate(targetType, targetRef);

        Page<Review> page = reviewRepository.findByTargetTypeAndTargetReferenceAndStatus(
                targetType, targetRef, ReviewStatus.PUBLIC, pageable);

        if (page.isEmpty()) {
            return PublicReviewPageDto.builder()
                    .aggregate(aggregate)
                    .items(Collections.emptyList())
                    .page(page.getNumber())
                    .size(page.getSize())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .hasNext(false)
                    .build();
        }

        List<String> reviewIds = page.getContent().stream().map(Review::getId).toList();
        List<ReviewReply> replies = replyRepository.findByReviewIdIn(reviewIds);
        Map<String, ReplyDto> replyMap = replies.stream()
                .map(reply -> applicationService.mapToReplyDto(reply, false))
                .collect(Collectors.toMap(ReplyDto::getReviewId, Function.identity(), (a, b) -> a));

        List<ReviewDto> dtos = page.getContent().stream()
                .map(r -> applicationService.mapToReviewDto(r, replyMap.get(r.getId())))
                .collect(Collectors.toList());

        return PublicReviewPageDto.builder()
                .aggregate(aggregate)
                .items(dtos)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewAggregateDto getRealtimeAggregate(TargetType targetType, String targetReference) {
        if (targetType == null || targetReference == null || targetReference.isBlank()) {
            return ReviewAggregateDto.builder()
                    .targetType(targetType)
                    .targetReference(targetReference)
                    .count(0L)
                    .averageRating(0.0)
                    .build();
        }

        ReviewAggregateProjection proj = reviewRepository.getAggregateByTargetAndStatus(
                targetType, targetReference.trim(), ReviewStatus.PUBLIC);

        long count = proj != null && proj.getTotalCount() != null ? proj.getTotalCount() : 0L;
        double avg = 0.0;
        if (proj != null && proj.getAverageRating() != null && count > 0) {
            avg = Math.round(proj.getAverageRating() * 10.0) / 10.0;
        }

        return ReviewAggregateDto.builder()
                .targetType(targetType)
                .targetReference(targetReference.trim())
                .count(count)
                .averageRating(avg)
                .build();
    }
}

