package edu.fpt.sep490_g22.ppswbs_backend.designreview;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequestImage;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReferenceImageStoragePort;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.DesignRequestImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.DesignRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
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

/** Spec 004 §7: reference images go only to the request owner, Staff or a Manager. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DesignImageAccessIT {

    private static final String PATH = "/api/v1/design-requests/images/IMG-TEST0001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenVerificationService tokenVerificationService;

    @MockitoBean
    private AccountFacade accountFacade;

    @MockitoBean
    private DesignRequestImageRepository imageRepository;

    @MockitoBean
    private DesignRequestRepository requestRepository;

    @MockitoBean
    private ReferenceImageStoragePort storagePort;

    @BeforeEach
    void setUp() {
        account("owner", 11L, ActorRole.MEMBER);
        account("other", 12L, ActorRole.MEMBER);
        account("staff", 21L, ActorRole.STAFF);
        account("manager", 31L, ActorRole.MANAGER);
        account("admin", 41L, ActorRole.ADMIN);

        when(imageRepository.findByImageReference("IMG-TEST0001")).thenReturn(Optional.of(DesignRequestImage.builder()
                .designRequestId("dr-1").imageReference("IMG-TEST0001").storagePath("path").contentType("image/png")
                .originalFilename("ring.png").fileSizeBytes(3L).build()));
        DesignRequest request = new DesignRequest();
        request.setOwnerAccountId(11L);
        when(requestRepository.findById("dr-1")).thenReturn(Optional.of(request));
        when(storagePort.loadTemporary("path")).thenReturn(new byte[] {1, 2, 3});
    }

    @Test
    void anonymousCallerIsRefused() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/custom-design-requests/images/IMG-TEST0001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerStaffAndManagerReceiveTheImage() throws Exception {
        for (String caller : List.of("owner", "staff", "manager")) {
            mockMvc.perform(get(PATH).header("Authorization", "Bearer " + caller)).andExpect(status().isOk());
        }
    }

    @Test
    void otherMemberAndAdminGetNotFound() throws Exception {
        for (String caller : List.of("other", "admin")) {
            mockMvc.perform(get(PATH).header("Authorization", "Bearer " + caller)).andExpect(status().isNotFound());
        }
    }

    private void account(String name, long accountId, ActorRole role) {
        String uid = "uid-image-" + name;
        when(tokenVerificationService.verify(name))
                .thenReturn(new VerifiedIdentity(uid, name + "@example.test", true, Instant.now()));
        when(accountFacade.resolveActor(uid, true)).thenReturn(Optional.of(
                new CurrentAccountActor(accountId, role, ActorStatus.ACTIVE, true, List.of())));
        when(accountFacade.resolveAccount(uid)).thenReturn(Optional.of(new AccountSummary(
                accountId, role, ActorStatus.ACTIVE, name + "@example.test", Instant.now(), Instant.now())));
    }
}
