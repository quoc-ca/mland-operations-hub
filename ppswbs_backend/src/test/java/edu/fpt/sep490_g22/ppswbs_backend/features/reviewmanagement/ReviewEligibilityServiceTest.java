package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.GuestEligibilityHandleRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dao.ReviewRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ContactType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.GuestEligibilityHandle;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.SourceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.TargetType;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.EligibleSourceListResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.dto.GuestEligibilityLookupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.EligibleSourceItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.RetailFulfilmentReviewPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility.WorkshopAttendanceReviewPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewEligibilityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewManagementException;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service.ReviewMaskingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewEligibilityServiceTest {

    @Mock
    private WorkshopAttendanceReviewPort workshopAttendancePort;

    @Mock
    private RetailFulfilmentReviewPort retailFulfilmentPort;

    @Mock
    private GuestEligibilityHandleRepository handleRepository;

    @Mock
    private ReviewRepository reviewRepository;

    private ReviewMaskingService maskingService;
    private ObjectMapper objectMapper;
    private ReviewEligibilityService eligibilityService;

    @BeforeEach
    void setUp() {
        maskingService = new ReviewMaskingService();
        objectMapper = new ObjectMapper();
        eligibilityService = new ReviewEligibilityService(
                workshopAttendancePort,
                retailFulfilmentPort,
                handleRepository,
                reviewRepository,
                maskingService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Guest lookup returns opaque handle, masked contact and eligible sources with 30-minute TTL")
    void guestLookup_Success() {
        GuestEligibilityLookupRequest req = GuestEligibilityLookupRequest.builder()
                .contactType(ContactType.EMAIL)
                .contactValue("customer@example.com")
                .build();

        EligibleSourceItem item = EligibleSourceItem.builder()
                .sourceType(SourceType.WORKSHOP_ATTENDANCE)
                .sourceReference("TICK-001")
                .targetType(TargetType.WORKSHOP_SESSION)
                .targetReference("SESS-001")
                .targetName("Latte Art Workshop")
                .completedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .eligible(true)
                .build();

        when(workshopAttendancePort.findEligibleByContact(ContactType.EMAIL, "customer@example.com"))
                .thenReturn(List.of(item));
        when(reviewRepository.findBySourceTypeAndSourceReferenceIn(eq(SourceType.WORKSHOP_ATTENDANCE), anyCollection()))
                .thenReturn(Collections.emptyList());

        EligibleSourceListResponse response = eligibilityService.lookupGuestEligibility(req);

        assertThat(response.getGuestEligibilityHandle()).isNotBlank();
        assertThat(response.getMaskedContact()).isEqualTo("c***r@example.com");
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources().get(0).getSourceReference()).isEqualTo("TICK-001");
        assertThat(response.getSources().get(0).isAlreadyReviewed()).isFalse();

        ArgumentCaptor<GuestEligibilityHandle> captor = ArgumentCaptor.forClass(GuestEligibilityHandle.class);
        verify(handleRepository).save(captor.capture());
        GuestEligibilityHandle saved = captor.getValue();
        assertThat(saved.getContactType()).isEqualTo(ContactType.EMAIL);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(25, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("Validate guest handle rejects expired handle with 401 REVIEW_GUEST_HANDLE_EXPIRED")
    void validateGuestHandle_Expired() {
        String rawHandle = "abc123xyz456";
        String hash = eligibilityService.hashHandle(rawHandle);

        GuestEligibilityHandle expiredHandle = GuestEligibilityHandle.builder()
                .handleHash(hash)
                .contactType(ContactType.EMAIL)
                .contactValueMasked("c***@test.com")
                .sourceReferencesJson("[\"TICK-001\"]")
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();

        when(handleRepository.findByHandleHash(hash)).thenReturn(Optional.of(expiredHandle));

        assertThatThrownBy(() -> eligibilityService.validateGuestHandle(rawHandle, "TICK-001"))
                .isInstanceOf(ReviewManagementException.class)
                .hasMessageContaining("Mã handle đã hết hạn");
    }

    @Test
    @DisplayName("Validate guest handle rejects unknown reference outside session with REVIEW_SOURCE_NOT_ELIGIBLE")
    void validateGuestHandle_ReferenceNotAllowed() {
        String rawHandle = "abc123xyz456";
        String hash = eligibilityService.hashHandle(rawHandle);

        GuestEligibilityHandle handle = GuestEligibilityHandle.builder()
                .handleHash(hash)
                .contactType(ContactType.EMAIL)
                .contactValueMasked("c***@test.com")
                .sourceReferencesJson("[\"TICK-001\"]")
                .expiresAt(Instant.now().plus(20, ChronoUnit.MINUTES))
                .build();

        when(handleRepository.findByHandleHash(hash)).thenReturn(Optional.of(handle));

        assertThatThrownBy(() -> eligibilityService.validateGuestHandle(rawHandle, "TICK-999"))
                .isInstanceOf(ReviewManagementException.class)
                .hasMessageContaining("Mã tham chiếu không thuộc phiên tra cứu");
    }
}
