package edu.fpt.sep490_g22.ppswbs_backend.designreview;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.DesignRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.web.DesignReviewDtos.QuoteProjectionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.custommanufacturing.service.CustomOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 004 2.1: a quote is readable only by the request owner, Staff or a Manager. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DesignQuoteAccessIT {

    private static final List<String> BASES = List.of("/api/v1/design-requests/", "/api/v1/custom-design-requests/");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenVerificationService tokenVerificationService;

    @MockitoBean
    private AccountFacade accountFacade;

    @MockitoBean
    private DesignRequestRepository requestRepository;

    @MockitoBean
    private CustomOrderService customOrderService;

    @BeforeEach
    void setUp() {
        account("owner", 11L, ActorRole.MEMBER);
        account("other", 12L, ActorRole.MEMBER);
        account("staff", 21L, ActorRole.STAFF);
        account("manager", 31L, ActorRole.MANAGER);
        account("admin", 41L, ActorRole.ADMIN);

        for (String id : List.of("quoted", "unquoted")) {
            DesignRequest request = new DesignRequest();
            request.setOwnerAccountId(11L);
            when(requestRepository.findById(id)).thenReturn(Optional.of(request));
        }
        when(customOrderService.findLatestQuoteByDesignRequestId("quoted"))
                .thenReturn(Optional.of(QuoteProjectionDto.builder().totalAmount("15000000").build()));
        when(customOrderService.findLatestQuoteByDesignRequestId("unquoted")).thenReturn(Optional.empty());
    }

    @Test
    void anonymousCallerIsRefused() throws Exception {
        for (String base : BASES) {
            mockMvc.perform(get(base + "quoted/quote")).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void ownerStaffAndManagerReadTheQuote() throws Exception {
        for (String base : BASES) {
            for (String caller : List.of("owner", "staff", "manager")) {
                mockMvc.perform(get(base + "quoted/quote").header("Authorization", "Bearer " + caller))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    void ownerGetsNoContentWhileThereIsNoQuote() throws Exception {
        mockMvc.perform(get(BASES.get(0) + "unquoted/quote").header("Authorization", "Bearer owner"))
                .andExpect(status().isNoContent());
    }

    @Test
    void otherMemberAndAdminGetNotFoundWithOrWithoutAQuote() throws Exception {
        for (String base : BASES) {
            for (String caller : List.of("other", "admin")) {
                for (String id : List.of("quoted", "unquoted", "unknown")) {
                    mockMvc.perform(get(base + id + "/quote").header("Authorization", "Bearer " + caller))
                            .andExpect(status().isNotFound());
                }
            }
        }
    }

    private void account(String name, long accountId, ActorRole role) {
        String uid = "uid-quote-" + name;
        when(tokenVerificationService.verify(name))
                .thenReturn(new VerifiedIdentity(uid, name + "@example.test", true, Instant.now()));
        when(accountFacade.resolveActor(uid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(accountId, role, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(uid)).thenReturn(Optional.of(new AccountSummary(
                accountId, role, ActorStatus.ACTIVE, name + "@example.test", Instant.now(), Instant.now())));
    }
}
