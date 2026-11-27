package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.BookingNotificationDeliveryPayload;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 003.1 FR-063: ticket QR images travel inside the buyer confirmation and the ticket recipient email. */
class TicketQrMailTest {

    private static final String QR_1 = "QR-0f8b3c2e-1111-4222-8333-944455556666";
    private static final String QR_2 = "QR-9a1d7e40-aaaa-4bbb-8ccc-0ddddeeeeeff";

    private static MailMessage compose(String kind, List<String> artifacts) {
        return BookingMailComposer.compose(kind, "GRP-1", new BookingNotificationDeliveryPayload(kind,
                "buyer@example.test", "Xin chào", artifacts, "", "corr", "MLAND – Vé"), "http://localhost:5173");
    }

    @Test
    void confirmationShowsOneImagePerTicketHoldingExactlyItsReference() throws Exception {
        MailMessage message = compose("GROUP_CONFIRMATION", List.of(
                "GRP-1-L0-T1 · Nến · 01/12/2026 09:00 · Còn lại khi checkout: 0đ · QR: " + QR_1,
                "GRP-1-L0-T2 · Nến · 01/12/2026 09:00 · Còn lại khi checkout: 0đ · QR: " + QR_2));

        assertThat(message.images()).hasSize(2);
        assertThat(decode(message.images().get(0).png())).isEqualTo(QR_1);
        assertThat(decode(message.images().get(1).png())).isEqualTo(QR_2);
        assertThat(message.html()).contains("cid:ticket-qr-1", "cid:ticket-qr-2");
        assertThat(message.text()).contains(QR_1, QR_2);
    }

    @Test
    void recipientEmailShowsTheTicketQrAndAnEmailWithoutTicketsHasNoImage() throws Exception {
        MailMessage recipient = compose("GROUP_TICKET_RECIPIENT", List.of("Mã vé: GRP-1-L0-T1", "QR: " + QR_1));
        assertThat(recipient.images()).hasSize(1);
        assertThat(decode(recipient.images().get(0).png())).isEqualTo(QR_1);

        assertThat(compose("GROUP_TICKET_RECIPIENT_REVOKED", List.of()).images()).isEmpty();
    }

    @Test
    void smtpAttachesEveryImageInlineUnderItsContentId() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        new SmtpMailAdapter(sender, "shop@example.test")
                .send("REF-1", compose("GROUP_TICKET_RECIPIENT", List.of("QR: " + QR_1)));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        sent.getValue().saveChanges();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        sent.getValue().writeTo(raw);
        assertThat(raw.toString(StandardCharsets.UTF_8))
                .contains("multipart/related", "Content-ID: <ticket-qr-1>", "Content-Type: image/png");
    }

    private static String decode(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)))).getText();
    }
}
