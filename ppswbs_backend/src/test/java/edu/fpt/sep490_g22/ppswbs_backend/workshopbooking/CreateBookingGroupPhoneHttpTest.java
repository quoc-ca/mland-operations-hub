package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.WorkshopBookingExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.WorkshopBookingGroupController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingGroupCreationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyClaim;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 003.1 FR-030: a malformed contactPhone is a 400 VALIDATION_ERROR on create-booking-group; a valid one passes. */
class CreateBookingGroupPhoneHttpTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        BookingGroupCreationService creation = mock(BookingGroupCreationService.class);
        BookingIdempotencyService idempotency = mock(BookingIdempotencyService.class);
        BookingIdempotencyClaim claim = mock(BookingIdempotencyClaim.class);
        when(claim.cachedOutcome()).thenReturn(false);
        when(idempotency.acquireClaim(anyString(), anyString(), anyString(), anyString())).thenReturn(claim);
        when(creation.requireBuyer(any(), anyString())).thenReturn(null);
        mvc = MockMvcBuilders.standaloneSetup(new WorkshopBookingGroupController(creation, idempotency,
                        new ObjectMapper()))
                .setControllerAdvice(new WorkshopBookingExceptionHandler()).build();
    }

    private org.springframework.test.web.servlet.ResultActions submit(String phone) throws Exception {
        return mvc.perform(post("/api/v1/workshop-booking-groups")
                .header("Idempotency-Key", "0123456789abcdef0123").accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quoteRef\":\"q-1\",\"contactEmail\":\"a@b.test\",\"contactPhone\":\"" + phone + "\"}"));
    }

    @Test
    void aMalformedPhoneAnswers400ValidationError() throws Exception {
        for (String bad : new String[] {"0338905173", "84338905173", "+99338905173", "+8433890517",
                "+843389051731", "+1415555267"}) {
            submit(bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void aValidInternationalPhonePassesValidation() throws Exception {
        for (String good : new String[] {"+84338905173", "+14155552671"}) {
            submit(good).andExpect(status().isCreated());
        }
    }
}
