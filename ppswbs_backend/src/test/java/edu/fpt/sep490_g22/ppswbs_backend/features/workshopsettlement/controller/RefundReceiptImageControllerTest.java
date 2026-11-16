package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RefundReceiptImageService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 003.3 T154: both receipt-image routes are Manager only (Staff 403 before any storage call), no provider URL leaves. */
class RefundReceiptImageControllerTest {

    private static final CurrentAccountActor MANAGER =
            new CurrentAccountActor(22L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of(1L));
    private static final CurrentAccountActor STAFF =
            new CurrentAccountActor(11L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(1L));

    private final RefundReceiptImageService receiptImages = mock(RefundReceiptImageService.class);
    private final WorkshopSettlementAuthorizationService authorization =
            mock(WorkshopSettlementAuthorizationService.class);
    private final RefundReceiptImageController controller =
            new RefundReceiptImageController(receiptImages, authorization);

    @Test
    void staffIncludingAnAssignedOneIsRefusedOnBothRoutesBeforeAnyStorageCall() {
        when(authorization.requireManager(STAFF)).thenThrow(new WorkshopSettlementAuthorizationException("MANAGER_REQUIRED"));
        var file = new MockMultipartFile("file", "r.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> controller.upload("REFUND-1", "idempotency-key-0001", file, auth(STAFF)))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        assertThatThrownBy(() -> controller.read("REFUND-1", auth(STAFF)))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        verifyNoInteractions(receiptImages);
    }

    @Test
    void aManagerUploadsAndGets201WithMetadataOnly() {
        when(authorization.requireManager(MANAGER)).thenReturn(MANAGER);
        var now = Instant.now();
        when(receiptImages.upload(eq("REFUND-1"), eq(22L), eq("idempotency-key-0001"), eq("image/png"), any()))
                .thenReturn(new RefundReceiptImageService.ReceiptImageView("IMG-1", "image/png", 1, "abc", now));

        var response = controller.upload("REFUND-1", "idempotency-key-0001",
                new MockMultipartFile("file", "r.png", "image/png", new byte[]{1}), auth(MANAGER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().receiptImageId()).isEqualTo("IMG-1");
        assertThat(response.getBody().toString()).doesNotContain("http");
    }

    @Test
    void theStreamCarriesNoStoreAndNosniffAndTheStoredContentType() {
        when(authorization.requireManager(MANAGER)).thenReturn(MANAGER);
        when(receiptImages.read("REFUND-1"))
                .thenReturn(new RefundReceiptImageService.ReceiptContent("image/webp", new byte[]{9, 9}));

        var response = controller.read("REFUND-1", auth(MANAGER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/webp");
        assertThat(response.getHeaders().getLocation()).isNull();
        assertThat(response.getBody()).containsExactly(9, 9);
    }

    private static Authentication auth(CurrentAccountActor actor) {
        return new UsernamePasswordAuthenticationToken(actor, null, List.of());
    }
}
