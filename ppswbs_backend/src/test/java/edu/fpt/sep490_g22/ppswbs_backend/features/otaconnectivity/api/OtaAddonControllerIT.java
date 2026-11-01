package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAddonService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade.OtaAddonSettlementPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade.OtaAddonSettlementPort.DirectBillCreationOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaAddonBillLink;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaAddonBillLinkRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaMappingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("003.4 T049: OTA Add-on REST Controller Integration Tests")
class OtaAddonControllerIT {

    @Mock private AccountFacade accountFacade;
    @Mock private OtaChannelBookingRepository bookingRepository;
    @Mock private OtaAddonBillLinkRepository addonBillLinkRepository;
    @Mock private OtaAddonSettlementPort settlementPort;

    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Map<String, OtaAddonBillLink> linkStore = new HashMap<>();
    private OtaChannelBooking booking;
    private final String bookingUuid = UUID.randomUUID().toString();
    private final Long locationId = 1001L;

    private static final String STAFF_AUTH = "Bearer test-uid:staff-101";
    private static final String OTHER_STAFF_AUTH = "Bearer test-uid:staff-999";
    private static final String MEMBER_AUTH = "Bearer test-uid:member-202";

    @BeforeEach
    void setUp() {
        linkStore.clear();

        when(addonBillLinkRepository.save(any(OtaAddonBillLink.class))).thenAnswer(invocation -> {
            OtaAddonBillLink link = invocation.getArgument(0);
            linkStore.put(link.getSettlementQuoteReference(), link);
            return link;
        });

        when(addonBillLinkRepository.findBySettlementQuoteReference(any())).thenAnswer(invocation -> {
            String ref = invocation.getArgument(0);
            return Optional.ofNullable(linkStore.get(ref));
        });

        OtaAuthorizationService authorizationService = new OtaAuthorizationService(accountFacade);
        ReflectionTestUtils.setField(authorizationService, "allowTestTokens", true);

        OtaAddonService addonService = new OtaAddonService(bookingRepository, addonBillLinkRepository, settlementPort);
        OtaAddonController controller = new OtaAddonController(authorizationService, addonService);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new OtaExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();

        // Setup Accounts: Staff 101 with location scope [1001], Staff 999 with [9999], Member 202
        AccountSummary staff101 = new AccountSummary(101L, ActorRole.STAFF, ActorStatus.ACTIVE, "staff@mland.test", Instant.now(), Instant.now());
        when(accountFacade.resolveAccount("staff-101")).thenReturn(Optional.of(staff101));
        when(accountFacade.getLocationScope(101L)).thenReturn(List.of(locationId));

        AccountSummary staff999 = new AccountSummary(999L, ActorRole.STAFF, ActorStatus.ACTIVE, "other@mland.test", Instant.now(), Instant.now());
        when(accountFacade.resolveAccount("staff-999")).thenReturn(Optional.of(staff999));
        when(accountFacade.getLocationScope(999L)).thenReturn(List.of(9999L));

        AccountSummary member202 = new AccountSummary(202L, ActorRole.MEMBER, ActorStatus.ACTIVE, "member@mland.test", Instant.now(), Instant.now());
        when(accountFacade.resolveAccount("member-202")).thenReturn(Optional.of(member202));

        // Setup Booking fixture
        OtaChannelMapping mapping = OtaChannelMapping.builder()
                .id(1L)
                .connection(OtaChannelConnection.builder()
                        .providerCode(OtaProvider.KLOOK)
                        .environment(OtaEnvironment.TEST)
                        .build())
                .offerReference("offer-ring-basic")
                .sessionReference("session-20261101")
                .locationReference(locationId.toString())
                .externalProductId("klook-prod-01")
                .externalOptionId("klook-opt-01")
                .status(OtaMappingStatus.BOOKING_ENABLED)
                .build();

        booking = OtaChannelBooking.capture(
                mapping,
                "ext-booking-ref-42",
                "CONFIRMED",
                1,
                "mland-bk-42",
                "KLOOK",
                "policy-standard",
                "v1",
                Instant.now(),
                "Asia/Ho_Chi_Minh",
                100_000L,
                80_000L,
                15_000L,
                5_000L,
                100_000L,
                "VND"
        );
        ReflectionTestUtils.setField(booking, "publicReference", bookingUuid);
        when(bookingRepository.findByPublicReference(bookingUuid)).thenReturn(Optional.of(booking));
    }

    @Nested
    @DisplayName("Endpoint 1: POST /api/v1/ota-channel-bookings/{channelBookingId}/add-on-quotes")
    class CreateQuoteTests {

        @Test
        @DisplayName("Free engraving: Zero charge succeeds with 201 Created and status RECORDED_FREE")
        void freeLaserEngraving_zeroCharge_succeeds201() throws Exception {
            String requestJson = """
                {
                    "addonType": "LASER_ENGRAVING",
                    "itemDescription": "Complimentary Name Laser Engraving",
                    "amount": {
                        "amount": "0",
                        "currency": "VND"
                    }
                }
                """;

            mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/add-on-quotes")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.quoteId").isNotEmpty())
                    .andExpect(jsonPath("$.channelBookingId").value(bookingUuid))
                    .andExpect(jsonPath("$.addonType").value("LASER_ENGRAVING"))
                    .andExpect(jsonPath("$.amount.amount").value("0"))
                    .andExpect(jsonPath("$.amount.currency").value("VND"))
                    .andExpect(jsonPath("$.status").value("RECORDED_FREE"));
        }

        @Test
        @DisplayName("Free engraving: Non-zero charge is strictly rejected with 422 Unprocessable")
        void freeLaserEngraving_nonZeroCharge_rejected422() throws Exception {
            String requestJson = """
                {
                    "addonType": "LASER_ENGRAVING",
                    "itemDescription": "Engraving with illegal fee",
                    "amount": {
                        "amount": "50000",
                        "currency": "VND"
                    }
                }
                """;

            mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/add-on-quotes")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("UNPROCESSABLE"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("free of charge")));
        }

        @Test
        @DisplayName("Paid option: Positive amount and itemCode succeeds with 201 Created and status QUOTED")
        void paidOption_validPositiveAmount_succeeds201() throws Exception {
            String requestJson = """
                {
                    "addonType": "PAID_OPTION",
                    "itemCode": "STONE-GEM-RUBY",
                    "itemDescription": "Natural Ruby Accent Inlay",
                    "amount": {
                        "amount": "250000",
                        "currency": "VND"
                    }
                }
                """;

            mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/add-on-quotes")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.quoteId").isNotEmpty())
                    .andExpect(jsonPath("$.channelBookingId").value(bookingUuid))
                    .andExpect(jsonPath("$.addonType").value("PAID_OPTION"))
                    .andExpect(jsonPath("$.itemCode").value("STONE-GEM-RUBY"))
                    .andExpect(jsonPath("$.amount.amount").value("250000"))
                    .andExpect(jsonPath("$.amount.currency").value("VND"))
                    .andExpect(jsonPath("$.status").value("QUOTED"));
        }

        @Test
        @DisplayName("Paid option: Missing itemCode is rejected with 400 Bad Request")
        void paidOption_missingItemCode_rejected400() throws Exception {
            String requestJson = """
                {
                    "addonType": "PAID_OPTION",
                    "itemDescription": "Option missing code",
                    "amount": {
                        "amount": "250000",
                        "currency": "VND"
                    }
                }
                """;

            mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/add-on-quotes")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Paid option: Zero or negative amount is rejected with 422 Unprocessable")
        void paidOption_zeroAmount_rejected422() throws Exception {
            String requestJson = """
                {
                    "addonType": "PAID_OPTION",
                    "itemCode": "OPT-TEST",
                    "itemDescription": "Free paid option",
                    "amount": {
                        "amount": "0",
                        "currency": "VND"
                    }
                }
                """;

            mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/add-on-quotes")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("UNPROCESSABLE"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("positive amount")));
        }

        @Test
        @DisplayName("Currency: Non-VND currency is rejected with 422 Unprocessable")
        void nonVndCurrency_rejected422() throws Exception {
            String requestJson = """
                {
                    "addonType": "PAID_OPTION",
                    "itemCode": "OPT-USD",
                    "itemDescription": "Foreign currency option",
                    "amount": {
                        "amount": "20",
                        "currency": "USD"
                    }
                }
                """;

            mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/add-on-quotes")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("UNPROCESSABLE"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Only VND currency")));
        }

        @Test
        @DisplayName("Authorization: Missing token returns 401 Unauthorized")
        void missingToken_returns401() throws Exception {
            String requestJson = """
                {
                    "addonType": "LASER_ENGRAVING",
                    "itemDescription": "Laser",
                    "amount": { "amount": "0", "currency": "VND" }
                }
                """;

            mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/add-on-quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("Authorization: Non-staff role returns 403 Forbidden")
        void nonStaffRole_returns403() throws Exception {
            String requestJson = """
                {
                    "addonType": "LASER_ENGRAVING",
                    "itemDescription": "Laser",
                    "amount": { "amount": "0", "currency": "VND" }
                }
                """;

            mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/add-on-quotes")
                            .header("Authorization", MEMBER_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Authorization: Staff outside location scope returns 403 Forbidden")
        void staffOutsideLocationScope_returns403() throws Exception {
            String requestJson = """
                {
                    "addonType": "LASER_ENGRAVING",
                    "itemDescription": "Laser",
                    "amount": { "amount": "0", "currency": "VND" }
                }
                """;

            mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/add-on-quotes")
                            .header("Authorization", OTHER_STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("LOCATION_SCOPE_DENIED"));
        }

        @Test
        @DisplayName("Path validation: Non-existent booking UUID returns 404 Not Found")
        void nonExistentBooking_returns404() throws Exception {
            String missingUuid = UUID.randomUUID().toString();
            String requestJson = """
                {
                    "addonType": "LASER_ENGRAVING",
                    "itemDescription": "Laser",
                    "amount": { "amount": "0", "currency": "VND" }
                }
                """;

            mvc.perform(post("/api/v1/ota-channel-bookings/" + missingUuid + "/add-on-quotes")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("Endpoint 2: POST /api/v1/ota-addon-quotes/{quoteId}/acceptance")
    class AcceptanceTests {

        private String paidQuoteId;

        @BeforeEach
        void createPaidQuote() {
            OtaAddonBillLink quoteLink = OtaAddonBillLink.builder()
                    .channelBooking(booking)
                    .settlementQuoteReference(UUID.randomUUID().toString())
                    .addonType("PAID_OPTION")
                    .itemCode("OPT-PLATINUM")
                    .itemDescription("Platinum Inlay")
                    .amountMinor(300_000L)
                    .currencyCode("VND")
                    .status("QUOTED")
                    .build();
            paidQuoteId = quoteLink.getSettlementQuoteReference();
            linkStore.put(paidQuoteId, quoteLink);
        }

        @Test
        @DisplayName("Customer acceptance succeeds with 200 OK and status ACCEPTED")
        void acceptQuote_succeeds200() throws Exception {
            String requestJson = """
                {
                    "acceptedAt": "2026-11-01T12:00:00Z",
                    "acceptanceReference": "consent-sign-guest-001"
                }
                """;

            mvc.perform(post("/api/v1/ota-addon-quotes/" + paidQuoteId + "/acceptance")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quoteId").value(paidQuoteId))
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.itemCode").value("OPT-PLATINUM"))
                    .andExpect(jsonPath("$.amount.amount").value("300000"));
        }

        @Test
        @DisplayName("Idempotent replay: Acceptance with same consent reference returns 200 OK")
        void acceptQuote_sameReplay_succeeds200() throws Exception {
            String requestJson = """
                {
                    "acceptedAt": "2026-11-01T12:00:00Z",
                    "acceptanceReference": "consent-sign-guest-001"
                }
                """;

            // First call
            mvc.perform(post("/api/v1/ota-addon-quotes/" + paidQuoteId + "/acceptance")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            // Replay call
            mvc.perform(post("/api/v1/ota-addon-quotes/" + paidQuoteId + "/acceptance")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("Changed replay: Acceptance with different consent reference is rejected with 409 Conflict")
        void acceptQuote_changedReplay_rejected409() throws Exception {
            String requestJson1 = """
                {
                    "acceptedAt": "2026-11-01T12:00:00Z",
                    "acceptanceReference": "consent-sign-guest-001"
                }
                """;
            String requestJson2 = """
                {
                    "acceptedAt": "2026-11-01T12:00:00Z",
                    "acceptanceReference": "consent-different-reference-002"
                }
                """;

            // First call
            mvc.perform(post("/api/v1/ota-addon-quotes/" + paidQuoteId + "/acceptance")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson1))
                    .andExpect(status().isOk());

            // Changed replay call
            mvc.perform(post("/api/v1/ota-addon-quotes/" + paidQuoteId + "/acceptance")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson2))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("different consent reference")));
        }

        @Test
        @DisplayName("Free engraving cannot be accepted: rejected with 409 Conflict")
        void acceptFreeEngraving_rejected409() throws Exception {
            OtaAddonBillLink freeLink = OtaAddonBillLink.builder()
                    .channelBooking(booking)
                    .settlementQuoteReference(UUID.randomUUID().toString())
                    .addonType("LASER_ENGRAVING")
                    .itemCode("FREE-ENGRAVE")
                    .amountMinor(0L)
                    .currencyCode("VND")
                    .status("RECORDED_FREE")
                    .build();
            String freeQuoteId = freeLink.getSettlementQuoteReference();
            linkStore.put(freeQuoteId, freeLink);

            String requestJson = """
                {
                    "acceptedAt": "2026-11-01T12:00:00Z",
                    "acceptanceReference": "consent-free"
                }
                """;

            mvc.perform(post("/api/v1/ota-addon-quotes/" + freeQuoteId + "/acceptance")
                            .header("Authorization", STAFF_AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"));
        }
    }

    @Nested
    @DisplayName("Endpoint 3: POST /api/v1/ota-addon-quotes/{quoteId}/direct-bill")
    class DirectBillTests {

        private String acceptedQuoteId;

        @BeforeEach
        void createAcceptedQuote() {
            OtaAddonBillLink quoteLink = OtaAddonBillLink.builder()
                    .channelBooking(booking)
                    .settlementQuoteReference(UUID.randomUUID().toString())
                    .addonType("PAID_OPTION")
                    .itemCode("OPT-SAPPHIRE")
                    .itemDescription("Sapphire Setting")
                    .amountMinor(450_000L)
                    .currencyCode("VND")
                    .status("ACCEPTED")
                    .customerConsentReference("consent-signed-sapphire-01")
                    .acceptedAt(Instant.now())
                    .build();
            acceptedQuoteId = quoteLink.getSettlementQuoteReference();
            linkStore.put(acceptedQuoteId, quoteLink);
        }

        @Test
        @DisplayName("Valid accepted quote creates direct bill with 201 Created")
        void acceptedQuote_createsDirectBill_succeeds201() throws Exception {
            when(settlementPort.createDirectBill(any())).thenReturn(
                    DirectBillCreationOutcome.success("SETTLE-ADDON-BILL-777", "https://vnpay.mock/pay/777")
            );

            mvc.perform(post("/api/v1/ota-addon-quotes/" + acceptedQuoteId + "/direct-bill")
                            .header("Authorization", STAFF_AUTH)
                            .header("X-Correlation-Id", UUID.randomUUID().toString()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.quoteId").value(acceptedQuoteId))
                    .andExpect(jsonPath("$.settlementBillReference").value("SETTLE-ADDON-BILL-777"))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());

            // Verify status transitioned to BILLED
            OtaAddonBillLink billed = linkStore.get(acceptedQuoteId);
            assertThat(billed.getStatus()).isEqualTo("BILLED");
            assertThat(billed.getSettlementBillReference()).isEqualTo("SETTLE-ADDON-BILL-777");
        }

        @Test
        @DisplayName("Idempotent replay: Direct bill creation on already billed quote returns 201 with existing bill")
        void alreadyBilled_replayReturnsExistingBill() throws Exception {
            when(settlementPort.createDirectBill(any())).thenReturn(
                    DirectBillCreationOutcome.success("SETTLE-ADDON-BILL-777", "https://vnpay.mock/pay/777")
            );

            // First call
            mvc.perform(post("/api/v1/ota-addon-quotes/" + acceptedQuoteId + "/direct-bill")
                            .header("Authorization", STAFF_AUTH))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.settlementBillReference").value("SETTLE-ADDON-BILL-777"));

            // Second call (replay)
            mvc.perform(post("/api/v1/ota-addon-quotes/" + acceptedQuoteId + "/direct-bill")
                            .header("Authorization", STAFF_AUTH))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.settlementBillReference").value("SETTLE-ADDON-BILL-777"));
        }

        @Test
        @DisplayName("Missing consent: Direct bill creation without customer acceptance is rejected with 409 Conflict")
        void unacceptedQuote_directBill_rejected409() throws Exception {
            OtaAddonBillLink unacceptedLink = OtaAddonBillLink.builder()
                    .channelBooking(booking)
                    .settlementQuoteReference(UUID.randomUUID().toString())
                    .addonType("PAID_OPTION")
                    .itemCode("OPT-EMERALD")
                    .amountMinor(500_000L)
                    .currencyCode("VND")
                    .status("QUOTED") // Not accepted!
                    .build();
            String unacceptedQuoteId = unacceptedLink.getSettlementQuoteReference();
            linkStore.put(unacceptedQuoteId, unacceptedLink);

            mvc.perform(post("/api/v1/ota-addon-quotes/" + unacceptedQuoteId + "/direct-bill")
                            .header("Authorization", STAFF_AUTH))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Customer acceptance is required")));
        }

        @Test
        @DisplayName("Free engraving direct bill: Rejected with 422 Unprocessable")
        void freeEngraving_directBill_rejected422() throws Exception {
            OtaAddonBillLink freeLink = OtaAddonBillLink.builder()
                    .channelBooking(booking)
                    .settlementQuoteReference(UUID.randomUUID().toString())
                    .addonType("LASER_ENGRAVING")
                    .itemCode("FREE-ENGRAVE")
                    .amountMinor(0L)
                    .currencyCode("VND")
                    .status("RECORDED_FREE")
                    .build();
            String freeQuoteId = freeLink.getSettlementQuoteReference();
            linkStore.put(freeQuoteId, freeLink);

            mvc.perform(post("/api/v1/ota-addon-quotes/" + freeQuoteId + "/direct-bill")
                            .header("Authorization", STAFF_AUTH))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("UNPROCESSABLE"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Laser engraving is free")));
        }

        @Test
        @DisplayName("Financial Isolation: OTA booking price, accepted amount, commission, and fee remain strictly unchanged")
        void financialIsolation_bookingRemainsUnchanged() throws Exception {
            long initialAccepted = booking.getAcceptedAmountMinor();
            long initialRetail = booking.getRetailAmountMinor();
            long initialCommission = booking.getCommissionAmountMinor();
            long initialFee = booking.getFeeAmountMinor();

            when(settlementPort.createDirectBill(any())).thenReturn(
                    DirectBillCreationOutcome.success("SETTLE-ADDON-BILL-888", "https://vnpay.mock/pay/888")
            );

            mvc.perform(post("/api/v1/ota-addon-quotes/" + acceptedQuoteId + "/direct-bill")
                            .header("Authorization", STAFF_AUTH))
                    .andExpect(status().isCreated());

            assertThat(booking.getAcceptedAmountMinor()).isEqualTo(initialAccepted);
            assertThat(booking.getRetailAmountMinor()).isEqualTo(initialRetail);
            assertThat(booking.getCommissionAmountMinor()).isEqualTo(initialCommission);
            assertThat(booking.getFeeAmountMinor()).isEqualTo(initialFee);
        }
    }
}
