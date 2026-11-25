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

    private CustomOrder testOrder;

    @BeforeEach
    void setUp() {
        // Member
        when(tokenVerificationService.verify(memberToken)).thenReturn(
                new VerifiedIdentity(memberUid, "member-co@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(memberUid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(memberAccountId, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(memberUid)).thenReturn(Optional.of(
                new AccountSummary(memberAccountId, ActorRole.MEMBER, ActorStatus.ACTIVE, "member-co@mland.vn", Instant.now(), Instant.now())));

        // Staff
        when(tokenVerificationService.verify(staffToken)).thenReturn(
                new VerifiedIdentity(staffUid, "staff-co@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(staffUid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(staffAccountId, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(staffUid)).thenReturn(Optional.of(
                new AccountSummary(staffAccountId, ActorRole.STAFF, ActorStatus.ACTIVE, "staff-co@mland.vn", Instant.now(), Instant.now())));

        // Manager
        when(tokenVerificationService.verify(managerToken)).thenReturn(
                new VerifiedIdentity(managerUid, "manager-co@mland.vn", true, Instant.now()));
        when(accountFacade.resolveActor(managerUid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(managerAccountId, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(managerUid)).thenReturn(Optional.of(
                new AccountSummary(managerAccountId, ActorRole.MANAGER, ActorStatus.ACTIVE, "manager-co@mland.vn", Instant.now(), Instant.now())));

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
                .andExpect(jsonPath("$.items[0].customOrderReference").value(testOrder.getCustomOrderReference()))
                .andExpect(jsonPath("$.items[0].status").value("PAID"));
    }

    @Test
    void whenOperationsViewsOrder_thenStaffAccessSucceedsAndMemberForbidden() throws Exception {
        // Staff accessing operations endpoint -> 200 OK
        mockMvc.perform(get("/api/v1/custom-orders/" + testOrder.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customOrderReference").value(testOrder.getCustomOrderReference()));

        // Member accessing operations endpoint -> 403 Forbidden
        mockMvc.perform(get("/api/v1/custom-orders/" + testOrder.getId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenStaffUpdatesStatus_thenStatusTransitionsCorrectly() throws Exception {
        Map<String, String> body = Map.of(
                "status", "IN_PRODUCTION",
                "productionStatus", "IN_PRODUCTION"
        );

        mockMvc.perform(post("/api/v1/custom-orders/" + testOrder.getId() + "/status")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PRODUCTION"))
                .andExpect(jsonPath("$.productionStatus").value("IN_PRODUCTION"));
    }

    @Test
    void whenStaffPreparesQuoteAndManagerApproves_thenTwoStepQuoteTransitionsCorrectly() throws Exception {
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

        // Step 1: Staff prepares quote -> quote status QUOTE_PENDING, approvedByManager = false
        Map<String, Object> quoteBody = Map.of(
                "totalAmount", "8500000",
                "currency", "VND",
                "quoteExpiresAt", Instant.now().plus(7, ChronoUnit.DAYS).toString(),
                "managerReason", "Báo giá gia công nhẫn đá quý"
        );

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

        // Step 2: Manager approves quote -> quote status QUOTE_READY, approvedByManager = true, order FULL_PAYMENT_PENDING
        Map<String, String> approveBody = Map.of(
                "managerReason", "Phê duyệt mức giá gia công 8.500.000 VND"
        );

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
    void whenStaffAttemptsToApproveQuote_thenForbidden403() throws Exception {
        String drId = "DR-STAFF-APP-" + UUID.randomUUID().toString().substring(0, 8);
        DesignRequest dr = DesignRequest.builder()
                .id(drId)
                .requestReference("DR-REF-" + drId)
                .ownerAccountId(memberAccountId)
                .ownershipType("MEMBER")
                .description("Yêu cầu thử nghiệm staff tự duyệt")
                .requestVersion(1)
                .idempotencyKey("idemp-dr-" + UUID.randomUUID())
                .requestStatus("ACTIVE")
                .reviewStatus(edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus.NEED_REVIEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        designRequestRepository.save(dr);

        // Staff attempts approval -> 403 Forbidden
        mockMvc.perform(post("/api/v1/design-requests/" + drId + "/quote/approve")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_AUTHORIZED"));
    }

    @Test
    void whenMemberPaysFull100PercentViaVnpay_thenIdempotentlyTransitionsToPaidOnce() throws Exception {
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
                "VND"
        );

        // Verify order transitioned to PAID once
        CustomOrder paidOrder = customOrderRepository.findById(orderId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("PAID", paidOrder.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", paidOrder.getProductionStatus());

        CustomOrderQuote paidQuote = customOrderQuoteRepository.findById(quote.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("PAID", paidQuote.getStatus());

        // Replay callback with exact same transaction -> Idempotency check: no error, status remains PAID
        customOrderService.applyVerifiedFullPayment(
                co.getCustomOrderReference(),
                merchantRef,
                "VNPAY-TXN-SUCCESS-12345",
                5_000_000L,
                "VND"
        );

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
                "VND"
        );

        // Order remains FULL_PAYMENT_PENDING (NOT marked PAID)
        CustomOrder stillPendingOrder = customOrderRepository.findById(orderId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("FULL_PAYMENT_PENDING", stillPendingOrder.getStatus());

        // Context marked REVIEW_REQUIRED
        CustomOrderPaymentContext updatedContext = paymentContextRepository.findById(context.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("REVIEW_REQUIRED", updatedContext.getStatus());
    }
}
