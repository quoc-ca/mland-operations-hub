package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaFinancialEvidenceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaPayoutReconciliationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.*;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("003.4 T044: OTA Finance Reconciliation REST Controller Integration Tests")
class OtaFinancialEvidenceControllerIT {

        @Mock
        private AccountFacade accountFacade;
        @Mock
        private OtaChannelBookingRepository bookingRepository;
        @Mock
        private OtaCustomerPaymentEvidenceRepository customerPaymentRepository;
        @Mock
        private OtaPayoutPositionRepository positionRepository;
        @Mock
        private OtaPayoutLedgerEntryRepository ledgerRepository;
        @Mock
        private OtaReconciliationWorkItemRepository workItemRepository;

        private MockMvc mvc;
        private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        private static final String MANAGER_AUTH = "Bearer test-uid:manager-001";
        private static final String STAFF_AUTH = "Bearer test-uid:staff-101";
        private static final String MEMBER_AUTH = "Bearer test-uid:member-202";

        private final String bookingUuid = UUID.randomUUID().toString();
        private OtaChannelBooking booking;
        private OtaPayoutPosition position;

        private final List<OtaCustomerPaymentEvidence> payments = new ArrayList<>();
        private final List<OtaPayoutPosition> positions = new ArrayList<>();
        private final List<OtaPayoutLedgerEntry> ledgerEntries = new ArrayList<>();
        private final List<OtaReconciliationWorkItem> workItems = new ArrayList<>();
        private long nextLedgerId = 100L;
        private long nextWorkItemId = 200L;

        @BeforeEach
        void setUp() {
                payments.clear();
                positions.clear();
                ledgerEntries.clear();
                workItems.clear();
                nextLedgerId = 100L;
                nextWorkItemId = 200L;

                // Accounts setup
                when(accountFacade.resolveAccount("manager-001")).thenReturn(Optional.of(
                                new AccountSummary(1L, ActorRole.MANAGER, ActorStatus.ACTIVE, "mgr@mland.test", null, null)));
                when(accountFacade.resolveAccount("staff-101")).thenReturn(Optional.of(
                                new AccountSummary(2L, ActorRole.STAFF, ActorStatus.ACTIVE, "staff@mland.test", null, null)));
                when(accountFacade.resolveAccount("member-202")).thenReturn(Optional.of(
                                new AccountSummary(3L, ActorRole.MEMBER, ActorStatus.ACTIVE, "member@mland.test", null, null)));

                // Fixtures
                OtaChannelConnection connection = OtaChannelConnection.builder()
                                .id(1L)
                                .providerCode(OtaProvider.KLOOK)
                                .environment(OtaEnvironment.TEST)
                                .status("ACTIVE")
                                .build();
                OtaChannelMapping mapping = OtaChannelMapping.builder()
                                .id(1L)
                                .publicReference(UUID.randomUUID().toString())
                                .connection(connection)
                                .locationReference("1001")
                                .externalProductId("prod-1")
                                .externalOptionId("opt-1")
                                .offerReference("offer-1")
                                .sessionReference(UUID.randomUUID().toString())
                                .status(OtaMappingStatus.BOOKING_ENABLED)
                                .version(1L)
                                .build();
                booking = OtaChannelBooking.capture(
                                mapping, "klook-bk-001", "CONFIRMED", 2, "mland-bk-001",
                                "KLOOK", "POLICY-REF-1", "V1",
                                Instant.parse("2026-10-31T10:00:00Z"), "Asia/Ho_Chi_Minh",
                                100_000L, 80_000L, 20_000L, 0L, 100_000L, "VND");
                ReflectionTestUtils.setField(booking, "id", 10L);
                ReflectionTestUtils.setField(booking, "publicReference", bookingUuid);

                position = OtaPayoutPosition.create(10L, 1L, 80_000L, "VND", OtaPayoutStatus.NOT_ELIGIBLE);
                ReflectionTestUtils.setField(position, "id", 20L);
                positions.add(position);

                OtaCustomerPaymentEvidence paymentEvidence = OtaCustomerPaymentEvidence.record(
                                10L, "ext-pay-ref-001", "PAYMENT", OtaCustomerPaymentState.PAID_ON_CHANNEL,
                                100_000L, "VND", Instant.parse("2026-10-31T10:00:00Z"), "trace-001");
                ReflectionTestUtils.setField(paymentEvidence, "id", 30L);
                payments.add(paymentEvidence);

                // Repositories setup
                when(bookingRepository.findByPublicReference(bookingUuid)).thenReturn(Optional.of(booking));
                when(bookingRepository.findByPublicReferenceForUpdate(bookingUuid)).thenReturn(Optional.of(booking));

                when(customerPaymentRepository.findAllByChannelBookingIdOrderByRecordedAtAscIdAsc(10L))
                                .thenAnswer(inv -> List.copyOf(payments));

                when(positionRepository.findAllByChannelBookingIdOrderByCreatedAtAscIdAsc(10L))
                                .thenAnswer(inv -> List.copyOf(positions));
                when(positionRepository.findByChannelBookingIdForUpdate(10L))
                                .thenAnswer(inv -> List.copyOf(positions));

                when(ledgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(20L))
                                .thenAnswer(inv -> List.copyOf(ledgerEntries));

                when(ledgerRepository.findByPublicReference(any())).thenAnswer(inv -> {
                        String ref = inv.getArgument(0);
                        return ledgerEntries.stream().filter(e -> ref.equals(e.getPublicReference())).findFirst();
                });

                when(ledgerRepository.findByAllocationReference(any())).thenAnswer(inv -> {
                        String ref = inv.getArgument(0);
                        return ledgerEntries.stream().filter(e -> ref.equals(e.getAllocationReference())).findFirst();
                });

                when(ledgerRepository.findByPayoutPositionIdAndEntryKindAndExternalEvidenceReference(any(), any(),
                                any()))
                                .thenAnswer(inv -> {
                                        Long posId = inv.getArgument(0);
                                        String kind = inv.getArgument(1);
                                        String ref = inv.getArgument(2);
                                        return ledgerEntries.stream()
                                                        .filter(e -> posId.equals(e.getPayoutPositionId()) &&
                                                                        kind.equals(e.getEntryKind()) &&
                                                                        ref.equals(e.getExternalEvidenceReference()))
                                                        .findFirst();
                                });

                when(ledgerRepository.save(any(OtaPayoutLedgerEntry.class))).thenAnswer(inv -> {
                        OtaPayoutLedgerEntry entry = inv.getArgument(0);
                        if (entry.getId() == null) {
                                ReflectionTestUtils.setField(entry, "id", nextLedgerId++);
                        }
                        ledgerEntries.add(entry);
                        return entry;
                });

                when(workItemRepository.save(any(OtaReconciliationWorkItem.class))).thenAnswer(inv -> {
                        OtaReconciliationWorkItem item = inv.getArgument(0);
                        if (item.getId() == null) {
                                ReflectionTestUtils.setField(item, "id", nextWorkItemId++);
                        }
                        workItems.add(item);
                        return item;
                });

                // Controller & MockMvc setup
                OtaAuthorizationService authorizationService = new OtaAuthorizationService(accountFacade);
                ReflectionTestUtils.setField(authorizationService, "allowTestTokens", true);

                OtaPayoutReconciliationService payoutService = new OtaPayoutReconciliationService(
                                bookingRepository, positionRepository, ledgerRepository, workItemRepository);
                OtaFinancialEvidenceService financialService = new OtaFinancialEvidenceService(
                                bookingRepository, customerPaymentRepository, positionRepository, ledgerRepository,
                                payoutService);

                OtaFinancialEvidenceController controller = new OtaFinancialEvidenceController(authorizationService,
                                financialService);

                mvc = MockMvcBuilders.standaloneSetup(controller)
                                .setControllerAdvice(new OtaExceptionHandler())
                                .addFilters(new CorrelationIdFilter())
                                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                                .build();
        }

        // =========================================================================
        // GET /api/v1/ota-channel-bookings/{channelBookingId}/financial-evidence
        // =========================================================================

        @Nested
        @DisplayName("GET /financial-evidence (Read financial separation)")
        class ReadFinancialEvidenceTests {

                @Test
                @DisplayName("Manager can read separated financial evidence with redacted sensitive data")
                void managerCanReadFinancialEvidence_withRedactedSensitiveData() throws Exception {
                        mvc.perform(get("/api/v1/ota-channel-bookings/" + bookingUuid + "/financial-evidence")
                                        .header("Authorization", MANAGER_AUTH)
                                        .accept(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.channelBookingId").value(bookingUuid))
                                        .andExpect(jsonPath("$.customerPaymentState").value("PAID_ON_CHANNEL"))
                                        .andExpect(jsonPath("$.payoutState").value("NOT_ELIGIBLE"))
                                        .andExpect(jsonPath("$.entries").isArray())
                                        .andExpect(jsonPath("$.entries[0].kind").value("CHANNEL_PAYMENT"))
                                        .andExpect(jsonPath("$.entries[0].evidenceType").value("PAYMENT"))
                                        .andExpect(jsonPath("$.entries[0].amount.amount").value("100000"))
                                        .andExpect(jsonPath("$.entries[0].amount.currency").value("VND"))
                                        .andExpect(jsonPath("$.entries[0].reference").value("ext-pay-ref-001"))
                                        // Verify sensitive fields (credit card, customer phone/email, credentials) are
                                        // not present
                                        .andExpect(jsonPath("$.entries[0].creditCard").doesNotExist())
                                        .andExpect(jsonPath("$.entries[0].customerEmail").doesNotExist())
                                        .andExpect(jsonPath("$.entries[0].customerPhone").doesNotExist())
                                        .andExpect(jsonPath("$.entries[0].rawPayload").doesNotExist())
                                        .andExpect(jsonPath("$.entries[0].secret").doesNotExist());
                }

                @Test
                @DisplayName("Read without authorization header returns 401 Unauthorized")
                void read_unauthorizedWithoutToken() throws Exception {
                        mvc.perform(get("/api/v1/ota-channel-bookings/" + bookingUuid + "/financial-evidence")
                                        .accept(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isUnauthorized())
                                        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
                }

                @Test
                @DisplayName("Staff is forbidden from reading financial evidence (requires Manager)")
                void read_forbiddenForStaff() throws Exception {
                        mvc.perform(get("/api/v1/ota-channel-bookings/" + bookingUuid + "/financial-evidence")
                                        .header("Authorization", STAFF_AUTH)
                                        .accept(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
                }

                @Test
                @DisplayName("Member is forbidden from reading financial evidence")
                void read_forbiddenForMember() throws Exception {
                        mvc.perform(get("/api/v1/ota-channel-bookings/" + bookingUuid + "/financial-evidence")
                                        .header("Authorization", MEMBER_AUTH)
                                        .accept(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
                }

                @Test
                @DisplayName("Unknown booking UUID returns 404 Not Found")
                void read_notFoundForMissingBooking() throws Exception {
                        String missingUuid = UUID.randomUUID().toString();
                        mvc.perform(get("/api/v1/ota-channel-bookings/" + missingUuid + "/financial-evidence")
                                        .header("Authorization", MANAGER_AUTH)
                                        .accept(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
                }

                @Test
                @DisplayName("Malformed booking UUID returns 400 Bad Request")
                void read_badRequestForInvalidUuid() throws Exception {
                        mvc.perform(get("/api/v1/ota-channel-bookings/not-a-valid-uuid/financial-evidence")
                                        .header("Authorization", MANAGER_AUTH)
                                        .accept(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
                }
        }

        // =========================================================================
        // POST /api/v1/ota-channel-bookings/{channelBookingId}/payout-reconciliation
        // =========================================================================

        @Nested
        @DisplayName("POST /payout-reconciliation (Payout reconciliation lifecycle)")
        class PayoutReconciliationTests {

                @Test
                @DisplayName("Full payout lifecycle: ELIGIBILITY -> INVOICE -> REMITTANCE -> BANK_RECEIPT -> PAID_OUT")
                void fullPayoutLifecycle_advancesToPaidOut() throws Exception {
                        // 1. ELIGIBILITY stage (NOT_ELIGIBLE -> ACCRUED)
                        String eligibilityPayload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "ELIGIBILITY",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "elig-ref-001",
                                            "reason": "Customer completed workshop session",
                                            "occurredAt": "2026-10-31T14:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(eligibilityPayload))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.payoutState").value("ACCRUED"))
                                        .andExpect(jsonPath("$.entries.length()").value(2));

                        // 2. INVOICE stage (ACCRUED -> INVOICED)
                        String invoicePayload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "INVOICE",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "inv-ref-001",
                                            "reason": "Monthly supplier invoice issued",
                                            "occurredAt": "2026-10-31T15:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(invoicePayload))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.payoutState").value("INVOICED"))
                                        .andExpect(jsonPath("$.entries.length()").value(3));

                        // 3. REMITTANCE stage (INVOICED -> PAYOUT_PENDING)
                        String remittancePayload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "REMITTANCE",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "remit-ref-001",
                                            "reason": "OTA remittance advice received",
                                            "payoutReference": "PO-KL-202610-001",
                                            "occurredAt": "2026-10-31T16:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(remittancePayload))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.payoutState").value("PAYOUT_PENDING"))
                                        .andExpect(jsonPath("$.entries.length()").value(4));

                        // 4. BANK_RECEIPT stage (PAYOUT_PENDING -> PAID_OUT)
                        String bankReceiptPayload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "BANK_RECEIPT",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "bank-ref-001",
                                            "reason": "Bank statement confirmed deposit",
                                            "payoutReference": "PO-KL-202610-001",
                                            "allocationReference": "ALLOC-BK-001",
                                            "occurredAt": "2026-10-31T17:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(bankReceiptPayload))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.payoutState").value("PAID_OUT"))
                                        .andExpect(jsonPath("$.entries.length()").value(5));

                        assertThat(position.getStatus()).isEqualTo(OtaPayoutStatus.PAID_OUT);
                        assertThat(workItems).isEmpty();
                }

                @Test
                @DisplayName("Bank receipt with mismatched amount opens dispute and stays in PAYOUT_PENDING")
                void bankReceipt_mismatchedAmount_opensDispute() throws Exception {
                        advanceToPayoutPending("PO-KL-DISPUTE-01");

                        // Bank receipt with only 60,000 VND instead of expected 80,000 VND
                        String bankReceiptPayload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "BANK_RECEIPT",
                                            "amount": { "amount": "60000", "currency": "VND" },
                                            "reference": "bank-ref-short-001",
                                            "reason": "Partial bank receipt received",
                                            "payoutReference": "PO-KL-DISPUTE-01",
                                            "allocationReference": "ALLOC-SHORT-001",
                                            "occurredAt": "2026-10-31T17:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(bankReceiptPayload))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.payoutState").value("PAYOUT_PENDING"));

                        assertThat(position.getStatus()).isEqualTo(OtaPayoutStatus.PAYOUT_PENDING);
                        assertThat(workItems).hasSize(1);
                        assertThat(workItems.getFirst().getCategory()).isEqualTo("PAYOUT_DISPUTE");
                        assertThat(workItems.getFirst().getStatus()).isEqualTo("OPEN");
                }

                @Test
                @DisplayName("Bank receipt with mismatched payoutReference opens dispute and stays in PAYOUT_PENDING")
                void bankReceipt_mismatchedPayoutReference_opensDispute() throws Exception {
                        advanceToPayoutPending("PO-KL-MATCH-01");

                        // Bank receipt with different payout reference
                        String bankReceiptPayload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "BANK_RECEIPT",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "bank-ref-diff-po",
                                            "reason": "Bank deposit with unlinked PO reference",
                                            "payoutReference": "PO-UNKNOWN-999",
                                            "allocationReference": "ALLOC-DIFF-001",
                                            "occurredAt": "2026-10-31T17:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(bankReceiptPayload))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.payoutState").value("PAYOUT_PENDING"));

                        assertThat(position.getStatus()).isEqualTo(OtaPayoutStatus.PAYOUT_PENDING);
                        assertThat(workItems).hasSize(1);
                        assertThat(workItems.getFirst().getCategory()).isEqualTo("PAYOUT_DISPUTE");
                }

                @Test
                @DisplayName("Invoice with mismatched amount opens dispute and stays in ACCRUED")
                void invoice_mismatchedAmount_opensDispute() throws Exception {
                        // First transition to ACCRUED
                        advanceToAccrued();

                        // Mismatched invoice amount (70,000 instead of 80,000)
                        String invoicePayload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "INVOICE",
                                            "amount": { "amount": "70000", "currency": "VND" },
                                            "reference": "inv-ref-mismatch-001",
                                            "reason": "Provider invoice disputed rate",
                                            "occurredAt": "2026-10-31T15:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(invoicePayload))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.payoutState").value("ACCRUED"));

                        assertThat(position.getStatus()).isEqualTo(OtaPayoutStatus.ACCRUED);
                        assertThat(workItems).hasSize(1);
                        assertThat(workItems.getFirst().getCategory()).isEqualTo("PAYOUT_DISPUTE");
                }

                @Test
                @DisplayName("Idempotent replay returns existing entry without creating duplicate")
                void idempotentReplay_returnsExistingEntry() throws Exception {
                        String eligibilityPayload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "ELIGIBILITY",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "elig-ref-replay-001",
                                            "reason": "Customer completed workshop session",
                                            "occurredAt": "2026-10-31T14:00:00Z"
                                        }
                                        """;
                        // First call
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(eligibilityPayload))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.payoutState").value("ACCRUED"));

                        assertThat(ledgerEntries).hasSize(1);

                        // Replay identical call
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(eligibilityPayload))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.payoutState").value("ACCRUED"));

                        assertThat(ledgerEntries).hasSize(1); // No duplicate added
                }

                @Test
                @DisplayName("Conflicting replay with same reference but different reason returns 409 Conflict")
                void conflictingReplay_returns409Conflict() throws Exception {
                        String eligibilityPayload1 = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "ELIGIBILITY",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "elig-ref-conflict-001",
                                            "reason": "Original reason",
                                            "occurredAt": "2026-10-31T14:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(eligibilityPayload1))
                                        .andExpect(status().isCreated());

                        String eligibilityPayload2 = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "ELIGIBILITY",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "elig-ref-conflict-001",
                                            "reason": "Conflicting altered reason",
                                            "occurredAt": "2026-10-31T14:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(eligibilityPayload2))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value("CONFLICT"));
                }

                @Test
                @DisplayName("Out-of-order stage transition returns 409 Conflict")
                void outOfLifecycleOrder_returns409Conflict() throws Exception {
                        // Attempting BANK_RECEIPT while position is still NOT_ELIGIBLE
                        String bankReceiptPayload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "BANK_RECEIPT",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "bank-ref-out-of-order",
                                            "reason": "Premature bank receipt",
                                            "payoutReference": "PO-001",
                                            "allocationReference": "ALLOC-001",
                                            "occurredAt": "2026-10-31T17:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(bankReceiptPayload))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value("CONFLICT"));
                }

                @Test
                @DisplayName("Bank receipt missing allocationReference returns 400 Bad Request")
                void bankReceipt_missingAllocationReference_returns400() throws Exception {
                        advanceToPayoutPending("PO-KL-ALLOC-TEST");

                        String payload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "BANK_RECEIPT",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "bank-ref-no-alloc",
                                            "reason": "Missing allocation reference",
                                            "payoutReference": "PO-KL-ALLOC-TEST",
                                            "occurredAt": "2026-10-31T17:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
                }

                @Test
                @DisplayName("Bank receipt missing payoutReference returns 400 Bad Request")
                void bankReceipt_missingPayoutReference_returns400() throws Exception {
                        advanceToPayoutPending("PO-KL-POREF-TEST");

                        String payload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "BANK_RECEIPT",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "bank-ref-no-po",
                                            "reason": "Missing payout reference",
                                            "allocationReference": "ALLOC-001",
                                            "occurredAt": "2026-10-31T17:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
                }

                @Test
                @DisplayName("Mismatched kind and evidenceType returns 400 Bad Request")
                void mismatchedKindAndEvidenceType_returns400() throws Exception {
                        String payload = """
                                        {
                                            "kind": "ADJUSTMENT",
                                            "evidenceType": "ELIGIBILITY",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "elig-ref-mismatch-kind",
                                            "reason": "Mismatched kind and evidenceType",
                                            "occurredAt": "2026-10-31T14:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
                }

                @Test
                @DisplayName("Unknown JSON property is rejected with 400 Bad Request (OtaStrictRequest)")
                void unknownJsonProperty_returns400() throws Exception {
                        String payload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "ELIGIBILITY",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "elig-ref-extra-prop",
                                            "reason": "Legitimate reason",
                                            "occurredAt": "2026-10-31T14:00:00Z",
                                            "unauthorizedSecretField": "should-fail-strict-request"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
                }

                @Test
                @DisplayName("Post reconciliation without authorization returns 401 Unauthorized")
                void post_unauthorizedWithoutToken() throws Exception {
                        String payload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "ELIGIBILITY",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "elig-ref-unauth",
                                            "reason": "Session completed",
                                            "occurredAt": "2026-10-31T14:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                        .andExpect(status().isUnauthorized())
                                        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
                }

                @Test
                @DisplayName("Staff is forbidden from posting payout reconciliation (requires Manager)")
                void post_forbiddenForStaff() throws Exception {
                        String payload = """
                                        {
                                            "kind": "PAYOUT",
                                            "evidenceType": "ELIGIBILITY",
                                            "amount": { "amount": "80000", "currency": "VND" },
                                            "reference": "elig-ref-staff",
                                            "reason": "Staff attempt",
                                            "occurredAt": "2026-10-31T14:00:00Z"
                                        }
                                        """;
                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", STAFF_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
                }

                @Test
                @DisplayName("Adjustment and Reversal: linked correction and negation")
                void adjustmentAndReversal_linkedCorrectionAndNegation() throws Exception {
                        advanceToAccrued();
                        String eligibilityId = ledgerEntries.getFirst().getPublicReference();

                        // 1. Valid ADJUSTMENT linked to ELIGIBILITY entry
                        String adjustmentPayload = String.format("""
                                        {
                                            "kind": "ADJUSTMENT",
                                            "evidenceType": "ADJUSTMENT",
                                            "amount": { "amount": "-10000", "currency": "VND" },
                                            "reference": "adj-ref-001",
                                            "reason": "Partial refund deduction",
                                            "relatedEntryId": "%s",
                                            "occurredAt": "2026-10-31T14:30:00Z"
                                        }
                                        """, eligibilityId);

                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(adjustmentPayload))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.payoutState").value("ACCRUED"))
                                        .andExpect(jsonPath("$.entries.length()").value(3));

                        String adjustmentId = ledgerEntries.getLast().getPublicReference();

                        // 2. Valid REVERSAL negating the ADJUSTMENT (+10,000)
                        String reversalPayload = String.format("""
                                        {
                                            "kind": "REVERSAL",
                                            "evidenceType": "REVERSAL",
                                            "amount": { "amount": "10000", "currency": "VND" },
                                            "reference": "rev-ref-001",
                                            "reason": "Reversing incorrect deduction",
                                            "relatedEntryId": "%s",
                                            "occurredAt": "2026-10-31T14:45:00Z"
                                        }
                                        """, adjustmentId);

                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(reversalPayload))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.payoutState").value("ACCRUED"))
                                        .andExpect(jsonPath("$.entries.length()").value(4));

                        // 3. Duplicate REVERSAL of the same adjustment returns 409 Conflict
                        String duplicateReversalPayload = String.format("""
                                        {
                                            "kind": "REVERSAL",
                                            "evidenceType": "REVERSAL",
                                            "amount": { "amount": "10000", "currency": "VND" },
                                            "reference": "rev-ref-002",
                                            "reason": "Duplicate reversal attempt",
                                            "relatedEntryId": "%s",
                                            "occurredAt": "2026-10-31T14:50:00Z"
                                        }
                                        """, adjustmentId);

                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(duplicateReversalPayload))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value("CONFLICT"));
                }

                @Test
                @DisplayName("Adjustment missing relatedEntryId returns 400 Bad Request")
                void adjustment_missingRelatedEntryId_returns400() throws Exception {
                        advanceToAccrued();

                        String adjustmentPayload = """
                                        {
                                            "kind": "ADJUSTMENT",
                                            "evidenceType": "ADJUSTMENT",
                                            "amount": { "amount": "-10000", "currency": "VND" },
                                            "reference": "adj-ref-no-related",
                                            "reason": "Missing relatedEntryId",
                                            "occurredAt": "2026-10-31T14:30:00Z"
                                        }
                                        """;

                        mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                        .header("Authorization", MANAGER_AUTH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(adjustmentPayload))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
                }
        }

        // =========================================================================
        // Helper Methods
        // =========================================================================

        private void advanceToAccrued() throws Exception {
                String eligibilityPayload = """
                                {
                                    "kind": "PAYOUT",
                                    "evidenceType": "ELIGIBILITY",
                                    "amount": { "amount": "80000", "currency": "VND" },
                                    "reference": "elig-step-ref",
                                    "reason": "Session completed",
                                    "occurredAt": "2026-10-31T14:00:00Z"
                                }
                                """;
                mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                .header("Authorization", MANAGER_AUTH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(eligibilityPayload))
                                .andExpect(status().isCreated());
        }

        private void advanceToPayoutPending(String payoutReference) throws Exception {
                advanceToAccrued();

                String invoicePayload = """
                                {
                                    "kind": "PAYOUT",
                                    "evidenceType": "INVOICE",
                                    "amount": { "amount": "80000", "currency": "VND" },
                                    "reference": "inv-step-ref",
                                    "reason": "Invoice issued",
                                    "occurredAt": "2026-10-31T15:00:00Z"
                                }
                                """;
                mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                .header("Authorization", MANAGER_AUTH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invoicePayload))
                                .andExpect(status().isCreated());

                String remittancePayload = String.format("""
                                {
                                    "kind": "PAYOUT",
                                    "evidenceType": "REMITTANCE",
                                    "amount": { "amount": "80000", "currency": "VND" },
                                    "reference": "remit-step-ref",
                                    "reason": "Remittance received",
                                    "payoutReference": "%s",
                                    "occurredAt": "2026-10-31T16:00:00Z"
                                }
                                """, payoutReference);
                mvc.perform(post("/api/v1/ota-channel-bookings/" + bookingUuid + "/payout-reconciliation")
                                .header("Authorization", MANAGER_AUTH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(remittancePayload))
                                .andExpect(status().isCreated());
        }
}
