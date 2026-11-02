package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.MagicLinkIncidentRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BookingRecoveryServiceTest {

    @Mock private WorkshopBookingRepository bookings;
    @Mock private BookingSessionCommitmentRepository commitments;
    @Mock private WorkshopBookingAuthorizationService authorization;
    @Mock private BookingIdempotencyService idempotency;
    @Mock private BookingRecoveryTransactionService transactionService;
    @Mock private ObjectMapper mapper;
    @InjectMocks private BookingRecoveryService service;

    @Test
    void rejectsCredentialMaterialInReasonBeforeAuthorizationOrPersistence() {
        MagicLinkIncidentRequest request = MagicLinkIncidentRequest.builder()
                .action("ROTATE")
                .reason("Rotate after exposure https://example.test/access#123e4567-e89b-12d3-a456-426614174000")
                .build();

        assertThatThrownBy(() -> service.handleMagicLinkIncident("Bearer manager", "BK-1",
                "magic-link-incident-0001", "corr-1", request))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("must not contain");

        verifyNoInteractions(bookings, commitments, authorization, idempotency, transactionService, mapper);
    }
}
