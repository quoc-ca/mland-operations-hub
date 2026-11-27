package edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.DesignRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderPaymentContextRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderPaymentContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service.CustomOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CustomOrderIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private CustomOrderRepository customOrderRepository;

        @Autowired
        private CustomOrderQuoteRepository customOrderQuoteRepository;

        @Autowired
        private DesignRequestRepository designRequestRepository;

        @Autowired
        private CustomOrderService customOrderService;

        @Autowired
        private CustomOrderPaymentContextRepository paymentContextRepository;

        @Autowired
        private edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderShipmentRepository customOrderShipmentRepository;

        @MockitoBean
        private TokenVerificationService tokenVerificationService;

        @MockitoBean
        private AccountFacade accountFacade;

        private final String memberToken = "token-member-co-it";
        private final String memberUid = "uid-member-co-it";
        private final Long memberAccountId = 5001L;

        private final String staffToken = "token-staff-co-it";
        private final String staffUid = "uid-staff-co-it";
        private final Long staffAccountId = 5002L;

        private final String managerToken = "token-manager-co-it";
        private final String managerUid = "uid-manager-co-it";
        private final Long managerAccountId = 5003L;

        private final String carrierToken = "token-carrier-co-it";
        private final String carrierUid = "uid-carrier-co-it";
        private final Long carrierAccountId = 5004L;

        private CustomOrder testOrder;

        @BeforeEach
        void setUp() {
                // Member
                when(tokenVerificationService.verify(memberToken)).thenReturn(
                                new VerifiedIdentity(memberUid, "member-co@mland.vn", true, Instant.now()));
                when(accountFacade.resolveActor(memberUid, true)).thenReturn(Optional.of(
                                new CurrentAccountActor(memberAccountId, ActorRole.MEMBER, ActorStatus.ACTIVE, true,
                                                List.of())));
                when(accountFacade.resolveAccount(memberUid)).thenReturn(Optional.of(
                                new AccountSummary(memberAccountId, ActorRole.MEMBER, ActorStatus.ACTIVE,
                                                "member-co@mland.vn", Instant.now(), Instant.now())));

                // Staff
                when(tokenVerificationService.verify(staffToken)).thenReturn(
                                new VerifiedIdentity(staffUid, "staff-co@mland.vn", true, Instant.now()));
                when(accountFacade.resolveActor(staffUid, true)).thenReturn(Optional.of(
                                new CurrentAccountActor(staffAccountId, ActorRole.STAFF, ActorStatus.ACTIVE, true,
                                                List.of())));
                when(accountFacade.resolveAccount(staffUid)).thenReturn(Optional.of(
                                new AccountSummary(staffAccountId, ActorRole.STAFF, ActorStatus.ACTIVE,
                                                "staff-co@mland.vn", Instant.now(), Instant.now())));

                // Manager
                when(tokenVerificationService.verify(managerToken)).thenReturn(
                                new VerifiedIdentity(managerUid, "manager-co@mland.vn", true, Instant.now()));
                when(accountFacade.resolveActor(managerUid, true)).thenReturn(Optional.of(
                                new CurrentAccountActor(managerAccountId, ActorRole.MANAGER, ActorStatus.ACTIVE, true,
                                                List.of())));
                when(accountFacade.resolveAccount(managerUid)).thenReturn(Optional.of(
                                new AccountSummary(managerAccountId, ActorRole.MANAGER, ActorStatus.ACTIVE,
                                                "manager-co@mland.vn", Instant.now(), Instant.now())));

                // Carrier
                when(tokenVerificationService.verify(carrierToken)).thenReturn(
                                new VerifiedIdentity(carrierUid, "carrier-co@mland.vn", true, Instant.now()));
                when(accountFacade.resolveActor(carrierUid, true)).thenReturn(Optional.of(
                                new CurrentAccountActor(carrierAccountId, ActorRole.CARRIER, ActorStatus.ACTIVE, true,
                                                List.of())));
                when(accountFacade.resolveAccount(carrierUid)).thenReturn(Optional.of(
                                new AccountSummary(carrierAccountId, ActorRole.CARRIER, ActorStatus.ACTIVE,
                                                "carrier-co@mland.vn", Instant.now(), Instant.now())));

                // Create design request fixture
                String designRequestId = "DR-" + UUID.randomUUID().toString().substring(0, 8);
                DesignRequest dr = DesignRequest.builder()
                                .id(designRequestId)
                                .requestReference("DR-REF-" + UUID.randomUUID().toString().substring(0, 8))
                                .ownerAccountId(memberAccountId)
                                .ownershipType("MEMBER")
                                .description("Nhẫn gia công tùy chỉnh cho Integration Test")
                                .requestVersion(1)
                                .idempotencyKey("idemp-dr-" + UUID.randomUUID())
                                .requestStatus("ACTIVE")
                                .reviewStatus(edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus.NEED_REVIEW)
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                designRequestRepository.save(dr);

                // Create CustomOrder fixture
                String orderId = "CO-" + UUID.randomUUID().toString().substring(0, 8);
                testOrder = CustomOrder.builder()
                                .id(orderId)
                                .customOrderReference("REF-" + orderId)
                                .memberAccountId(memberAccountId)
                                .designRequestId(designRequestId)
                                .status("PAID")
                                .productionStatus("QUEUED_FOR_PRODUCTION")
                                .fulfilmentStatus("PREPARING")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                customOrderRepository.save(testOrder);

                // Create Quote fixture
                CustomOrderQuote quote = CustomOrderQuote.builder()
                                .id("QT-" + UUID.randomUUID().toString().substring(0, 8))
                                .customOrderId(orderId)
                                .quoteReference("QREF-" + orderId)
                                .quoteGeneration(1)
                                .totalAmount(5_000_000L)
                                .currency("VND")
                                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                                .status("APPROVED")
                                .approvedByManager(true)
                                .paymentStatus("PAID")
                                .build();
                customOrderQuoteRepository.save(quote);
        }

        @Test
        void whenMemberListsOrders_thenReturns200WithOrderList() throws Exception {
                mockMvc.perform(get("/api/v1/members/me/custom-orders")
                                .header("Authorization", "Bearer " + memberToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items").isArray())
                                .andExpect(jsonPath("$.items[0].customOrderReference")
                                                .value(testOrder.getCustomOrderReference()))
                                .andExpect(jsonPath("$.items[0].status").value("PAID"));
        }

        @Test
        void whenOperationsViewsOrder_thenStaffAccessSucceedsAndMemberForbidden() throws Exception {
                // Staff accessing operations endpoint -> 200 OK
                mockMvc.perform(get("/api/v1/custom-orders/" + testOrder.getId())
                                .header("Authorization", "Bearer " + staffToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.customOrderReference")
                                                .value(testOrder.getCustomOrderReference()));

                // Member accessing operations endpoint -> 403 Forbidden
                mockMvc.perform(get("/api/v1/custom-orders/" + testOrder.getId())
                                .header("Authorization", "Bearer " + memberToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        void whenStaffUpdatesStatus_thenStatusTransitionsCorrectly() throws Exception {
                Map<String, String> body = Map.of(
                                "status", "IN_PRODUCTION",
                                "productionStatus", "IN_PRODUCTION");

                mockMvc.perform(post("/api/v1/custom-orders/" + testOrder.getId() + "/status")
                                .header("Authorization", "Bearer " + staffToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("IN_PRODUCTION"))
                                .andExpect(jsonPath("$.productionStatus").value("IN_PRODUCTION"));
        }

        @Test
        void shouldExecuteFullQuoteLifecycle() throws Exception {
                // Create fresh design request and custom order for two-step quote
                String drId = "DR-QUOTE-" + UUID.randomUUID().toString().substring(0, 8);
                DesignRequest dr = DesignRequest.builder()
                                .id(drId)
                                .requestReference("DR-REF-" + drId)
                                .ownerAccountId(memberAccountId)
                                .ownershipType("MEMBER")
                                .description("Yêu cầu thiết kế nhẫn vàng đính đá")
                                .requestVersion(1)
                                .idempotencyKey("idemp-dr-" + UUID.randomUUID())
                                .requestStatus("ACCEPTED")
                                .reviewStatus(edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus.ACCEPTED)
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                designRequestRepository.save(dr);

                String coId = "CO-QUOTE-" + UUID.randomUUID().toString().substring(0, 8);
                CustomOrder co = CustomOrder.builder()
                                .id(coId)
                                .customOrderReference("REF-" + coId)
                                .memberAccountId(memberAccountId)
                                .designRequestId(drId)
                                .status("QUOTE_PENDING")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                customOrderRepository.save(co);

                // Step 1: Staff prepares quote -> quote status QUOTE_PENDING, approvedByManager
                // = false
                Map<String, Object> quoteBody = Map.of(
                                "totalAmount", "8500000",
                                "currency", "VND",
                                "quoteExpiresAt", Instant.now().plus(7, ChronoUnit.DAYS).toString(),
                                "managerReason", "Báo giá gia công nhẫn đá quý");

                mockMvc.perform(post("/api/v1/design-requests/" + drId + "/quote")
                                .header("Authorization", "Bearer " + staffToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(quoteBody)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("QUOTE_PENDING"))
                                .andExpect(jsonPath("$.approvedByManager").value(false))
                                .andExpect(jsonPath("$.totalAmount").value(8500000));

                // Verify order is still in QUOTE_PENDING
                CustomOrder updatedCo = customOrderRepository.findById(coId).orElseThrow();
                org.junit.jupiter.api.Assertions.assertEquals("QUOTE_PENDING", updatedCo.getStatus());

                // Step 2: Staff attempts approval -> forbidden 403 (Staff cannot self-approve)
                mockMvc.perform(post("/api/v1/design-requests/" + drId + "/quote/approve")
                                .header("Authorization", "Bearer " + staffToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").value("REVIEW_NOT_AUTHORIZED"));

                // Step 3: Member cannot modify quote or approve quote -> forbidden 403
                mockMvc.perform(post("/api/v1/design-requests/" + drId + "/quote")
                                .header("Authorization", "Bearer " + memberToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(quoteBody)))
                                .andExpect(status().isForbidden());

                mockMvc.perform(post("/api/v1/design-requests/" + drId + "/quote/approve")
                                .header("Authorization", "Bearer " + memberToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isForbidden());

                // Step 4: Manager approves quote -> quote status QUOTE_READY, approvedByManager
                // = true, order FULL_PAYMENT_PENDING
                Map<String, String> approveBody = Map.of(
                                "managerReason", "Phê duyệt mức giá gia công 8.500.000 VND");

                mockMvc.perform(post("/api/v1/design-requests/" + drId + "/quote/approve")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(approveBody)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("QUOTE_READY"))
                                .andExpect(jsonPath("$.approvedByManager").value(true));

                CustomOrder finalizedCo = customOrderRepository.findById(coId).orElseThrow();
                org.junit.jupiter.api.Assertions.assertEquals("FULL_PAYMENT_PENDING", finalizedCo.getStatus());
        }

        @Test
        void shouldEnforceFullPaymentAndPreventSplitOrCart() throws Exception {
                // Setup order in FULL_PAYMENT_PENDING with quote QUOTE_READY
                String orderId = "CO-PAY-" + UUID.randomUUID().toString().substring(0, 8);
                CustomOrder co = CustomOrder.builder()
                                .id(orderId)
                                .customOrderReference("REF-" + orderId)
                                .memberAccountId(memberAccountId)
                                .designRequestId(testOrder.getDesignRequestId())
                                .status("FULL_PAYMENT_PENDING")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                customOrderRepository.save(co);

                CustomOrderQuote quote = CustomOrderQuote.builder()
                                .id("QT-PAY-" + UUID.randomUUID().toString().substring(0, 8))
                                .customOrderId(orderId)
                                .quoteReference("QREF-" + orderId)
                                .quoteGeneration(1)
                                .totalAmount(5_000_000L)
                                .currency("VND")
                                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                                .status("QUOTE_READY")
                                .approvedByManager(true)
                                .paymentStatus("UNPAID")
                                .paymentMode("FULL_CUSTOM_ORDER_PAYMENT")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                customOrderQuoteRepository.save(quote);

                // Member triggers 100% full payment (no cart, no deposit, no installment)
                mockMvc.perform(post("/api/v1/members/me/custom-orders/" + orderId + "/payment")
                                .header("Authorization", "Bearer " + memberToken))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.paymentUrl").exists());

                // Retrieve created payment context
                List<CustomOrderPaymentContext> contexts = paymentContextRepository.findAll();
                CustomOrderPaymentContext context = contexts.stream()
                                .filter(c -> orderId.equals(c.getCustomOrderId()))
                                .findFirst()
                                .orElseThrow(() -> new AssertionError("Payment context must be registered"));

                String merchantRef = context.getMerchantReference();
                org.junit.jupiter.api.Assertions.assertNotNull(merchantRef);

                // Simulate VNPay verified full payment callback
                customOrderService.applyVerifiedFullPayment(
                                co.getCustomOrderReference(),
                                merchantRef,
                                "VNPAY-TXN-SUCCESS-12345",
                                5_000_000L,
                                "VND");

                // Verify order transitioned to PAID once
                CustomOrder paidOrder = customOrderRepository.findById(orderId).orElseThrow();
                org.junit.jupiter.api.Assertions.assertEquals("PAID", paidOrder.getStatus());
                org.junit.jupiter.api.Assertions.assertEquals("PENDING", paidOrder.getProductionStatus());

                CustomOrderQuote paidQuote = customOrderQuoteRepository.findById(quote.getId()).orElseThrow();
                org.junit.jupiter.api.Assertions.assertEquals("PAID", paidQuote.getStatus());

                // Replay callback with exact same transaction -> Idempotency check: no error,
                // status remains PAID
                customOrderService.applyVerifiedFullPayment(
                                co.getCustomOrderReference(),
                                merchantRef,
                                "VNPAY-TXN-SUCCESS-12345",
                                5_000_000L,
                                "VND");

                CustomOrder replayedOrder = customOrderRepository.findById(orderId).orElseThrow();
                org.junit.jupiter.api.Assertions.assertEquals("PAID", replayedOrder.getStatus());
        }

        @Test
        void whenPaymentAmountMismatched_thenRequiresReviewAndOrderNotPaid() throws Exception {
                String orderId = "CO-MISMATCH-" + UUID.randomUUID().toString().substring(0, 8);
                CustomOrder co = CustomOrder.builder()
                                .id(orderId)
                                .customOrderReference("REF-" + orderId)
                                .memberAccountId(memberAccountId)
                                .designRequestId(testOrder.getDesignRequestId())
                                .status("FULL_PAYMENT_PENDING")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                customOrderRepository.save(co);

                CustomOrderQuote quote = CustomOrderQuote.builder()
                                .id("QT-MISMATCH-" + UUID.randomUUID().toString().substring(0, 8))
                                .customOrderId(orderId)
                                .quoteReference("QREF-" + orderId)
                                .quoteGeneration(1)
                                .totalAmount(5_000_000L)
                                .currency("VND")
                                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                                .status("QUOTE_READY")
                                .approvedByManager(true)
                                .paymentStatus("UNPAID")
                                .paymentMode("FULL_CUSTOM_ORDER_PAYMENT")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                customOrderQuoteRepository.save(quote);

                // Initiate payment
                mockMvc.perform(post("/api/v1/members/me/custom-orders/" + orderId + "/payment")
                                .header("Authorization", "Bearer " + memberToken))
                                .andExpect(status().isCreated());

                CustomOrderPaymentContext context = paymentContextRepository.findAll().stream()
                                .filter(c -> orderId.equals(c.getCustomOrderId()))
                                .findFirst()
                                .orElseThrow();

                // Mismatched amount: quote was 5_000_000L, provider callback sends 4_000_000L
                customOrderService.applyVerifiedFullPayment(
                                co.getCustomOrderReference(),
                                context.getMerchantReference(),
                                "VNPAY-TXN-MISMATCH",
                                4_000_000L,
                                "VND");

                // Order remains FULL_PAYMENT_PENDING (NOT marked PAID)
                CustomOrder stillPendingOrder = customOrderRepository.findById(orderId).orElseThrow();
                org.junit.jupiter.api.Assertions.assertEquals("FULL_PAYMENT_PENDING", stillPendingOrder.getStatus());

                // Context marked REVIEW_REQUIRED
                CustomOrderPaymentContext updatedContext = paymentContextRepository.findById(context.getId())
                                .orElseThrow();
                org.junit.jupiter.api.Assertions.assertEquals("REVIEW_REQUIRED", updatedContext.getStatus());
        }

        @Test
        void quoteCommandsAreLockedOnceTheOrderIsPaid() throws Exception {
                DesignRequest request = designRequestRepository.findById(testOrder.getDesignRequestId()).orElseThrow();
                request.setReviewStatus(edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus.ACCEPTED);
                designRequestRepository.save(request);
                CustomOrderQuote paid = customOrderQuoteRepository
                                .findTopByCustomOrderIdOrderByQuoteGenerationDesc(testOrder.getId()).orElseThrow();
                // Past its expiry: before the lock, this let the Member send a paid order back
                // to QUOTE_PENDING.
                paid.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
                customOrderQuoteRepository.save(paid);
                long quotes = customOrderQuoteRepository.countByCustomOrderId(testOrder.getId());

                for (String state : List.of("PAID", "IN_PRODUCTION", "READY_FOR_FULFILMENT", "CLOSED")) {
                        testOrder.setStatus(state);
                        customOrderRepository.save(testOrder);

                        for (String token : List.of(staffToken, managerToken)) {
                                prepareQuote(request.getId(), token)
                                                .andExpect(status().isConflict())
                                                .andExpect(jsonPath("$.code").value("CUSTOM_ORDER_QUOTE_LOCKED"));
                        }
                        mockMvc.perform(post(
                                        "/api/v1/members/me/custom-orders/" + testOrder.getId() + "/request-new-quote")
                                        .header("Authorization", "Bearer " + memberToken))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value("CUSTOM_ORDER_QUOTE_LOCKED"));
                        approve(testOrder.getId(), paid.getId())
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value("CUSTOM_ORDER_QUOTE_LOCKED"));

                        org.junit.jupiter.api.Assertions.assertEquals(state,
                                        customOrderRepository.findById(testOrder.getId()).orElseThrow().getStatus());
                }
                org.junit.jupiter.api.Assertions.assertEquals(quotes,
                                customOrderQuoteRepository.countByCustomOrderId(testOrder.getId()));
                org.junit.jupiter.api.Assertions.assertEquals("APPROVED",
                                customOrderQuoteRepository.findById(paid.getId()).orElseThrow().getStatus());
        }

        @Test
        void onlyTheLatestQuoteOfTheOrderCanBeApproved() throws Exception {
                testOrder.setStatus("QUOTE_PENDING");
                customOrderRepository.save(testOrder);
                CustomOrderQuote first = customOrderQuoteRepository
                                .findTopByCustomOrderIdOrderByQuoteGenerationDesc(testOrder.getId()).orElseThrow();
                first.setStatus("QUOTE_PENDING");
                customOrderQuoteRepository.save(first);
                CustomOrderQuote second = customOrderQuoteRepository.save(pendingQuote(testOrder.getId(), 2));
                CustomOrderQuote foreign = customOrderQuoteRepository.save(pendingQuote("CO-OTHER-ORDER", 9));

                for (CustomOrderQuote refused : List.of(first, foreign)) {
                        approve(testOrder.getId(), refused.getId())
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value("QUOTE_ALREADY_DECIDED"));
                }
                approve(testOrder.getId(), second.getId()).andExpect(status().isOk());
        }

        @Test
        void shouldEnforceStrictStateTransitionsAndMultiRoleSync() throws Exception {
                // Setup order in PAID state
                String coId = "CO-SYNC-" + UUID.randomUUID().toString().substring(0, 8);
                CustomOrder co = CustomOrder.builder()
                                .id(coId)
                                .customOrderReference("REF-" + coId)
                                .memberAccountId(memberAccountId)
                                .designRequestId(testOrder.getDesignRequestId())
                                .status("PAID")
                                .productionStatus("PENDING")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                customOrderRepository.save(co);

                CustomOrderQuote paidQuote = CustomOrderQuote.builder()
                                .id("QT-SYNC-" + UUID.randomUUID().toString().substring(0, 8))
                                .customOrderId(coId)
                                .quoteReference("QREF-" + coId)
                                .quoteGeneration(1)
                                .totalAmount(5_000_000L)
                                .currency("VND")
                                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                                .status("PAID")
                                .approvedByManager(true)
                                .paymentStatus("PAID")
                                .paymentMode("FULL_CUSTOM_ORDER_PAYMENT")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                customOrderQuoteRepository.save(paidQuote);

                // 1. Negative: Attempt to skip stage from PAID directly to CLOSED -> 409
                // CUSTOM_ORDER_STATUS_CONFLICT
                mockMvc.perform(post("/api/v1/custom-orders/" + coId + "/status")
                                .header("Authorization", "Bearer " + staffToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "status", "CLOSED",
                                                "productionStatus", "COMPLETED",
                                                "fulfilmentStatus", "DELIVERED"))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("CUSTOM_ORDER_STATUS_CONFLICT"));

                // 2. Negative: Attempt to set invalid status PRODUCTION_FAILED -> 400
                // CUSTOM_ORDER_STATUS_INVALID
                mockMvc.perform(post("/api/v1/custom-orders/" + coId + "/status")
                                .header("Authorization", "Bearer " + staffToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "status", "PRODUCTION_FAILED",
                                                "productionStatus", "FAILED"))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("CUSTOM_ORDER_STATUS_INVALID"));

                // 3. Step 1: Transition PAID -> IN_PRODUCTION
                mockMvc.perform(post("/api/v1/custom-orders/" + coId + "/status")
                                .header("Authorization", "Bearer " + staffToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "status", "IN_PRODUCTION",
                                                "productionStatus", "IN_PRODUCTION"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("IN_PRODUCTION"))
                                .andExpect(jsonPath("$.productionStatus").value("IN_PRODUCTION"));

                // Member verifies status is synchronized to IN_PRODUCTION
                mockMvc.perform(get("/api/v1/members/me/custom-orders")
                                .header("Authorization", "Bearer " + memberToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[?(@.customOrderReference == '"
                                                + co.getCustomOrderReference() + "')].status").value("IN_PRODUCTION"));

                // 4. Negative: Attempt to rollback from IN_PRODUCTION back to PAID -> 409
                // CUSTOM_ORDER_STATUS_CONFLICT
                mockMvc.perform(post("/api/v1/custom-orders/" + coId + "/status")
                                .header("Authorization", "Bearer " + staffToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "status", "PAID"))))
                                .andExpect(status().isBadRequest()); // Stage doesn't even exist for PAID target in
                                                                     // STAGES map

                // 5. Step 2: Transition IN_PRODUCTION -> READY_FOR_FULFILMENT
                mockMvc.perform(post("/api/v1/custom-orders/" + coId + "/status")
                                .header("Authorization", "Bearer " + staffToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "status", "READY_FOR_FULFILMENT",
                                                "productionStatus", "COMPLETED",
                                                "fulfilmentStatus", "READY_FOR_FULFILMENT"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("READY_FOR_FULFILMENT"))
                                .andExpect(jsonPath("$.productionStatus").value("COMPLETED"))
                                .andExpect(jsonPath("$.fulfilmentStatus").value("READY_FOR_FULFILMENT"));

                // Member verifies status is synchronized to READY_FOR_FULFILMENT and tracking
                // number generated
                mockMvc.perform(get("/api/v1/members/me/custom-orders")
                                .header("Authorization", "Bearer " + memberToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[?(@.customOrderReference == '"
                                                + co.getCustomOrderReference() + "')].status")
                                                .value("READY_FOR_FULFILMENT"))
                                .andExpect(jsonPath("$.items[?(@.customOrderReference == '"
                                                + co.getCustomOrderReference() + "')].trackingNumber").exists());

                // Negative: Direct transition to CLOSED by Staff is rejected (Method 1B courier
                // rule)
                mockMvc.perform(post("/api/v1/custom-orders/" + coId + "/status")
                                .header("Authorization", "Bearer " + staffToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "status", "CLOSED",
                                                "productionStatus", "COMPLETED",
                                                "fulfilmentStatus", "DELIVERED"))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("CUSTOM_ORDER_STATUS_CONFLICT"));

                // 6. Step 3: Staff advances shipment to READY_FOR_CARRIER
                mockMvc.perform(post("/api/v1/custom-orders/" + coId + "/shipment/ready-for-carrier")
                                .header("Authorization", "Bearer " + staffToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.fulfilmentStatus").value("READY_FOR_CARRIER"));

                var shipment = customOrderShipmentRepository
                                .findTopByCustomOrderReferenceOrderByCreatedAtDesc(co.getCustomOrderReference())
                                .orElseThrow();
                String carrierShipmentId = "CUST_SHIP_" + shipment.getId();
                int versionAfterStaff = shipment.getVersion();

                // 7. Step 4: Courier collects parcel (PICKED_UP) -> HANDED_TO_CARRIER &
                // DELIVERING
                mockMvc.perform(post("/api/v1/carrier/shipments/" + carrierShipmentId + "/transitions")
                                .header("Authorization", "Bearer " + carrierToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "action", "PICKED_UP",
                                                "expectedVersion", versionAfterStaff))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("HANDED_TO_CARRIER"));

                mockMvc.perform(get("/api/v1/members/me/custom-orders")
                                .header("Authorization", "Bearer " + memberToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[?(@.customOrderReference == '"
                                                + co.getCustomOrderReference() + "')].status").value("DELIVERING"));

                // 8. Step 5: Courier completes delivery (DELIVERED) -> automatically
                // synchronizes CustomOrder to CLOSED
                mockMvc.perform(post("/api/v1/carrier/shipments/" + carrierShipmentId + "/transitions")
                                .header("Authorization", "Bearer " + carrierToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "action", "DELIVERED",
                                                "expectedVersion", versionAfterStaff + 1))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("DELIVERED"));

                // Both Staff and Member see CLOSED / DELIVERED
                mockMvc.perform(get("/api/v1/custom-orders/" + coId)
                                .header("Authorization", "Bearer " + staffToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CLOSED"))
                                .andExpect(jsonPath("$.fulfilmentStatus").value("DELIVERED"));

                mockMvc.perform(get("/api/v1/members/me/custom-orders")
                                .header("Authorization", "Bearer " + memberToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[?(@.customOrderReference == '"
                                                + co.getCustomOrderReference() + "')].status").value("CLOSED"))
                                .andExpect(jsonPath("$.items[?(@.customOrderReference == '"
                                                + co.getCustomOrderReference() + "')].fulfilmentStatus")
                                                .value("DELIVERED"));
        }

        @Test
        void shouldEnforce10MinutePaymentWindowDecoupledFromQuote() throws Exception {
                String coId = "CO-10M-" + UUID.randomUUID().toString().substring(0, 8);
                CustomOrder co = CustomOrder.builder()
                                .id(coId)
                                .customOrderReference(coId)
                                .memberAccountId(memberAccountId)
                                .designRequestId(testOrder.getDesignRequestId())
                                .status("FULL_PAYMENT_PENDING")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                customOrderRepository.save(co);

                // Quote valid for 7 days
                CustomOrderQuote quote = CustomOrderQuote.builder()
                                .id("QT-10M-" + UUID.randomUUID().toString().substring(0, 8))
                                .customOrderId(coId)
                                .quoteReference("QREF-" + coId)
                                .quoteGeneration(1)
                                .totalAmount(4_000_000L)
                                .currency("VND")
                                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                                .status("QUOTE_READY")
                                .approvedByManager(true)
                                .paymentStatus("UNPAID")
                                .paymentMode("FULL_CUSTOM_ORDER_PAYMENT")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                customOrderQuoteRepository.save(quote);

                // Member initiates payment
                var result = mockMvc.perform(post("/api/v1/members/me/custom-orders/" + coId + "/payment")
                                .header("Authorization", "Bearer " + memberToken))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.paymentUrl").exists())
                                .andReturn();

                // Verify payment context has 10-minute expiry (not 7 days)
                var context = paymentContextRepository.findAll().stream()
                                .filter(c -> coId.equals(c.getCustomOrderId()))
                                .findFirst()
                                .orElseThrow();
                Instant expiresAt = context.getExpiresAt();
                org.junit.jupiter.api.Assertions.assertNotNull(expiresAt);
                long minutesUntilExpiry = java.time.Duration.between(Instant.now(), expiresAt).toMinutes();
                org.junit.jupiter.api.Assertions.assertTrue(minutesUntilExpiry <= 10 && minutesUntilExpiry >= 9,
                                "Payment window must be capped at 10 minutes, was: " + minutesUntilExpiry);

                // Verify Browser Return redirect routes directly to custom order detail with
                // response code
                mockMvc.perform(get("/payment/vnpay/return")
                                .param("vnp_ResponseCode", "00")
                                .param("vnp_TxnRef", context.getMerchantReference())
                                .param("vnp_OrderInfo", "Custom Order: " + co.getCustomOrderReference())
                                .param("vnp_Amount", "400000000"))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                                .string("Location",
                                                                org.hamcrest.Matchers.containsString(
                                                                                "/account/my-custom-orders/" + co
                                                                                                .getCustomOrderReference())));
        }

        @Test
        void anUnpaidOrderCanStillBeQuotedAgain() throws Exception {
                DesignRequest request = designRequestRepository.findById(testOrder.getDesignRequestId()).orElseThrow();
                request.setReviewStatus(edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus.ACCEPTED);
                designRequestRepository.save(request);

                for (String state : List.of("QUOTE_PENDING", "FULL_PAYMENT_PENDING", "QUOTE_EXPIRED")) {
                        testOrder.setStatus(state);
                        customOrderRepository.save(testOrder);

                        prepareQuote(request.getId(), staffToken).andExpect(status().isOk());
                }
        }

        private org.springframework.test.web.servlet.ResultActions prepareQuote(String designRequestId, String token)
                        throws Exception {
                return mockMvc.perform(post("/api/v1/design-requests/" + designRequestId + "/quote")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "totalAmount", "7000000",
                                                "currency", "VND",
                                                "quoteExpiresAt", Instant.now().plus(7, ChronoUnit.DAYS).toString(),
                                                "managerReason", "Re-quote"))));
        }

        private org.springframework.test.web.servlet.ResultActions approve(String orderId, String quoteId)
                        throws Exception {
                return mockMvc.perform(post("/api/v1/custom-orders/" + orderId + "/quotes/" + quoteId + "/approve")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"managerReason\":\"Approve\"}"));
        }

        private CustomOrderQuote pendingQuote(String orderId, int generation) {
                return CustomOrderQuote.builder()
                                .id("QT-GEN-" + UUID.randomUUID().toString().substring(0, 8))
                                .customOrderId(orderId)
                                .quoteReference("QREF-" + UUID.randomUUID().toString().substring(0, 8))
                                .quoteGeneration(generation)
                                .totalAmount(6_000_000L)
                                .currency("VND")
                                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                                .status("QUOTE_PENDING")
                                .approvedByManager(false)
                                .paymentStatus("UNPAID")
                                .paymentMode("FULL_CUSTOM_ORDER_PAYMENT")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
        }
}
