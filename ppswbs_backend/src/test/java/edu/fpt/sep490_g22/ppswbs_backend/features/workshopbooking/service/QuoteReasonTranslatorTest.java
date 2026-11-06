package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.QuoteReasonDetail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteReasonTranslatorTest {
    @Test
    void translatesKnownReasonsToVietnameseWithCodes() {
        List<QuoteReasonDetail> details = QuoteReasonTranslator.translate(List.of(
                "The voucher code was not found.", "Points must be between 100 and 500."));
        assertThat(details).containsExactly(
                new QuoteReasonDetail("VOUCHER_NOT_FOUND", "Không tìm thấy mã voucher."),
                new QuoteReasonDetail("POINTS_OUT_OF_RANGE", "Số điểm phải từ 100 đến 500."));
    }

    @Test
    void keepsUnknownReasonsUnderOther() {
        assertThat(QuoteReasonTranslator.translate(List.of("Something new.")))
                .containsExactly(new QuoteReasonDetail("OTHER", "Something new."));
    }
}
