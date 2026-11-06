package edu.fpt.sep490_g22.ppswbs_backend.features.reporting;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ReportingMoney;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ReportSourceQuality;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ReportSourceQuality.SourceStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportingFoundationTest {
    @Test void distinguishesKnownEmptyPartialAndUnavailableSources() {
        assertThat(ReportSourceQuality.overall(List.of(COMPLETE, EMPTY))).isEqualTo(ReportSourceQuality.OverallStatus.COMPLETE);
        assertThat(ReportSourceQuality.overall(List.of(EMPTY, EMPTY))).isEqualTo(ReportSourceQuality.OverallStatus.EMPTY);
        assertThat(ReportSourceQuality.overall(List.of(COMPLETE, UNAVAILABLE))).isEqualTo(ReportSourceQuality.OverallStatus.PARTIAL);
        assertThat(ReportSourceQuality.overall(List.of(UNAVAILABLE, UNAVAILABLE))).isEqualTo(ReportSourceQuality.OverallStatus.UNAVAILABLE);
        assertThatThrownBy(() -> ReportSourceQuality.overall(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void preservesUnknownAmountsAndDoesNotOverflowLargeVndValues() {
        assertThat(ReportingMoney.sumVnd(new BigInteger("999999999999999999999"), BigInteger.ONE))
            .isEqualTo("1000000000000000000000");
        assertThat(ReportingMoney.sumVnd(BigInteger.ZERO, null)).isNull();
        assertThat(ReportingMoney.vnd(BigInteger.ZERO)).isEqualTo("0");
        assertThatThrownBy(() -> ReportingMoney.vnd(BigInteger.valueOf(-1))).isInstanceOf(IllegalArgumentException.class);
    }
}
