package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailDiscountAllocation;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetailDiscountAllocationTest {
    private static List<BigInteger> amounts(long... values) {
        return java.util.Arrays.stream(values).mapToObj(BigInteger::valueOf).toList();
    }

    @Test
    void splitsInProportionToEachBranchAmount() {
        assertThat(RetailDiscountAllocation.split(BigInteger.valueOf(85_000), amounts(120_000, 250_000, 480_000),
            amounts(120_000, 250_000, 480_000))).containsExactlyElementsOf(amounts(12_000, 25_000, 48_000));
    }

    @Test
    void roundingRemainderGoesToTheLargestBranchAndSharesAddUpToTheTotal() {
        var shares = RetailDiscountAllocation.split(BigInteger.valueOf(100), amounts(1, 1, 1), amounts(100, 100, 100));
        assertThat(shares.stream().reduce(BigInteger.ZERO, BigInteger::add)).isEqualTo(BigInteger.valueOf(100));
        assertThat(shares).allSatisfy(share -> assertThat(share).isBetween(BigInteger.valueOf(33), BigInteger.valueOf(34)));
    }

    @Test
    void voucherShareStaysOnTheBranchesHoldingItsLines() {
        assertThat(RetailDiscountAllocation.split(BigInteger.valueOf(50_000), amounts(0, 300_000),
            amounts(200_000, 300_000))).containsExactlyElementsOf(amounts(0, 50_000));
    }

    @Test
    void neverGivesABranchMoreThanItsCap() {
        var shares = RetailDiscountAllocation.split(BigInteger.valueOf(150), amounts(1, 100), amounts(100, 50));
        assertThat(shares).containsExactlyElementsOf(amounts(100, 50));
        assertThatThrownBy(() -> RetailDiscountAllocation.split(BigInteger.valueOf(200), amounts(1, 1), amounts(50, 50)))
            .isInstanceOf(RetailConflictException.class);
    }
}
