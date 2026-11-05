package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueMediaCleanupService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueMediaCleanupService.Result;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService.ImageInput;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaReferenceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductImageService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * T047 on MySQL with a stand-in provider: 24-hour and 30-day eligibility, references and product images
 * block deletion, DELETE_PENDING committed before the provider call, retry after a provider failure, and no
 * attach of an object being deleted (FR-008/024/027). The real Cloudinary call is T051.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueMediaCleanupIT {

    @MockitoBean private MediaStoragePort storage;
    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private CatalogueMediaCleanupService cleanup;
    @Autowired private ProductImageService imageService;
    @Autowired private DeleteProductService deletion;
    @Autowired private MediaReferenceService mediaReferences;
    @Autowired private TransactionTemplate transaction;

    @Test
    void neverAttachedForADayIsDeletedButAFreshUploadIsKept() {
        Instant now = Instant.now();
        UUID old = fixture.readyMedia(STAFF, now.minus(Duration.ofHours(25)));
        UUID fresh = fixture.readyMedia(STAFF, now.minus(Duration.ofHours(23)));

        assertThat(cleanup.clean(old, now)).isEqualTo(Result.DELETED);
        assertThat(cleanup.clean(fresh, now)).isEqualTo(Result.SKIPPED);

        CatalogueMediaObject deleted = fixture.media(old);
        assertThat(deleted.getState()).isEqualTo(CatalogueMediaObject.State.DELETED);
        assertThat(deleted.getDeleteRequestedAt()).isNotNull();
        verify(storage).deleteIfExists(deleted.getStorageKey());
        assertThat(fixture.media(fresh).getState()).isEqualTo(CatalogueMediaObject.State.READY);
        assertThat(cleanup.clean(old, now)).isEqualTo(Result.SKIPPED);
    }

    @Test
    void anAttachedImageAndTheImagesOfADeletedProductAreKept() {
        long productId = fixture.draftProduct(STAFF);
        UUID attached = fixture.readyMedia(STAFF, Instant.now().minus(Duration.ofDays(40)));
        imageService.replace(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                List.of(new ImageInput(attached, "Ảnh", 0)));
        Instant later = Instant.now().plus(Duration.ofDays(60));

        assertThat(cleanup.clean(attached, later)).isEqualTo(Result.SKIPPED);
        deletion.delete(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId));
        assertThat(cleanup.clean(attached, later)).isEqualTo(Result.SKIPPED);
        assertThat(fixture.media(attached).getState()).isEqualTo(CatalogueMediaObject.State.READY);
        verify(storage, never()).deleteIfExists(anyString());
    }

    @Test
    void aDetachedImageIsDeletedOnlyAfterThirtyDays() {
        long productId = fixture.draftProduct(STAFF);
        UUID media = fixture.readyMedia(STAFF, Instant.now());
        UUID keep = fixture.readyMedia(STAFF, Instant.now());
        imageService.replace(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                List.of(new ImageInput(media, "Ảnh", 0)));
        imageService.replace(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                List.of(new ImageInput(keep, "Ảnh khác", 0)));
        Instant detachedAt = fixture.media(media).getLastUnreferencedAt();

        assertThat(cleanup.clean(media, detachedAt.plus(Duration.ofDays(29)))).isEqualTo(Result.SKIPPED);
        assertThat(cleanup.clean(media, detachedAt.plus(Duration.ofDays(31)))).isEqualTo(Result.DELETED);
        assertThat(fixture.media(media).getState()).isEqualTo(CatalogueMediaObject.State.DELETED);
    }

    @Test
    void aProviderFailureLeavesDeletePendingWhichBlocksAttachAndIsRetried() {
        Instant now = Instant.now();
        UUID old = fixture.readyMedia(STAFF, now.minus(Duration.ofDays(2)));
        String key = fixture.media(old).getStorageKey();
        doThrow(new CatalogueException(CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE)).when(storage).deleteIfExists(key);

        assertThat(cleanup.clean(old, now)).isEqualTo(Result.RETRY_LATER);
        assertThat(fixture.media(old).getState()).isEqualTo(CatalogueMediaObject.State.DELETE_PENDING);
        long productId = fixture.draftProduct(STAFF);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> mediaReferences.attachOwnedMedia(STAFF,
                MediaReferenceService.OWNER_MODULE, MediaReferenceService.productRef(productId), List.of(old), now)))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.MEDIA_NOT_FOUND));

        doNothing().when(storage).deleteIfExists(key);
        assertThat(cleanup.clean(old, now)).isEqualTo(Result.DELETED);
        assertThat(fixture.media(old).getState()).isEqualTo(CatalogueMediaObject.State.DELETED);
    }
}
