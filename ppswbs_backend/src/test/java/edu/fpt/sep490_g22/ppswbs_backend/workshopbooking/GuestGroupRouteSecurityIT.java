package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.1 T108 (FR-031, FR-045, FR-047, FR-048, Constitution III): the seven Guest-capable group routes the contract
 * marks {@code security: [{}]} pass the whole security chain (Spring Security and the identity gate) with no
 * {@code Authorization} header and reach their controller, which then judges the request itself. An empty body is
 * enough to prove that: the answer is a validation or business error, never 401/403. Owner-only reads and the routes
 * FR-046 removed stay closed to an anonymous caller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workshopoperations-mysql-test")
@TestPropertySource(properties = {"workshop.booking.auth.allow-test-tokens=true"})
class GuestGroupRouteSecurityIT {

    @Autowired private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/workshop-cart-quotes",
            "/api/v1/workshop-booking-groups",
            "/api/v1/workshop-booking-groups/GRP-NOPE-0001/payment-attempts",
            "/api/v1/workshop-booking-group-lookups",
            "/api/v1/workshop-booking-group-magic-links/exchanges",
            "/api/v1/workshop-booking-groups/GRP-NOPE-0001/pending-hold-release",
            "/api/v1/workshop-booking-groups/GRP-NOPE-0001/confirmation-deliveries"})
    void aGuestReachesEveryGroupRouteWithoutABearer(String path) throws Exception {
        int status = mockMvc.perform(post(path)
                        .header("Idempotency-Key", "guest-security-check-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus();
        assertThat(status).as(path).isNotIn(401, 403);
    }

    @Test
    void anOwnerReadStillNeedsAMemberBearer() throws Exception {
        mockMvc.perform(get("/api/v1/workshop-booking-groups/GRP-NOPE-0001")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/workshop-bookings",
            "/api/v1/booking-lookups",
            "/api/v1/booking-magic-links/exchanges",
            "/api/v1/workshop-bookings/WB-NOPE/payment-attempts"})
    void theRoutesFr046RemovedAreNotOpen(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
