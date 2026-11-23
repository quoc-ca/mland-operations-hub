package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PriceProposalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.VariantBranchStockRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.VariantBranchStock;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocationPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T180 (FR-050, Session 2026-11-20): the old quantity command without a branch follows the same rule as the
 * per-branch one at a branch that is no longer active — a Manager changes it, a Staff member gets
 * {@code BRANCH_NOT_FOUND} and nothing is saved.
 */
class ProductBranchStockLegacyQuantityTests {

    private static final long ACTOR_ID = 9L;
    private static final long VARIANT_ID = 301L;
    private static final long CLOSED_BRANCH = 2L;

    private final AccountFacade accounts = mock(AccountFacade.class);
    private final VariantBranchStockRepository stocks = mock(VariantBranchStockRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private ProductBranchStockService service;
    private VariantBranchStock row;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SellingLocationPort> provider = mock(ObjectProvider.class);
        SellingLocationPort port = mock(SellingLocationPort.class);
        when(provider.getIfAvailable()).thenReturn(port);
        when(port.activeSellingLocations()).thenReturn(List.of(new SellingLocation(1L, "Chi nhánh 1", "Địa chỉ 1")));
        CatalogueBranchScope scope = new CatalogueBranchScope(accounts, stocks, variants,
                mock(PriceProposalRepository.class), provider);
        service = new ProductBranchStockService(stocks, variants, mock(CatalogueLockCoordinator.class),
                mock(CatalogueAvailabilityService.class), mock(CatalogueAuditWriter.class),
                mock(CatalogueCommandExecutor.class), scope, provider,
                mock(edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository.class),
                mock(PriceProposalRepository.class), mock(ProductReviewInvalidationService.class));
        row = VariantBranchStock.create(VARIANT_ID, CLOSED_BRANCH, BigInteger.valueOf(3));
        when(stocks.findByVariantIdInOrderByVariantIdAscLocationIdAsc(anyCollection())).thenReturn(List.of(row));
        variant = mock(ProductVariant.class);
        when(variant.getId()).thenReturn(VARIANT_ID);
    }

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aManagerBringsTheStockAtAClosedBranchToZero() {
        actAs(ActorRole.MANAGER, List.of());
        assertThat(service.legacyQuantityTouchesBranch(variant, BigInteger.ZERO)).isTrue();
        assertThat(row.getRemainingUnsold()).isEqualTo(BigInteger.ZERO);
        verify(stocks).saveAndFlush(row);
    }

    @Test
    void aStaffMemberCannotChangeTheStockAtAClosedBranch() {
        actAs(ActorRole.STAFF, List.of(CLOSED_BRANCH));
        assertThatThrownBy(() -> service.legacyQuantityTouchesBranch(variant, BigInteger.ZERO))
                .isInstanceOfSatisfying(CatalogueException.class,
                        e -> assertThat(e.code()).isEqualTo(CatalogueErrorCode.BRANCH_NOT_FOUND));
        assertThat(row.getRemainingUnsold()).isEqualTo(BigInteger.valueOf(3));
        verify(stocks, never()).saveAndFlush(any());
    }

    private void actAs(ActorRole role, List<Long> branches) {
        CurrentAccountActor actor = new CurrentAccountActor(ACTOR_ID, role, ActorStatus.ACTIVE, true, List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        when(accounts.getLocationScope(ACTOR_ID)).thenReturn(branches);
    }
}
