package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueMediaCleanupService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueMediaService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService.ImageInput;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductImageService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.Concurrently;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * T041 on MySQL with a controlled provider port (the real Cloudinary is T051/CatalogueCloudinaryIT): image count,
 * alt text and position boundaries, upload retry by checksum, provider failure then retry, and the race between
 * attaching an image and cleanup deleting it (FR-008/011/012/026/028). Signature/byte boundaries of the content
 * itself are unit-tested in CatalogueMediaStagingTests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueMediaIT {

    @MockitoBean private MediaStoragePort storage;
    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private ProductImageService images;
    @Autowired private CatalogueMediaService mediaService;
    @Autowired private CatalogueMediaCleanupService cleanup;

    @BeforeEach
    void providerWorks() {
        reset(storage);
        when(storage.storageKeyFor(any())).thenAnswer(call -> "it/" + call.getArgument(0));
        when(storage.putIfAbsent(anyString(), any())).thenAnswer(call ->
                new MediaStoragePort.StoredObject(call.getArgument(0), "asset", "1", "png"));
    }

    @Test
    void tenImagesAreAcceptedAndAnEleventhIsRefused() {
        long productId = fixture.draftProduct(STAFF);
        List<ImageInput> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add(new ImageInput(fixture.readyMedia(STAFF, Instant.now()), "Ảnh " + i, i));
        }

        assertCode(() -> replace(productId, eleven), CatalogueErrorCode.VALIDATION_ERROR);
        List<ImageInput> ten = eleven.subList(0, 10).stream()
                .map(image -> new ImageInput(image.mediaId(), image.altText(), 9 - image.position())).toList();
        replace(productId, ten);

        assertThat(fixture.images(productId)).hasSize(10)
                .extracting(ProductImage::getDisplayPosition).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(fixture.images(productId).get(0).getMediaObjectId()).isEqualTo(eleven.get(9).mediaId());
    }

    @Test
    void altTextAndPositionBoundaries() {
        long productId = fixture.draftProduct(STAFF);
        UUID first = fixture.readyMedia(STAFF, Instant.now());
        UUID second = fixture.readyMedia(STAFF, Instant.now());

        replace(productId, List.of(new ImageInput(first, "a".repeat(200), 0)));
        assertCode(() -> replace(productId, List.of(new ImageInput(first, "a".repeat(201), 0))),
                CatalogueErrorCode.VALIDATION_ERROR);
        assertCode(() -> replace(productId, List.of(new ImageInput(first, "   ", 0))), CatalogueErrorCode.VALIDATION_ERROR);
        assertCode(() -> replace(productId, List.of(new ImageInput(first, "A", 0), new ImageInput(second, "B", 0))),
                CatalogueErrorCode.VALIDATION_ERROR);
        assertCode(() -> replace(productId, List.of(new ImageInput(first, "A", 10))), CatalogueErrorCode.VALIDATION_ERROR);
        assertCode(() -> replace(productId, List.of(new ImageInput(first, "A", 0), new ImageInput(first, "B", 1))),
                CatalogueErrorCode.VALIDATION_ERROR);
        assertThat(fixture.images(productId)).singleElement()
                .satisfies(image -> assertThat(image.getAltText()).hasSize(200));
    }

    @Test
    void anUploadRetryIsRecognisedByItsChecksumAndOtherContentUnderTheSameKeyIsRefused() throws Exception {
        UUID key = UUID.randomUUID();
        byte[] png = png();

        CatalogueMediaService.UploadResult first = mediaService.upload(STAFF, key, "image/png", png);
        CatalogueMediaService.UploadResult retry = mediaService.upload(STAFF, key, "image/png", png);

        assertThat(retry.mediaId()).isEqualTo(first.mediaId());
        assertThat(fixture.media(first.mediaId()).getState()).isEqualTo(CatalogueMediaObject.State.READY);
        assertCode(() -> mediaService.upload(STAFF, key, "image/png", png()), CatalogueErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void aProviderFailureLeavesTheUploadStagedAndTheSameKeyFinishesItLater() throws Exception {
        UUID key = UUID.randomUUID();
        byte[] png = png();
        doThrow(new CatalogueException(CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE))
                .when(storage).putIfAbsent(anyString(), any());

        assertCode(() -> mediaService.upload(STAFF, key, "image/png", png), CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE);
        providerWorks();
        CatalogueMediaService.UploadResult done = mediaService.upload(STAFF, key, "image/png", png);

        assertThat(done.state()).isEqualTo("READY");
        assertThat(fixture.media(done.mediaId()).getState()).isEqualTo(CatalogueMediaObject.State.READY);
    }

    @Test
    void attachingAnImageWhileCleanupDeletesItEndsInOneConsistentState() {
        for (int round = 0; round < 3; round++) {
            long productId = fixture.draftProduct(STAFF);
            UUID old = fixture.readyMedia(STAFF, Instant.now().minus(Duration.ofDays(2)));
            long version = fixture.productVersion(productId);

            List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                    () -> cleanup.clean(old, Instant.now()),
                    () -> images.replace(STAFF, UUID.randomUUID(), Long.toString(productId), version,
                            List.of(new ImageInput(old, "Ảnh", 0))));

            CatalogueMediaObject media = fixture.media(old);
            if (outcomes.get(1).succeeded()) {
                assertThat(outcomes.get(0).value()).isEqualTo(CatalogueMediaCleanupService.Result.SKIPPED);
                assertThat(media.getState()).isEqualTo(CatalogueMediaObject.State.READY);
                assertThat(fixture.images(productId)).hasSize(1);
            } else {
                assertThat(outcomes.get(1).failure()).isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.MEDIA_NOT_FOUND));
                assertThat(media.getState()).isEqualTo(CatalogueMediaObject.State.DELETED);
                assertThat(fixture.images(productId)).isEmpty();
            }
        }
    }

    private void replace(long productId, List<ImageInput> wanted) {
        images.replace(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId), wanted);
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(6, 6, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, ThreadLocalRandom.current().nextInt(0xFFFFFF));
        image.setRGB(5, 5, ThreadLocalRandom.current().nextInt(0xFFFFFF));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void assertCode(ThrowableAssert.ThrowingCallable action, CatalogueErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CatalogueException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }
}
