package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt.ReceiptImageContentInspector;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt.SettlementReceiptException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 003.3 T154: the receipt content check equals 002's image rule: type, signature and header agree; 5,000,000 bytes at most. */
class ReceiptImageContentInspectorTest {

    @Test
    void aRealJpegPngAndWebpAreAcceptedUnderTheirDeclaredType() throws IOException {
        assertThat(ReceiptImageContentInspector.verify("image/jpeg", image("jpg"))).isEqualTo("image/jpeg");
        assertThat(ReceiptImageContentInspector.verify("image/png", image("png"))).isEqualTo("image/png");
        assertThat(ReceiptImageContentInspector.verify("image/webp", webp())).isEqualTo("image/webp");
    }

    @Test
    void aPdfIsRefused() {
        byte[] pdf = "%PDF-1.7\n1 0 obj<<>>endobj\n".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> ReceiptImageContentInspector.verify("application/pdf", pdf))
                .isInstanceOfSatisfying(SettlementReceiptException.class, e ->
                        assertThat(e.failure()).isEqualTo(SettlementReceiptException.Failure.TYPE_UNSUPPORTED));
    }

    @Test
    void aFileWhoseDeclaredTypeDoesNotMatchTheMagicBytesIsRefused() throws IOException {
        // A PNG sent as image/jpeg (for instance renamed to .jpg): the extension is never trusted.
        assertThatThrownBy(() -> ReceiptImageContentInspector.verify("image/jpeg", image("png")))
                .isInstanceOfSatisfying(SettlementReceiptException.class, e ->
                        assertThat(e.failure().code()).isEqualTo("RECEIPT_IMAGE_TYPE_UNSUPPORTED"));
    }

    @Test
    void aValidSignatureWithAnUnreadableHeaderIsRefused() {
        byte[] truncatedPng = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0};

        assertThatThrownBy(() -> ReceiptImageContentInspector.verify("image/png", truncatedPng))
                .isInstanceOfSatisfying(SettlementReceiptException.class, e ->
                        assertThat(e.failure()).isEqualTo(SettlementReceiptException.Failure.TYPE_UNSUPPORTED));
    }

    @Test
    void emptyBytesAreRefusedAsUnsupported() {
        assertThatThrownBy(() -> ReceiptImageContentInspector.verify("image/png", new byte[0]))
                .isInstanceOfSatisfying(SettlementReceiptException.class, e ->
                        assertThat(e.failure()).isEqualTo(SettlementReceiptException.Failure.TYPE_UNSUPPORTED));
    }

    @Test
    void fiveMillionBytesAreAcceptedAndOneMoreIsTooLarge() throws IOException {
        byte[] png = image("png");
        byte[] atLimit = Arrays.copyOf(png, 5_000_000);
        byte[] overLimit = Arrays.copyOf(png, 5_000_001);

        assertThat(ReceiptImageContentInspector.verify("image/png", atLimit)).isEqualTo("image/png");
        assertThatThrownBy(() -> ReceiptImageContentInspector.verify("image/png", overLimit))
                .isInstanceOfSatisfying(SettlementReceiptException.class, e -> {
                    assertThat(e.failure()).isEqualTo(SettlementReceiptException.Failure.TOO_LARGE);
                    assertThat(e.failure().httpStatus()).isEqualTo(413);
                });
    }

    static byte[] image(String format) throws IOException {
        BufferedImage picture = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(picture, format, out);
        return out.toByteArray();
    }

    /** A structurally valid WebP container with an extended (VP8X) header, as 002's inspector accepts. */
    static byte[] webp() {
        byte[] bytes = new byte[30];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        bytes[4] = 22; // RIFF payload size: "WEBP" + chunk header + 10 byte VP8X payload
        System.arraycopy("WEBPVP8X".getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 8);
        bytes[16] = 10; // VP8X payload length
        return bytes;
    }
}
