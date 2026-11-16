package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.GroupPaymentController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.WorkshopBookingExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingBuyerVerificationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 003.1 T137 (FR-017a): both or neither contact value is 400 VALIDATION_ERROR on every Guest route, with one safe answer. */
class ContactProofValidationTest {

    @Test
    void theBuyerVerificationRequestRefusesBothOrNeitherContactValue() {
        BuyerVerificationService service = new BuyerVerificationService(mock(BookingGroupRepository.class),
                mock(BookingBuyerVerificationRepository.class), mock(GroupNotificationService.class),
                Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofSeconds(60), 5, 5);

        assertThatThrownBy(() -> service.requestChallenge("GRP-X", "a@b.test", "0900000001", "corr"))
                .isInstanceOf(BookingValidationException.class);
        assertThatThrownBy(() -> service.requestChallenge("GRP-X", null, null, "corr"))
                .isInstanceOf(BookingValidationException.class);
        assertThatThrownBy(() -> service.requestChallenge("GRP-X", " ", "", "corr"))
                .isInstanceOf(BookingValidationException.class);
    }

    @Test
    void aValidationRefusalAnswers400WithTheSameCodeOnTheRouteBoundary() throws Exception {
        GroupPaymentService payments = mock(GroupPaymentService.class);
        BookingGroupRecoveryService recovery = mock(BookingGroupRecoveryService.class);
        when(recovery.resolveActor(any())).thenReturn(new BookingGroupRecoveryService.Actor(null));
        when(payments.initiate(anyString(), anyString(), any(), any(), any(), any()))
                .thenThrow(new BookingValidationException("Exactly one of contactEmail or contactPhone is required"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new GroupPaymentController(payments, recovery,
                        mock(GroupRecoveryRateLimiter.class)))
                .setControllerAdvice(new WorkshopBookingExceptionHandler()).build();

        for (String body : new String[] {"{\"contactEmail\":\"a@b.test\",\"contactPhone\":\"0900000001\"}", "{}"}) {
            mvc.perform(post("/api/v1/workshop-booking-groups/GRP-X/payment-attempts")
                            .header("Idempotency-Key", "0123456789abcdef0123").accept(MediaType.APPLICATION_JSON)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }
}
