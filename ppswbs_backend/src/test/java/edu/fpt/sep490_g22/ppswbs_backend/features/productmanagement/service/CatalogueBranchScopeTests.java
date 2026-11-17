package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.VariantBranchStockRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.VariantBranchStock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** FR-021/FR-048/FR-050: branch scope of size-wide sale status and of creating without a branch. */
class CatalogueBranchScopeTests {

    private static final long STAFF_ID = 7L;
    private static final long VARIANT_ID = 101L;

    private final AccountFacade accounts = mock(AccountFacade.class);
    private final VariantBranchStockRepository stocks = mock(VariantBranchStockRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final CatalogueBranchScope scope = new CatalogueBranchScope(accounts, stocks, variants,
            mock(PriceProposalRepository.class));

    @BeforeEach
    void variantExists() {
        when(variants.findProductIdById(VARIANT_ID)).thenReturn(Optional.of(42L));
    }

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void staffCannotStopASizeSoldAtABranchOutsideTheirScope() {
        actAs(ActorRole.STAFF, List.of(1L));
        soldAt(1L, 2L);
        assertThatThrownBy(() -> scope.requireEveryBranchOfVariant(Long.toString(VARIANT_ID)))
                .isInstanceOfSatisfying(CatalogueException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo(CatalogueErrorCode.BRANCH_OUT_OF_SCOPE));
    }

    @Test
    void staffMayStopASizeSoldOnlyAtTheirBranches() {
        actAs(ActorRole.STAFF, List.of(1L, 2L));
        soldAt(1L, 2L);
        assertThatCode(() -> scope.requireEveryBranchOfVariant(Long.toString(VARIANT_ID))).doesNotThrowAnyException();
    }

    @Test
    void managerMayStopASizeAnywhere() {
        actAs(ActorRole.MANAGER, List.of());
        soldAt(1L, 2L);
        assertThatCode(() -> scope.requireEveryBranchOfVariant(Long.toString(VARIANT_ID))).doesNotThrowAnyException();
    }

    @Test
    void staffWithoutBranchCannotTouchAnUnassignedSize() {
        actAs(ActorRole.STAFF, List.of());
        soldAt();
        assertThatThrownBy(() -> scope.requireEveryBranchOfVariant(Long.toString(VARIANT_ID)))
                .isInstanceOfSatisfying(CatalogueException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo(CatalogueErrorCode.BRANCH_SCOPE_EMPTY));
    }

    @Test
    void onlyAManagerOrAnInternalCallCreatesWithoutABranch() {
        assertThatCode(scope::requireNoBranchAllowed).doesNotThrowAnyException(); // internal call, no actor
        actAs(ActorRole.MANAGER, List.of());
        assertThatCode(scope::requireNoBranchAllowed).doesNotThrowAnyException();

        actAs(ActorRole.STAFF, List.of(1L));
        assertThatThrownBy(scope::requireNoBranchAllowed)
                .isInstanceOfSatisfying(CatalogueException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo(CatalogueErrorCode.VALIDATION_ERROR));

        actAs(ActorRole.STAFF, List.of());
        assertThatThrownBy(scope::requireNoBranchAllowed)
                .isInstanceOfSatisfying(CatalogueException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo(CatalogueErrorCode.BRANCH_SCOPE_EMPTY));
    }

    private void actAs(ActorRole role, List<Long> branches) {
        CurrentAccountActor actor = new CurrentAccountActor(STAFF_ID, role, ActorStatus.ACTIVE, true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        when(accounts.getLocationScope(STAFF_ID)).thenReturn(branches);
    }

    private void soldAt(Long... locationIds) {
        when(stocks.findByVariantIdInOrderByVariantIdAscLocationIdAsc(anyCollection())).thenReturn(
                java.util.Arrays.stream(locationIds)
                        .map(location -> VariantBranchStock.create(VARIANT_ID, location, BigInteger.ONE)).toList());
    }
}
