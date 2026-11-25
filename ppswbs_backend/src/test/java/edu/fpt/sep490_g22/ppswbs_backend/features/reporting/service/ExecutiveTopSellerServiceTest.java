package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTopSellerPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTopSellerPort.PackageSales;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTopSellerPort.ProductSales;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutiveTopSellerServiceTest {
    // 2026-11-14 10:00 in Vietnam.
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-14T03:00:00Z"), ZoneOffset.UTC);
    private static final Instant NOV_1 = Instant.parse("2026-10-31T17:00:00Z");
    private static final Instant NOW_INSTANT = Instant.parse("2026-11-14T03:00:00Z");
    private final ReportingTopSellerPort sources = mock(ReportingTopSellerPort.class);
    private final ExecutiveTopSellerService service = new ExecutiveTopSellerService(sources, new ReportingPeriodResolver(NOW), NOW);

    @Test void readsTheTopFiveOfTheMonthUpToNowForTheGivenBranches() {
        when(sources.topProducts(any(), any(), any(), anyInt())).thenReturn(List.of(
            new ProductSales(7, "Nhẫn bạc Moon", 12, BigInteger.valueOf(6_000_000)),
            new ProductSales(3, "Nhẫn vàng Sun", 4, BigInteger.valueOf(8_000_000))));
        when(sources.topPackages(any(), any(), any(), anyInt())).thenReturn(List.of(
            new PackageSales(2, "Signature Workshop - Silver 925", 9, BigInteger.valueOf(13_500_000))));

        var result = service.topSellers(null, null, List.of(1L, 2L));

        verify(sources).topProducts(List.of(1L, 2L), NOV_1, NOW_INSTANT, 5);
        verify(sources).topPackages(List.of(1L, 2L), NOV_1, NOW_INSTANT, 5);
        assertThat(result.products()).extracting("id", "name", "quantity", "revenue").containsExactly(
            org.assertj.core.groups.Tuple.tuple(7L, "Nhẫn bạc Moon", 12L, "6000000"),
            org.assertj.core.groups.Tuple.tuple(3L, "Nhẫn vàng Sun", 4L, "8000000"));
        assertThat(result.packages()).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("Signature Workshop - Silver 925");
            assertThat(p.quantity()).isEqualTo(9);
            assertThat(p.revenue()).isEqualTo("13500000");
        });
        assertThat(result.unavailable()).isEmpty();
        assertThat(result.scope().periodType()).isEqualTo("MONTH");
        assertThat(result.scope().locationIds()).containsExactly(1L, 2L);
    }

    @Test void aFailingSourceIsNullAndListedWithoutHidingTheOther() {
        when(sources.topProducts(any(), any(), any(), anyInt())).thenThrow(new IllegalStateException("db down"));
        when(sources.topPackages(any(), any(), any(), anyInt())).thenReturn(List.of());

        var result = service.topSellers("MONTH", null, List.of(1L));

        assertThat(result.products()).isNull();
        assertThat(result.packages()).isEmpty();
        assertThat(result.unavailable()).containsExactly("TOP_PRODUCTS");
    }

    @Test void anInvalidPeriodIsRejectedBeforeAnySourceIsRead() {
        assertThatThrownBy(() -> service.topSellers("DECADE", null, List.of(1L)))
            .isInstanceOf(ReportingException.class);
        verifyNoInteractions(sources);
    }

    @Test void aChosenDayReadsExactlyThatDay() {
        when(sources.topProducts(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(sources.topPackages(any(), any(), any(), anyInt())).thenReturn(List.of());

        service.topSellers("DAY", java.time.LocalDate.of(2026, 11, 3), List.of(1L));

        verify(sources).topProducts(List.of(1L), Instant.parse("2026-11-02T17:00:00Z"), Instant.parse("2026-11-03T17:00:00Z"), 5);
        verify(sources).topPackages(eq(List.of(1L)), eq(Instant.parse("2026-11-02T17:00:00Z")), eq(Instant.parse("2026-11-03T17:00:00Z")), eq(5));
    }
}
