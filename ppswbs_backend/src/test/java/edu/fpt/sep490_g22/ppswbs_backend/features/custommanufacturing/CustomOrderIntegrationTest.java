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
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.dao.CustomOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.domain.CustomOrderQuote;
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
}
