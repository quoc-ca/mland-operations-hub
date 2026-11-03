package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueMediaRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.MediaReferenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.MediaReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T016: image verification, staging with retry reconciliation, and the media reference registry. */
class CatalogueMediaStagingTests {

    private static final Instant NOW = Instant.parse("2026-10-26T05:00:00Z");

    @Nested
    class Inspector {

        @Test
        void acceptsMatchingPngAndJpeg() throws Exception {
            assertThat(ImageContentInspector.verify("image/png", image("png"))).isEqualTo("image/png");
            assertThat(ImageContentInspector.verify("image/jpeg", image("jpg"))).isEqualTo("image/jpeg");
        }

        @Test
        void acceptsStructurallyValidWebp() {
            assertThat(ImageContentInspector.verify("image/webp", webpLossless())).isEqualTo("image/webp");
        }

        @Test
        void declaredTypeMustMatchTheSignature() throws Exception {
            byte[] png = image("png");
            assertCode(() -> ImageContentInspector.verify("image/jpeg", png),
                    CatalogueErrorCode.MEDIA_TYPE_UNSUPPORTED);
        }

        @Test
        void gifTruncatedAndOversizedFilesAreRejected() throws Exception {
            assertCode(() -> ImageContentInspector.verify("image/gif", "GIF89a....".getBytes(StandardCharsets.US_ASCII)),
                    CatalogueErrorCode.MEDIA_TYPE_UNSUPPORTED);
            byte[] png = image("png");
            byte[] truncated = java.util.Arrays.copyOf(png, 12);
            assertCode(() -> ImageContentInspector.verify("image/png", truncated),
                    CatalogueErrorCode.MEDIA_TYPE_UNSUPPORTED);
            byte[] large = new byte[5_000_001];
            assertCode(() -> ImageContentInspector.verify("image/png", large), CatalogueErrorCode.MEDIA_TOO_LARGE);
        }
    }

    @Nested
    class Staging {

        private CatalogueMediaRepository media;
        private MediaStoragePort storage;
        private CatalogueCommandExecutor executor;
        private CatalogueMediaService service;
        private final UUID commandId = UUID.randomUUID();

        @BeforeEach
        void setUp() {
            media = mock(CatalogueMediaRepository.class);
            storage = mock(MediaStoragePort.class);
            var receipts = mock(edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueCommandReceiptRepository.class);
            when(receipts.findByActorIdAndActionAndCommandId(anyString(), anyString(), any())).thenReturn(Optional.empty());
            executor = new CatalogueCommandExecutor(receipts, new NoopTransactions(), new ObjectMapper());
            service = new CatalogueMediaService(media, storage, executor, mock(CatalogueAuditWriter.class),
                    new NoopTransactions());
            when(storage.storageKeyFor(any())).thenAnswer(call -> "test/" + call.getArgument(0));
            when(media.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        }

        @Test
        void reservesUploadsOutsideTransactionThenMarksReady() throws Exception {
            byte[] png = image("png");
            when(media.findByUploadedByAndCommandId("staff-1", commandId)).thenReturn(Optional.empty());
            when(storage.putIfAbsent(anyString(), any()))
                    .thenAnswer(call -> new MediaStoragePort.StoredObject(call.getArgument(0), "asset", "1", "png"));
            when(media.lockAllByIdIn(anyList())).thenAnswer(call -> {
                var captured = org.mockito.ArgumentCaptor.forClass(CatalogueMediaObject.class);
                verify(media).saveAndFlush(captured.capture());
                return List.of(captured.getValue());
            });

            var result = service.upload("staff-1", commandId, "image/png", png);

            assertThat(result.state()).isEqualTo("READY");
            assertThat(result.contentType()).isEqualTo("image/png");
            assertThat(result.byteCount()).isEqualTo(png.length);
            verify(storage).putIfAbsent(eq("test/" + result.mediaId()), any());
        }

        @Test
        void sameKeyWithAnotherFileIsRejectedBeforeUploading() throws Exception {
            CatalogueMediaObject existing = CatalogueMediaObject.stage(UUID.randomUUID(), "test/x", "other-checksum",
                    "image/png", 10, "staff-1", commandId, NOW);
            when(media.findByUploadedByAndCommandId("staff-1", commandId)).thenReturn(Optional.of(existing));
            byte[] png = image("png");

            assertCode(() -> service.upload("staff-1", commandId, "image/png", png),
                    CatalogueErrorCode.IDEMPOTENCY_KEY_REUSED);
            verify(storage, never()).putIfAbsent(anyString(), any());
        }

        @Test
        void providerFailureLeavesTheObjectStagedForARetry() throws Exception {
            byte[] png = image("png");
            CatalogueMediaObject staged = CatalogueMediaObject.stage(UUID.randomUUID(), "test/x",
                    CatalogueMediaService.sha256(png), "image/png", png.length, "staff-1", commandId, NOW);
            when(media.findByUploadedByAndCommandId("staff-1", commandId)).thenReturn(Optional.of(staged));
            when(storage.putIfAbsent(anyString(), any()))
                    .thenThrow(new CatalogueException(CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE));

            assertCode(() -> service.upload("staff-1", commandId, "image/png", png),
                    CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE);
            assertThat(staged.getState()).isEqualTo(CatalogueMediaObject.State.STAGED);
            verify(media, never()).saveAndFlush(any());
        }
    }

    @Nested
    class References {

        private CatalogueMediaRepository media;
        private MediaReferenceRepository references;
        private MediaReferenceService service;

        @BeforeEach
        void setUp() {
            media = mock(CatalogueMediaRepository.class);
            references = mock(MediaReferenceRepository.class);
            service = new MediaReferenceService(media, references);
            when(references.findByMediaObjectIdAndOwnerModuleAndOwnerRecordRef(any(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
        }

        @Test
        void attachRequiresReadyMediaOfTheSameUploaderAndLocksInAscendingOrder() {
            CatalogueMediaObject first = ready(UUID.fromString("00000000-0000-0000-0000-000000000001"), "staff-1");
            CatalogueMediaObject second = ready(UUID.fromString("00000000-0000-0000-0000-000000000002"), "staff-1");
            when(media.lockAllByIdIn(List.of(first.getId(), second.getId()))).thenReturn(List.of(first, second));

            service.attachOwnedMedia("staff-1", "productmanagement", "product:1", List.of(second.getId(), first.getId()), NOW);

            verify(references, times(2)).save(any(MediaReference.class));
            assertThat(first.getFirstAttachedAt()).isEqualTo(NOW);
        }

        @Test
        void anotherUsersOrDeletingMediaCannotBeAttached() {
            CatalogueMediaObject foreign = ready(UUID.randomUUID(), "staff-2");
            when(media.lockAllByIdIn(List.of(foreign.getId()))).thenReturn(List.of(foreign));
            assertCode(() -> service.attachOwnedMedia("staff-1", "productmanagement", "product:1",
                    List.of(foreign.getId()), NOW), CatalogueErrorCode.MEDIA_NOT_FOUND);

            CatalogueMediaObject deleting = ready(UUID.randomUUID(), "staff-1");
            deleting.requestDelete(NOW);
            when(media.lockAllByIdIn(List.of(deleting.getId()))).thenReturn(List.of(deleting));
            assertCode(() -> service.retain("readyringsales", "order:9", List.of(deleting.getId()), NOW),
                    CatalogueErrorCode.MEDIA_NOT_FOUND);
        }

        @Test
        void releasingTheLastReferenceStartsTheUnreferencedClockWithoutDeleting() {
            CatalogueMediaObject object = ready(UUID.randomUUID(), "staff-1");
            MediaReference reference = MediaReference.link(object.getId(), "productmanagement", "product:1", NOW);
            when(media.lockAllByIdIn(List.of(object.getId()))).thenReturn(List.of(object));
            when(references.findByMediaObjectIdAndOwnerModuleAndOwnerRecordRef(object.getId(), "productmanagement", "product:1"))
                    .thenReturn(Optional.of(reference));
            when(references.existsByMediaObjectIdAndReleasedAtIsNull(object.getId())).thenReturn(false);

            service.release("productmanagement", "product:1", List.of(object.getId()), NOW.plusSeconds(60));

            assertThat(reference.isActive()).isFalse();
            assertThat(object.getLastUnreferencedAt()).isEqualTo(NOW.plusSeconds(60));
            assertThat(object.getState()).isEqualTo(CatalogueMediaObject.State.READY);
        }

        private CatalogueMediaObject ready(UUID id, String uploader) {
            CatalogueMediaObject object = CatalogueMediaObject.stage(id, "test/" + id, "c", "image/png", 10, uploader,
                    UUID.randomUUID(), NOW);
            object.markReady("asset", "1", "png", NOW);
            return object;
        }
    }

    static byte[] image(String format) throws Exception {
        BufferedImage picture = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(picture, format, out);
        return out.toByteArray();
    }

    /** Minimal lossless WebP container: RIFF header, VP8L chunk with signature byte 0x2F. */
    static byte[] webpLossless() {
        byte[] payload = new byte[10];
        payload[0] = 0x2F;
        ByteBuffer buffer = ByteBuffer.allocate(12 + 8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(4 + 8 + payload.length)
                .put("WEBP".getBytes(StandardCharsets.US_ASCII))
                .put("VP8L".getBytes(StandardCharsets.US_ASCII)).putInt(payload.length).put(payload);
        return buffer.array();
    }

    static void assertCode(Runnable action, CatalogueErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CatalogueException.class, error -> assertThat(error.code()).isEqualTo(code));
    }

    static class NoopTransactions extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
