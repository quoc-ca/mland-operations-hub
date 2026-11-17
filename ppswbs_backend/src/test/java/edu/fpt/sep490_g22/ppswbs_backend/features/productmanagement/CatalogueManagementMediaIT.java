package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/catalogue-management/media/{mediaId}} (contract 2026-11-03) over HTTP with the real security
 * chain on MySQL: Staff/Manager preview an image of any product that is not soft-deleted, draft or hidden included,
 * and their own unattached uploads; the bytes are streamed with {@code no-store}, never the provider URL. Firebase verification, the 001 actor
 * lookup and the provider port are stubbed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueManagementMediaIT {

    private static final String BASE = "/api/v1/catalogue-management/media/";
    private static final byte[] BYTES = {1, 2, 3, 4};
    private static final Map<String, CurrentAccountActor> ACTORS = Map.of(
            "staff", actor(9201L, ActorRole.STAFF),
            "manager", actor(9202L, ActorRole.MANAGER),
            "member", actor(9203L, ActorRole.MEMBER));

    @MockitoBean private TokenVerificationService tokens;
    @MockitoBean private AccountFacade accounts;
    @MockitoBean private MediaStoragePort storage;
    @Autowired private MockMvc mvc;
    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private DeleteProductService deletes;

    @BeforeEach
    void stub() {
        when(tokens.verify(anyString())).thenAnswer(call -> new VerifiedIdentity(call.getArgument(0), "x@example.test",
                true, Instant.now()));
        when(accounts.resolveActor(anyString(), anyBoolean()))
                .thenAnswer(call -> Optional.ofNullable(ACTORS.get((String) call.getArgument(0))));
        when(storage.open(any())).thenAnswer(call -> Optional.of(new ByteArrayInputStream(BYTES)));
    }

    @Test
    void staffSeeAnImageOfADraftThatThePublicCannot() throws Exception {
        long draft = fixture.draftProduct("9201");
        fixture.addReadyImage(draft, "9201");
        UUID image = fixture.images(draft).get(0).getMediaObjectId();

        for (String role : new String[] {"staff", "manager"}) {
            mvc.perform(as(role, get(BASE + image)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(content().contentType("image/webp"))
                    .andExpect(content().bytes(BYTES));
        }
        mvc.perform(get("/api/v1/product-media/" + image))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void anUnattachedUploadIsVisibleOnlyToTheActorWhoUploadedIt() throws Exception {
        UUID upload = fixture.readyMedia("9201", Instant.now());

        mvc.perform(as("staff", get(BASE + upload))).andExpect(status().isOk()).andExpect(content().bytes(BYTES));
        mvc.perform(as("manager", get(BASE + upload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void anImageOfASoftDeletedProductIsGoneLikeItsDetail() throws Exception {
        long draft = fixture.draftProduct("9201");
        fixture.addReadyImage(draft, "9201");
        UUID image = fixture.images(draft).get(0).getMediaObjectId();
        mvc.perform(as("staff", get(BASE + image))).andExpect(status().isOk());

        deletes.delete("9201", UUID.randomUUID(), Long.toString(draft), fixture.productVersion(draft));

        mvc.perform(as("staff", get("/api/v1/catalogue-management/products/" + draft))).andExpect(status().isNotFound());
        mvc.perform(as("staff", get(BASE + image)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void needsAnActiveStaffOrManager() throws Exception {
        long draft = fixture.draftProduct("9201");
        fixture.addReadyImage(draft, "9201");
        UUID image = fixture.images(draft).get(0).getMediaObjectId();

        mvc.perform(get(BASE + image)).andExpect(status().isUnauthorized());
        mvc.perform(as("member", get(BASE + image)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATALOGUE_ACCESS_DENIED"));
    }

    @Test
    void unknownLostOrUnreachableMediaAreTypedErrors() throws Exception {
        long draft = fixture.draftProduct("9201");
        fixture.addReadyImage(draft, "9201");
        UUID image = fixture.images(draft).get(0).getMediaObjectId();

        mvc.perform(as("staff", get(BASE + UUID.randomUUID())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        mvc.perform(as("staff", get(BASE + "not-a-uuid")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));

        when(storage.open(any())).thenThrow(new CatalogueException(CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE));
        mvc.perform(as("staff", get(BASE + image)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MEDIA_DEPENDENCY_UNAVAILABLE"));

        doReturn(Optional.empty()).when(storage).open(any());
        when(storage.verifyReadable(any())).thenReturn(MediaStoragePort.Availability.MISSING);
        mvc.perform(as("staff", get(BASE + image)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private static CurrentAccountActor actor(long id, ActorRole role) {
        return new CurrentAccountActor(id, role, ActorStatus.ACTIVE, true, List.of());
    }
}
