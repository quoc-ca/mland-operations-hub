package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundReceiptImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundReceiptImage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.BankRefundService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RefundAccountCipher;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RefundReceiptImageCleanupScheduler;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RefundReceiptImageService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt.SettlementReceiptStoragePort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * 003.3 T154 on MySQL: the Flyway-built {@code settlement_refund_receipt_images} table, the transfer and the image
 * attached in one transaction, and the 24 hour cleanup that never removes an attached image, also after a return to
 * the pending-account state and after {@code REFUNDED}. The Cloudinary port is replaced by an in-memory store.
 */
@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {
        "vnpay.merchant-api-ip-address=127.0.0.1",
        "vnpay.merchant-api-created-by=it-merchant"})
class RefundReceiptImageMysqlIT {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final String ACCOUNT = "12345678901";
    private static final Long MANAGER = 22L;

    @MockitoBean PaymentGatewayCheckoutPort checkout;
    @MockitoBean VnpayMerchantOperationsPort merchantOperations;
    @MockitoBean WorkshopOperationsFacade operationsFacade;
    @MockitoBean LoyaltyFacade loyalty;
    @MockitoBean WorkshopBookingFacade bookingFacade;
    @MockitoBean edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade bookingNotifications;
    @MockitoBean SettlementReceiptStoragePort storage;

    @Autowired SettlementFinalBillRepository bills;
    @Autowired SettlementRefundRequestRepository refunds;
    @Autowired SettlementRefundReceiptImageRepository images;
    @Autowired BankRefundService bankRefunds;
    @Autowired RefundReceiptImageService receiptImages;
    @Autowired RefundReceiptImageCleanupScheduler cleanup;

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @BeforeAll
    static void refundAccountKey() {
        ReflectionTestUtils.invokeMethod(RefundAccountCipher.class, "useKeyForTests",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }

    @BeforeEach
    void inMemoryStorage() {
        objects.clear();
        doReturn("payer@example.com").when(bookingFacade).getSettlementNotificationRecipient(any());
        when(storage.storageKeyFor(any())).thenAnswer(call -> "ppswbs/settlement-receipts/" + call.getArgument(0));
        when(storage.put(anyString(), anyString(), any())).thenAnswer(call -> {
            objects.put(call.getArgument(0), call.getArgument(2));
            return new SettlementReceiptStoragePort.StoredObject(call.getArgument(0), "asset-1", "1", "png");
        });
        when(storage.open(any())).thenAnswer(call -> Optional.ofNullable(
                objects.get(((SettlementReceiptStoragePort.StoredObject) call.getArgument(0)).storageKey()))
                .map(ByteArrayInputStream::new));
        doAnswer(call -> {
            objects.remove((String) call.getArgument(0));
            return null;
        }).when(storage).deleteIfExists(anyString());
    }

    private String refundAwaitingTransfer() {
        String ticket = "T-RCPT-" + SEQ.incrementAndGet();
        bills.save(SettlementFinalBill.builder().billReference("BILL-" + ticket).bookingReference(ticket)
                .groupReference("G-RCPT").postPromotionAmountVnd(500_000L).acceptedDepositAmountVnd(500_000L)
                .remainingAmountVnd(0L).currencyCode("VND").lifecycleStatus("OPEN").build());
        var opened = bankRefunds.open(ticket, "G-RCPT", RefundReason.EXCESS_OR_DUPLICATE_MONEY, "src-" + ticket,
                100_000, "CORR");
        String reference = opened.request().getRequestReference();
        bankRefunds.submitAccountByLink(opened.rawAccountLinkToken(), "it-link-key-" + UUID.randomUUID(), "BIDV",
                ACCOUNT, "NGUYEN VAN A", true, "CORR");
        bankRefunds.approve(reference, MANAGER, "ok", "CORR");
        return reference;
    }

    private static byte[] png() throws java.io.IOException {
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB),
                "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("T154: the image attaches with the transfer in one transaction, is read back, and survives cleanup, a return and REFUNDED")
    void attachedImageIsKeptThroughReturnAndRefunded() throws Exception {
        String reference = refundAwaitingTransfer();
        var kept = receiptImages.upload(reference, MANAGER, "it-upload-key-" + UUID.randomUUID(), "image/png", png());
        var abandoned = receiptImages.upload(reference, MANAGER, "it-upload-key-" + UUID.randomUUID(), "image/png", png());
        assertThat(images.findById(kept.receiptImageId())).get()
                .extracting(SettlementRefundReceiptImage::getState).isEqualTo(SettlementRefundReceiptImage.UPLOADED);

        receiptImages.recordTransferWithImage(reference, 33L, "FT-" + SEQ.incrementAndGet(), null, kept.receiptImageId(), "CORR");

        var attached = images.findById(kept.receiptImageId()).orElseThrow();
        assertThat(attached.getState()).isEqualTo(SettlementRefundReceiptImage.ATTACHED);
        assertThat(attached.getAttachedBankTransferReference()).startsWith("FT-");
        assertThat(refunds.findByRequestReference(reference).orElseThrow().getStatus())
                .isEqualTo(RefundRequestStatus.PENDING_RECONCILIATION);
        assertThat(receiptImages.read(reference).contentType()).isEqualTo("image/png");
        assertThat(receiptImages.requestIdsWithAttachedImage(
                java.util.List.of(refunds.findByRequestReference(reference).orElseThrow().getId())))
                .hasSize(1);

        // 25 hours later only the never-attached upload goes; 400 days later the attached one is still there.
        assertThat(cleanup.cleanUp(Instant.now().plus(Duration.ofHours(25)))).isGreaterThanOrEqualTo(1);
        assertThat(images.findById(abandoned.receiptImageId()).orElseThrow().getState())
                .isEqualTo(SettlementRefundReceiptImage.DELETED);
        assertThat(objects).hasSize(1);
        cleanup.cleanUp(Instant.now().plus(Duration.ofDays(400)));
        assertThat(images.findById(kept.receiptImageId()).orElseThrow().getState())
                .isEqualTo(SettlementRefundReceiptImage.ATTACHED);

        // A bank return: the request goes back to PENDING_ACCOUNT and the image stays with the refund record.
        bankRefunds.returnToPendingAccount(reference, MANAGER, "Ngan hang tra lai", "CORR");
        cleanup.cleanUp(Instant.now().plus(Duration.ofDays(400)));
        assertThat(images.findById(kept.receiptImageId()).orElseThrow().getState())
                .isEqualTo(SettlementRefundReceiptImage.ATTACHED);
        assertThat(receiptImages.read(reference).bytes()).isNotEmpty();
    }

    @Test
    @DisplayName("T154: the transfer is recorded without an image; a second image or a foreign image is refused and rolls the transfer back")
    void transferWithoutImageAndRefusedAttachments() throws Exception {
        String reference = refundAwaitingTransfer();
        var first = receiptImages.upload(reference, MANAGER, "it-upload-key-" + UUID.randomUUID(), "image/png", png());
        var second = receiptImages.upload(reference, MANAGER, "it-upload-key-" + UUID.randomUUID(), "image/png", png());

        assertThatThrownBy(() -> receiptImages.recordTransferWithImage(reference, 33L, "FT-X", null,
                UUID.randomUUID().toString(), "CORR")).isInstanceOf(RuntimeException.class);
        // The failed attach rolled the transfer back: the request still waits for it.
        assertThat(refunds.findByRequestReference(reference).orElseThrow().getStatus())
                .isEqualTo(RefundRequestStatus.PENDING_TRANSFER);

        receiptImages.recordTransferWithImage(reference, 33L, "FT-Y", null, first.receiptImageId(), "CORR");
        assertThatThrownBy(() -> receiptImages.recordTransferWithImage(reference, 33L, "FT-Y", null,
                second.receiptImageId(), "CORR")).isInstanceOf(WorkshopSettlementConflictException.class);

        String plain = refundAwaitingTransfer();
        bankRefunds.recordTransfer(plain, 33L, "FT-" + SEQ.incrementAndGet(), null, "CORR");
        assertThat(refunds.findByRequestReference(plain).orElseThrow().getStatus())
                .isEqualTo(RefundRequestStatus.PENDING_RECONCILIATION);
    }

    @Test
    @DisplayName("T154: a replay of the upload with the same key returns the first image; REFUNDED keeps the attached image")
    void replayAndRefunded() throws Exception {
        String reference = refundAwaitingTransfer();
        String key = "it-upload-key-" + UUID.randomUUID();
        var first = receiptImages.upload(reference, MANAGER, key, "image/png", png());
        var replay = receiptImages.upload(reference, MANAGER, key, "image/png", png());
        assertThat(replay.receiptImageId()).isEqualTo(first.receiptImageId());
        assertThat(images.findAll().stream().filter(i -> key.equals(i.getUploadIdempotencyKey()))).hasSize(1);

        receiptImages.recordTransferWithImage(reference, 33L, "FT-" + SEQ.incrementAndGet(), null, first.receiptImageId(), "CORR");
        bankRefunds.confirmRefunded(reference, MANAGER, "CORR");
        cleanup.cleanUp(Instant.now().plus(Duration.ofDays(400)));

        assertThat(images.findById(first.receiptImageId()).orElseThrow().getState())
                .isEqualTo(SettlementRefundReceiptImage.ATTACHED);
        assertThat(receiptImages.read(reference).bytes()).isNotEmpty();
    }
}
