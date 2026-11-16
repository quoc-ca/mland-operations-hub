package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.WorkshopBookingExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.WorkshopBookingGroupController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 003.1 T138 (FR-013) and T140 (FR-031): the typed 409 codes of the group commands, through the real handler. */
class GroupCommandErrorContractTest {

    private static final String KEY = "0123456789abcdef0123";
    private static final String BODY = "{\"quoteRef\":\"Q-1\",\"contactEmail\":\"buyer@example.com\",\"contactPhone\":\"+84901234567\"}";

    private BookingGroupCreationService creation;
    private BookingIdempotencyService idempotency;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        creation = mock(BookingGroupCreationService.class);
        idempotency = mock(BookingIdempotencyService.class);
        mvc = MockMvcBuilders.standaloneSetup(new WorkshopBookingGroupController(creation, idempotency, new ObjectMapper()))
                .setControllerAdvice(new WorkshopBookingExceptionHandler()).build();
        when(creation.requireBuyer(any(), any())).thenReturn(null);
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(BookingIdempotencyClaim.live(1L, 1L));
    }

    /** T140: expired, already accepted and changed quotes answer 409 with their own code and create no group. */
    @ParameterizedTest
    @ValueSource(strings = {"QUOTE_EXPIRED", "QUOTE_ALREADY_ACCEPTED", "QUOTE_CHANGED"})
    void aQuoteErrorAnswers409WithItsOwnCode(String code) throws Exception {
        when(creation.create(any(), any(), anyString(), any()))
                .thenThrow(new BookingCreationRejectedException(code, "refused"));

        mvc.perform(post("/api/v1/workshop-booking-groups").header("Idempotency-Key", KEY)
                        .accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code));
    }

    /** T138: a reused key with different content on a group command answers 409 IDEMPOTENCY_CONFLICT. */
    @Test
    void aReusedKeyWithDifferentContentAnswersIdempotencyConflict() throws Exception {
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new BookingIdempotencyConflictException("Idempotency-Key was used with different content"));

        mvc.perform(post("/api/v1/workshop-booking-groups").header("Idempotency-Key", KEY)
                        .accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        verify(creation, never()).create(any(), any(), anyString(), any());
    }

    /** T138: the seven group command entry points all claim the key, and the retired code name is absent. */
    @Test
    void everyGroupCommandClaimsTheKeyAndNoRetiredCodeRemains() throws Exception {
        Path main = Path.of("src/main/java/edu/fpt/sep490_g22/ppswbs_backend/features/workshopbooking");
        for (String file : new String[] {"controller/WorkshopBookingGroupController.java",
                "service/GroupPaymentService.java", "controller/BookingGroupRecoveryController.java",
                "controller/LatePaymentReviewController.java", "controller/TicketRecipientController.java"}) {
            assertThat(Files.readString(main.resolve(file))).as(file).contains("acquireClaim");
        }
        String recovery = Files.readString(main.resolve("controller/BookingGroupRecoveryController.java"));
        for (String action : new String[] {"RELEASE_GROUP_PENDING_HOLD", "RESEND_GROUP_CONFIRMATION", "GROUP_MAGIC_LINK_INCIDENT"}) {
            assertThat(recovery).contains(action);
        }
        try (var files = Files.walk(main)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(path)).as(path.toString()).doesNotContain("IDEMPOTENCY_KEY_REUSED");
            }
        }
    }

    /** T146: POST /workshop-cart-quotes answers 503 DEPENDENCY_UNAVAILABLE (typed, no booking data) for any cart. */
    @Test
    void theCartQuoteRouteAnswers503DependencyUnavailable() throws Exception {
        var quotes = mock(WorkshopCartQuoteService.class);
        when(quotes.quote(any(), any())).thenThrow(new WorkshopBookingProblemException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "down"));
        MockMvc quoteMvc = MockMvcBuilders.standaloneSetup(
                        new edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.WorkshopCartQuoteController(quotes))
                .setControllerAdvice(new WorkshopBookingExceptionHandler()).build();
        String body = "{\"lines\":[{\"packageId\":7,\"sessionReferences\":[\"" + java.util.UUID.randomUUID()
                + "\"],\"participantCount\":2}]}";

        quoteMvc.perform(post("/api/v1/workshop-cart-quotes").accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }
}
