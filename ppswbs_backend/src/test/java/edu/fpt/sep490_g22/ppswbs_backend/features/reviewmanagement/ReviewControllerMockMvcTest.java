package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.controller.ReviewController;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.controller.ReviewEligibilityController;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.controller.ReviewManagementExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ContactType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.OwnerType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ReviewStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.SourceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewEligibilityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewPublicReadFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReviewControllerMockMvcTest {

    @Mock
    private ReviewApplicationService applicationService;

    @Mock
    private ReviewPublicReadFacade publicReadFacade;

    @Mock
    private ReviewEligibilityService eligibilityService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @BeforeEach
    void setUp() {
        ReviewController reviewController = new ReviewController(applicationService, publicReadFacade);
        ReviewEligibilityController eligibilityController = new ReviewEligibilityController(eligibilityService);

        mockMvc = MockMvcBuilders.standaloneSetup(reviewController, eligibilityController)
                .setControllerAdvice(new ReviewManagementExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/review-eligibility/lookup returns 200 with guest handle and sources")
    void lookupGuestEligibility_Success() throws Exception {
        GuestEligibilityLookupRequest req = GuestEligibilityLookupRequest.builder()
                .contactType(ContactType.EMAIL)
                .contactValue("test@example.com")
                .build();

        EligibleSourceListResponse resp = EligibleSourceListResponse.builder()
                .guestEligibilityHandle("handle123")
                .maskedContact("t***t@example.com")
                .expiresAt(Instant.now())
                .sources(List.of(
                        EligibleSourceDto.builder()
                                .sourceType(SourceType.WORKSHOP_ATTENDANCE)
                                .sourceReference("TICK-1")
                                .targetType(TargetType.WORKSHOP_SESSION)
                                .targetReference("SESS-1")
                                .eligible(true)
                                .alreadyReviewed(false)
                                .build()
                ))
                .build();

        when(eligibilityService.lookupGuestEligibility(any(GuestEligibilityLookupRequest.class), any(String.class)))
                .thenReturn(resp);

        mockMvc.perform(post("/api/v1/review-eligibility/lookup")
                        .header("Idempotency-Key", "lookup-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                        .andExpect(jsonPath("$.eligibilityHandle").value("handle123"))
                .andExpect(jsonPath("$.maskedContact").value("t***t@example.com"))
                .andExpect(jsonPath("$.items[0].sourceReference").value("TICK-1"));
    }

    @Test
    @DisplayName("GET /api/v1/reviews returns 200 with realtime aggregate and items")
    void getPublicReviews_Success() throws Exception {
        PublicReviewPageDto page = PublicReviewPageDto.builder()
                .aggregate(ReviewAggregateDto.builder()
                        .targetType(TargetType.WORKSHOP_SESSION)
                        .targetReference("SESS-1")
                        .count(5L)
                        .averageRating(4.8)
                        .build())
                .items(Collections.emptyList())
                .page(0)
                .size(10)
                .totalElements(5L)
                .totalPages(1)
                .hasNext(false)
                .build();

        when(publicReadFacade.getPublicReviews(eq(TargetType.WORKSHOP_SESSION), eq("SESS-1"), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/reviews")
                        .param("targetType", "WORKSHOP_SESSION")
                        .param("targetReference", "SESS-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregate.count").value(5))
                .andExpect(jsonPath("$.aggregate.averageRating").value(4.8));
    }

    @Test
    @DisplayName("POST /api/v1/reviews with invalid rating returns 400 validation error")
    void createReview_InvalidRating_Returns400() throws Exception {
        CreateReviewRequest req = CreateReviewRequest.builder()
                .sourceType(SourceType.WORKSHOP_ATTENDANCE)
                .sourceReference("TICK-1")
                .targetType(TargetType.WORKSHOP_SESSION)
                .targetReference("SESS-1")
                .rating(6) // Invalid (> 5)
                .build();

        mockMvc.perform(post("/api/v1/reviews")
                        .header("Idempotency-Key", "test-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_INPUT_INVALID"));
    }

    @Test
    @DisplayName("POST /api/v1/reviews duplicate throws 409 conflict handled by Advice")
    void createReview_Conflict_HandledByAdvice() throws Exception {
        CreateReviewRequest req = CreateReviewRequest.builder()
                .sourceType(SourceType.WORKSHOP_ATTENDANCE)
                .sourceReference("TICK-1")
                .targetType(TargetType.WORKSHOP_SESSION)
                .targetReference("SESS-1")
                .rating(5)
                .guestEligibilityHandle("guest-handle")
                .build();

        when(applicationService.createReview(any(), any(), any(String.class)))
                .thenThrow(ReviewManagementException.conflict("REVIEW_ALREADY_EXISTS", "Trải nghiệm này đã được gửi đánh giá"));

        mockMvc.perform(post("/api/v1/reviews")
                        .header("Idempotency-Key", "test-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Trải nghiệm này đã được gửi đánh giá"));
    }
}

