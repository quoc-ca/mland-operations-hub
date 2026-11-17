package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.PublicCatalogueDtos.PublicVariantBranch;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.VariantBranchStock;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.BranchVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.HeldSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PublicCatalogueProjection.SizeAvailability;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PublicCatalogueProjection.SizeLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T153 (FR-052, option C1 of Session 2026-11-14): the public {@code availableQuantity} per size and per branch.
 * Per branch = min(branch remaining − holds at the branch, size remaining − holds of the size), never below 0;
 * per size = size remaining − holds of the size, capped at the sum of the listed branches when the size is
 * assigned to branches; a size not on sale is "0". Stopped or inactive branches are not listed.
 */
class PublicCatalogueAvailabilityTests {

    private static final long BRANCH_A = 3;
    private static final long BRANCH_B = 9;
    private static final long BRANCH_INACTIVE = 12;

    private final Map<Long, BigInteger> heldBySize = new HashMap<>();
    private final Map<BranchVariant, BigInteger> heldByBranch = new HashMap<>();
    private final Map<Long, Map<Long, VariantBranchStock>> stocks = new HashMap<>();
    private ProductBranchStockService branchStock;
    private PublicCatalogueProjection projection;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        VariantHoldReader reader = mock(VariantHoldReader.class);
        when(reader.validHeldByVariant(anyCollection())).thenAnswer(call -> {
            Map<Long, BigInteger> result = new HashMap<>();
            for (Long id : (Collection<Long>) call.getArgument(0)) {
                result.put(id, heldBySize.getOrDefault(id, BigInteger.ZERO));
            }
            return result;
        });
        ObjectProvider<VariantHoldReader> readers = mock(ObjectProvider.class);
        when(readers.getIfAvailable()).thenReturn(reader);

        branchStock = mock(ProductBranchStockService.class);
        when(branchStock.activeBranches()).thenReturn(Map.of(
                BRANCH_A, new SellingLocation(BRANCH_A, "MLAND Quận 1", null),
                BRANCH_B, new SellingLocation(BRANCH_B, "MLAND Thủ Đức", null)));
        when(branchStock.stocksBySize(anyCollection())).thenAnswer(call -> {
            Map<Long, Map<Long, VariantBranchStock>> result = new HashMap<>();
            for (Long id : (Collection<Long>) call.getArgument(0)) {
                if (stocks.containsKey(id)) {
                    result.put(id, stocks.get(id));
                }
            }
            return result;
        });

        CatalogueAvailabilityService availability = mock(CatalogueAvailabilityService.class);
        when(availability.readBranchSnapshots(anyCollection(), any())).thenAnswer(call -> {
            Map<BranchVariant, HeldSnapshot> result = new HashMap<>();
            for (BranchVariant key : (Collection<BranchVariant>) call.getArgument(0)) {
                result.put(key, new HeldSnapshot(key.variantId(), heldByBranch.getOrDefault(key, BigInteger.ZERO), 0));
            }
            return result;
        });

        projection = new PublicCatalogueProjection(null, null, null, null, null, null, readers, branchStock, availability);
    }

    @Test
    void unassignedSizeShowsRemainingMinusHolds() {
        heldBySize.put(1L, BigInteger.TWO);
        SizeAvailability size = only(new SizeLine(1, true, BigInteger.valueOf(5)));
        assertThat(size.available()).isEqualTo(BigInteger.valueOf(3));
        assertThat(size.canBuy()).isTrue();
        assertThat(size.assignedToBranches()).isFalse();
        assertThat(size.branches()).isEmpty();
    }

    @Test
    void unassignedSizeWithMoreHoldsThanRemainingShowsZero() {
        heldBySize.put(1L, BigInteger.valueOf(7));
        SizeAvailability size = only(new SizeLine(1, true, BigInteger.valueOf(5)));
        assertThat(size.available()).isZero();
        assertThat(size.canBuy()).isFalse();
    }

    @Test
    void sizeNotOnSaleShowsZero() {
        SizeAvailability size = only(new SizeLine(1, false, BigInteger.valueOf(5)));
        assertThat(size.available()).isZero();
        assertThat(size.canBuy()).isFalse();
    }

    @Test
    void sizeSumsItsBranchesAndBranchHoldsLowerTheBranch() {
        heldBySize.put(1L, BigInteger.ONE);
        stock(1, BRANCH_A, 4);
        stock(1, BRANCH_B, 5);
        heldByBranch.put(new BranchVariant(1, BRANCH_A), BigInteger.ONE);
        SizeAvailability size = only(new SizeLine(1, true, BigInteger.valueOf(10)));
        assertThat(branches(size)).containsExactly(Map.entry("3", "3"), Map.entry("9", "5"));
        assertThat(size.available()).isEqualTo(BigInteger.valueOf(8));
        assertThat(size.canBuy()).isTrue();
        assertThat(size.assignedToBranches()).isTrue();
    }

    @Test
    void sizeTotalCapsBothTheBranchesAndTheirSum() {
        heldBySize.put(1L, BigInteger.TWO);
        stock(1, BRANCH_A, 6);
        stock(1, BRANCH_B, 3);
        SizeAvailability size = only(new SizeLine(1, true, BigInteger.valueOf(6)));
        // size left = 6 − 2 = 4: branch A min(6, 4) = 4, branch B min(3, 4) = 3, size min(4 + 3, 4) = 4
        assertThat(branches(size)).containsExactly(Map.entry("3", "4"), Map.entry("9", "3"));
        assertThat(size.available()).isEqualTo(BigInteger.valueOf(4));
    }

    @Test
    void branchWithMoreHoldsThanStockShowsZeroAndCannotBeBought() {
        stock(1, BRANCH_A, 2);
        heldByBranch.put(new BranchVariant(1, BRANCH_A), BigInteger.valueOf(3));
        SizeAvailability size = only(new SizeLine(1, true, BigInteger.valueOf(10)));
        assertThat(size.branches()).singleElement().satisfies(branch -> {
            assertThat(branch.availableQuantity()).isEqualTo("0");
            assertThat(branch.canBuy()).isFalse();
        });
        assertThat(size.available()).isZero();
        assertThat(size.canBuy()).isFalse();
    }

    @Test
    void stoppedAndInactiveBranchesAreNotListed() {
        stock(1, BRANCH_A, 2);
        stock(1, BRANCH_B, 4).setSaleEnabled(false);
        stock(1, BRANCH_INACTIVE, 7);
        SizeAvailability size = only(new SizeLine(1, true, BigInteger.valueOf(13)));
        assertThat(branches(size)).containsExactly(Map.entry("3", "2"));
        assertThat(size.available()).isEqualTo(BigInteger.TWO);
    }

    /** T156 (FR-052, decided 2026-11-14): the branch list failing does not fail the read; selling rows count as active. */
    @Test
    void branchSourceDownTreatsSellingRowsAsActive() {
        when(branchStock.activeBranches()).thenThrow(new CatalogueException(CatalogueErrorCode.LOCATION_DEPENDENCY_UNAVAILABLE));
        stock(1, BRANCH_A, 2);
        stock(1, BRANCH_B, 4).setSaleEnabled(false);
        stock(1, BRANCH_INACTIVE, 7);
        SizeAvailability size = only(new SizeLine(1, true, BigInteger.valueOf(13)));
        assertThat(branches(size)).containsExactly(Map.entry("3", "2"), Map.entry("12", "7"));
        assertThat(size.available()).isEqualTo(BigInteger.valueOf(9));
        assertThat(size.canBuy()).isTrue();
    }

    @Test
    void sizeNotOnSaleListsItsBranchesWithZero() {
        stock(1, BRANCH_A, 4);
        stock(1, BRANCH_B, 5);
        SizeAvailability size = only(new SizeLine(1, false, BigInteger.valueOf(9)));
        assertThat(branches(size)).containsExactly(Map.entry("3", "0"), Map.entry("9", "0"));
        assertThat(size.branches()).noneMatch(PublicVariantBranch::canBuy);
        assertThat(size.available()).isZero();
        assertThat(size.canBuy()).isFalse();
    }

    @Test
    void sizeAssignedOnlyToStoppedBranchesShowsZero() {
        stock(1, BRANCH_A, 4).setSaleEnabled(false);
        SizeAvailability size = only(new SizeLine(1, true, BigInteger.valueOf(4)));
        assertThat(size.assignedToBranches()).isTrue();
        assertThat(size.branches()).isEmpty();
        assertThat(size.available()).isZero();
        assertThat(size.canBuy()).isFalse();
    }

    @Test
    void canBuyMatchesAPositiveAvailableQuantity() {
        heldBySize.put(2L, BigInteger.valueOf(5));
        stock(3, BRANCH_A, 1);
        stock(4, BRANCH_B, 0);
        Map<Long, SizeAvailability> sizes = projection.availability(List.of(
                new SizeLine(1, true, BigInteger.ONE), new SizeLine(2, true, BigInteger.valueOf(5)),
                new SizeLine(3, true, BigInteger.ONE), new SizeLine(4, true, BigInteger.ONE)));
        sizes.values().forEach(size -> assertThat(size.canBuy()).isEqualTo(size.available().signum() > 0));
        assertThat(sizes.get(1L).canBuy()).isTrue();
        assertThat(sizes.get(2L).canBuy()).isFalse();
        assertThat(sizes.get(3L).canBuy()).isTrue();
        assertThat(sizes.get(4L).canBuy()).isFalse();
    }

    private VariantBranchStock stock(long variantId, long locationId, long quantity) {
        VariantBranchStock row = VariantBranchStock.create(variantId, locationId, BigInteger.valueOf(quantity));
        stocks.computeIfAbsent(variantId, id -> new LinkedHashMap<>()).put(locationId, row);
        return row;
    }

    private SizeAvailability only(SizeLine line) {
        return projection.availability(List.of(line)).get(line.id());
    }

    private static List<Map.Entry<String, String>> branches(SizeAvailability size) {
        return size.branches().stream().map(branch -> Map.entry(branch.locationId(), branch.availableQuantity())).toList();
    }
}
