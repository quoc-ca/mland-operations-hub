package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundReceiptImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundReceiptImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt.SettlementReceiptException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt.SettlementReceiptStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 003.3 T154: upload, replay, attach, read and the 24 hour cleanup, against an in-memory store and storage. */
class RefundReceiptImageServiceTest {

    private static final String REFUND = "REFUND-1";
    private static final long REQUEST_ID = 7L;
    private static final Long MANAGER = 22L;
    private static final String KEY = "idempotency-key-0001";

    private SettlementRefundReceiptImageRepository images;
    private SettlementRefundRequestRepository requests;
    private BankRefundService bankRefunds;
    private FakeStorage storage;
    private RefundReceiptImageService service;
    private SettlementRefundRequest request;
    private final Map<String, SettlementRefundReceiptImage> rows = new HashMap<>();

    @BeforeEach
    void setUp() {
        images = mock(SettlementRefundReceiptImageRepository.class);
        requests = mock(SettlementRefundRequestRepository.class);
        bankRefunds = mock(BankRefundService.class);
        storage = new FakeStorage();
        service = new RefundReceiptImageService(images, requests, storage, bankRefunds);
        request = SettlementRefundRequest.builder().id(REQUEST_ID).requestReference(REFUND)
                .bookingReference("T-1").reason(RefundReason.EXCESS_OR_DUPLICATE_MONEY).sourceReference("src")
                .amountVnd(100_000L).status(RefundRequestStatus.PENDING_TRANSFER).build();
        when(requests.findByRequestReference(REFUND)).thenReturn(Optional.of(request));
        when(images.saveAndFlush(any())).thenAnswer(call -> {
            SettlementRefundReceiptImage row = call.getArgument(0);
            rows.put(row.getId(), row);
            return row;
        });
        when(images.findForUpdate(anyString())).thenAnswer(call -> Optional.ofNullable(rows.get(call.<String>getArgument(0))));
        when(images.findByUploadedByAccountIdAndUploadIdempotencyKey(any(), anyString())).thenAnswer(call ->
                rows.values().stream().filter(r -> r.getUploadedByAccountId().equals(call.getArgument(0))
                        && r.getUploadIdempotencyKey().equals(call.getArgument(1))).findFirst());
        when(images.findFirstByRefundRequestIdAndStateOrderByAttachedAtDesc(any(), anyString())).thenAnswer(call ->
                rows.values().stream().filter(r -> r.getRefundRequestId().equals(call.getArgument(0))
                        && r.getState().equals(call.getArgument(1))).findFirst());
        when(images.existsByRefundRequestIdAndAttachedBankTransferReferenceAndState(any(), anyString(), anyString()))
                .thenAnswer(call -> rows.values().stream().anyMatch(r -> r.getRefundRequestId().equals(call.getArgument(0))
                        && call.getArgument(1).equals(r.getAttachedBankTransferReference())
                        && r.getState().equals(call.getArgument(2))));
    }

    private static byte[] png() {
        try {
            return ReceiptImageContentInspectorTest.image("png");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void anUploadIsStoredAndOnlyMetadataIsKept() {
        var view = service.upload(REFUND, MANAGER, KEY, "image/png", png());

        assertThat(view.contentType()).isEqualTo("image/png");
        assertThat(view.byteCount()).isEqualTo(png().length);
        assertThat(view.checksum()).hasSize(64);
        var row = rows.get(view.receiptImageId());
        assertThat(row.getState()).isEqualTo(SettlementRefundReceiptImage.UPLOADED);
        assertThat(row.getRefundRequestId()).isEqualTo(REQUEST_ID);
        assertThat(storage.objects).containsKey(row.getStorageKey());
        assertThat(view.toString()).doesNotContain("cloudinary").doesNotContain("http");
    }

    @Test
    void anUploadOutsidePendingTransferIsRefusedAndStoresNothing() {
        for (RefundRequestStatus status : RefundRequestStatus.values()) {
            if (status == RefundRequestStatus.PENDING_TRANSFER) continue;
            request.setStatus(status);
            assertThatThrownBy(() -> service.upload(REFUND, MANAGER, "key-" + UUID.randomUUID(), "image/png", png()))
                    .as(status.name()).isInstanceOf(WorkshopSettlementConflictException.class)
                    .hasMessage("REFUND_NOT_AWAITING_TRANSFER");
        }
        assertThat(storage.objects).isEmpty();
        assertThat(rows).isEmpty();
    }

    @Test
    void anUnknownRequestIsNotFoundAndAShortKeyIsRefused() {
        assertThatThrownBy(() -> service.upload("NOPE", MANAGER, KEY, "image/png", png()))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
        assertThatThrownBy(() -> service.upload(REFUND, MANAGER, "short", "image/png", png()))
                .isInstanceOf(WorkshopSettlementValidationException.class).hasMessage("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void aRefusedFileNeverReachesStorage() {
        byte[] oversize = new byte[5_000_001];
        assertThatThrownBy(() -> service.upload(REFUND, MANAGER, KEY, "image/png", oversize))
                .isInstanceOfSatisfying(SettlementReceiptException.class, e -> assertThat(e.failure().httpStatus()).isEqualTo(413));
        assertThatThrownBy(() -> service.upload(REFUND, MANAGER, "key-pdf-0000000001", "application/pdf",
                "%PDF-1.7".getBytes()))
                .isInstanceOfSatisfying(SettlementReceiptException.class, e -> assertThat(e.failure().httpStatus()).isEqualTo(415));
        assertThat(storage.objects).isEmpty();
        assertThat(rows).isEmpty();
    }

    @Test
    void unavailableStorageIsA503AndRecordsNothing() {
        storage.unavailable = true;

        assertThatThrownBy(() -> service.upload(REFUND, MANAGER, KEY, "image/png", png()))
                .isInstanceOfSatisfying(SettlementReceiptException.class, e -> {
                    assertThat(e.failure().httpStatus()).isEqualTo(503);
                    assertThat(e.failure().code()).isEqualTo("RECEIPT_STORAGE_UNAVAILABLE");
                });
        assertThat(rows).isEmpty();
    }

    @Test
    void aReplayOfTheSameKeyAndBytesReturnsTheFirstResultAndOtherBytesAreAConflict() throws Exception {
        var first = service.upload(REFUND, MANAGER, KEY, "image/png", png());
        var replay = service.upload(REFUND, MANAGER, KEY, "image/png", png());

        assertThat(replay.receiptImageId()).isEqualTo(first.receiptImageId());
        assertThat(rows).hasSize(1);
        assertThat(storage.objects).hasSize(1);
        assertThatThrownBy(() -> service.upload(REFUND, MANAGER, KEY, "image/jpeg",
                ReceiptImageContentInspectorTest.image("jpg")))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void recordingTheTransferAttachesTheImageOfTheSameRequest() {
        var view = service.upload(REFUND, MANAGER, KEY, "image/png", png());
        transferRecorded("BANK-1");

        service.recordTransferWithImage(REFUND, MANAGER, "BANK-1", null, view.receiptImageId(), "corr");

        var row = rows.get(view.receiptImageId());
        assertThat(row.getState()).isEqualTo(SettlementRefundReceiptImage.ATTACHED);
        assertThat(row.getAttachedBankTransferReference()).isEqualTo("BANK-1");
        assertThat(row.getAttachedByAccountId()).isEqualTo(MANAGER);
        assertThat(service.read(REFUND).contentType()).isEqualTo("image/png");
    }

    @Test
    void aSecondImageForTheSameTransferAndAnAlreadyAttachedOneAreConflicts() {
        var first = service.upload(REFUND, MANAGER, KEY, "image/png", png());
        var second = service.upload(REFUND, MANAGER, "idempotency-key-0002", "image/png", png());
        transferRecorded("BANK-1");
        service.recordTransferWithImage(REFUND, MANAGER, "BANK-1", null, first.receiptImageId(), "corr");

        assertThatThrownBy(() -> service.recordTransferWithImage(REFUND, MANAGER, "BANK-1", null,
                second.receiptImageId(), "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("RECEIPT_IMAGE_ALREADY_ATTACHED");
        transferRecorded("BANK-2");
        assertThatThrownBy(() -> service.recordTransferWithImage(REFUND, MANAGER, "BANK-2", null,
                first.receiptImageId(), "corr"))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("RECEIPT_IMAGE_ALREADY_ATTACHED");
    }

    @Test
    void anUnknownOrForeignImageReferenceIsRefusedAndTheTransferRollsBackWithIt() {
        var view = service.upload(REFUND, MANAGER, KEY, "image/png", png());
        rows.get(view.receiptImageId()).setRefundRequestId(99L); // belongs to another request
        transferRecorded("BANK-1");

        assertThatThrownBy(() -> service.recordTransferWithImage(REFUND, MANAGER, "BANK-1", null,
                view.receiptImageId(), "corr")).isInstanceOf(SettlementUnprocessableException.class)
                .hasMessage("RECEIPT_IMAGE_REFERENCE_INVALID");
        assertThatThrownBy(() -> service.recordTransferWithImage(REFUND, MANAGER, "BANK-1", null,
                UUID.randomUUID().toString(), "corr")).isInstanceOf(SettlementUnprocessableException.class);
    }

    @Test
    void readIsNotFoundWithoutAnAttachedImageAndUnavailableWhenStorageIs() {
        assertThatThrownBy(() -> service.read(REFUND)).isInstanceOf(WorkshopSettlementNotFoundException.class);

        var view = service.upload(REFUND, MANAGER, KEY, "image/png", png());
        transferRecorded("BANK-1");
        service.recordTransferWithImage(REFUND, MANAGER, "BANK-1", null, view.receiptImageId(), "corr");
        storage.unavailable = true;

        assertThatThrownBy(() -> service.read(REFUND)).isInstanceOf(SettlementReceiptException.class);
    }

    @Test
    void theCleanupRemovesANeverAttachedUploadAfter24HoursAndNeverAnAttachedOne() {
        var stale = service.upload(REFUND, MANAGER, KEY, "image/png", png());
        var attached = service.upload(REFUND, MANAGER, "idempotency-key-0002", "image/png", png());
        var fresh = service.upload(REFUND, MANAGER, "idempotency-key-0003", "image/png", png());
        transferRecorded("BANK-1");
        service.recordTransferWithImage(REFUND, MANAGER, "BANK-1", null, attached.receiptImageId(), "corr");
        Instant now = Instant.now();
        rows.get(stale.receiptImageId()).setCreatedAt(now.minus(Duration.ofHours(25)));
        rows.get(attached.receiptImageId()).setCreatedAt(now.minus(Duration.ofDays(400)));
        rows.get(fresh.receiptImageId()).setCreatedAt(now.minus(Duration.ofHours(23)));
        when(images.findTop100ByStateAndCreatedAtBeforeOrderByCreatedAtAsc(anyString(), any())).thenAnswer(call ->
                rows.values().stream().filter(r -> r.getState().equals(call.getArgument(0))
                        && r.getCreatedAt().isBefore(call.getArgument(1))).toList());
        when(images.findTop100ByStateOrderByDeleteRequestedAtAsc(anyString())).thenAnswer(call ->
                new ArrayList<>(rows.values().stream().filter(r -> r.getState().equals(call.getArgument(0))).toList()));
        var cleanup = new RefundReceiptImageCleanupScheduler(images, service, storage);

        assertThat(cleanup.cleanUp(now)).isEqualTo(1);

        assertThat(rows.get(stale.receiptImageId()).getState()).isEqualTo(SettlementRefundReceiptImage.DELETED);
        assertThat(rows.get(stale.receiptImageId()).getDeletedAt()).isNotNull();
        assertThat(storage.objects).doesNotContainKey(rows.get(stale.receiptImageId()).getStorageKey());
        assertThat(rows.get(attached.receiptImageId()).getState()).isEqualTo(SettlementRefundReceiptImage.ATTACHED);
        assertThat(storage.objects).containsKey(rows.get(attached.receiptImageId()).getStorageKey());
        assertThat(rows.get(fresh.receiptImageId()).getState()).isEqualTo(SettlementRefundReceiptImage.UPLOADED);
    }

    @Test
    void aFailedDestroyLeavesTheRowPendingAndIsRetried() {
        var stale = service.upload(REFUND, MANAGER, KEY, "image/png", png());
        Instant now = Instant.now();
        rows.get(stale.receiptImageId()).setCreatedAt(now.minus(Duration.ofHours(30)));
        when(images.findTop100ByStateAndCreatedAtBeforeOrderByCreatedAtAsc(anyString(), any())).thenAnswer(call ->
                rows.values().stream().filter(r -> r.getState().equals(call.getArgument(0))
                        && r.getCreatedAt().isBefore(call.getArgument(1))).toList());
        when(images.findTop100ByStateOrderByDeleteRequestedAtAsc(anyString())).thenAnswer(call ->
                new ArrayList<>(rows.values().stream().filter(r -> r.getState().equals(call.getArgument(0))).toList()));
        var cleanup = new RefundReceiptImageCleanupScheduler(images, service, storage);
        storage.unavailable = true;

        assertThat(cleanup.cleanUp(now)).isZero();
        assertThat(rows.get(stale.receiptImageId()).getState()).isEqualTo(SettlementRefundReceiptImage.DELETE_PENDING);

        storage.unavailable = false;
        assertThat(cleanup.cleanUp(now.plusSeconds(60))).isEqualTo(1);
        assertThat(rows.get(stale.receiptImageId()).getState()).isEqualTo(SettlementRefundReceiptImage.DELETED);
    }

    private void transferRecorded(String bankReference) {
        when(bankRefunds.recordTransfer(eq(REFUND), any(), eq(bankReference), any(), anyString())).thenAnswer(call -> {
            request.setBankTransferReference(bankReference);
            request.setStatus(RefundRequestStatus.PENDING_RECONCILIATION);
            return request;
        });
    }

    private static final class FakeStorage implements SettlementReceiptStoragePort {
        final Map<String, byte[]> objects = new HashMap<>();
        boolean unavailable;

        @Override
        public String storageKeyFor(UUID imageId) {
            return "ppswbs/settlement-receipts/" + imageId;
        }

        @Override
        public StoredObject put(String storageKey, String contentType, byte[] bytes) {
            failIfUnavailable();
            objects.put(storageKey, bytes);
            return new StoredObject(storageKey, "asset-" + storageKey, "1", contentType.substring(6));
        }

        @Override
        public Optional<InputStream> open(StoredObject object) {
            failIfUnavailable();
            return Optional.ofNullable(objects.get(object.storageKey())).map(ByteArrayInputStream::new);
        }

        @Override
        public void deleteIfExists(String storageKey) {
            failIfUnavailable();
            objects.remove(storageKey);
        }

        private void failIfUnavailable() {
            if (unavailable) {
                throw new SettlementReceiptException(SettlementReceiptException.Failure.STORAGE_UNAVAILABLE);
            }
        }
    }
}
