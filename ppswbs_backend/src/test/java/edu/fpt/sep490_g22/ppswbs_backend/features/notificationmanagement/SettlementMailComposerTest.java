package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.SettlementNotificationDeliveryPayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Settlement customer emails composed by Feature 007 (003.3 FR-040, template 7). */
class SettlementMailComposerTest {

    @Test
    void refundAccountRequest_usesRenderedSubject_andAnAbsolutePrivateLink() {
        SettlementNotificationDeliveryPayload payload = new SettlementNotificationDeliveryPayload(
                "REFUND_ACCOUNT_REQUEST", "payer@example.com", "MLAND sẽ hoàn 150.000 VND cho đơn GRP-1.",
                "corr", "MLAND – Cung cấp tài khoản để nhận hoàn 150.000 VND (đơn GRP-1)", "/refund-account#tok-1");

        MailMessage message = SettlementMailComposer.compose("REFUND-1", payload, "https://mland.vn/");

        assertThat(message.to()).isEqualTo("payer@example.com");
        assertThat(message.subject()).isEqualTo("MLAND – Cung cấp tài khoản để nhận hoàn 150.000 VND (đơn GRP-1)");
        assertThat(message.text()).contains("https://mland.vn/refund-account#tok-1").contains("đừng chia sẻ");
        assertThat(message.html()).contains("href=\"https://mland.vn/refund-account#tok-1\"")
                .contains("Nhập tài khoản nhận hoàn");
    }

    @Test
    void existingKind_withoutSubject_getsTheDefaultSubject_andNoLink() {
        SettlementNotificationDeliveryPayload payload = new SettlementNotificationDeliveryPayload(
                "REFUND_VERIFIED", "payer@example.com", "Mland đã hoàn 10.000 VND cho đơn BK-1.", "corr");

        MailMessage message = SettlementMailComposer.compose("REFUND-1", payload, "https://mland.vn");

        assertThat(message.subject()).isEqualTo("MLAND – Đã hoàn tiền");
        assertThat(message.html()).doesNotContain("href=");
    }

    @Test
    void unknownKind_isNotMailed() {
        assertThat(SettlementMailComposer.compose("X", new SettlementNotificationDeliveryPayload(
                "SOMETHING_ELSE", "payer@example.com", "body", "corr"), "https://mland.vn")).isNull();
    }
}
