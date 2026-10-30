package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CartDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CartService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private RetailCartRepository cartRepository;

    @Mock
    private RetailCataloguePort cataloguePort;

    @InjectMocks
    private CartService cartService;

    @Test
    void addsEligibleVariantToNewMemberCartWithoutCheckoutEffects() {
        when(cataloguePort.isEligibleForCart(101L, 10L)).thenReturn(true);
        when(cartRepository.findByMemberAccountId(99L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(RetailCart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartDtos.RetailCartView result = cartService.addLine(99L, new CartDtos.AddCartLineRequest(101L, 10L, 2));

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().variantId()).isEqualTo(101L);
        assertThat(result.lines().getFirst().locationId()).isEqualTo(10L);
        assertThat(result.lines().getFirst().quantity()).isEqualTo(2);

        ArgumentCaptor<RetailCart> cartCaptor = ArgumentCaptor.forClass(RetailCart.class);
        verify(cartRepository).save(cartCaptor.capture());
        assertThat(cartCaptor.getValue().getMemberAccountId()).isEqualTo(99L);
    }

    @Test
    void rejectsAnIneligibleVariantBeforePersistingTheCart() {
        when(cataloguePort.isEligibleForCart(999999L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> cartService.addLine(99L, new CartDtos.AddCartLineRequest(999999L, 10L, 1)))
            .isInstanceOf(RetailConflictException.class);

        verify(cartRepository, never()).save(any(RetailCart.class));
    }

    @Test
    void rejectsQuantityAboveNinetyNineAcrossLocationsForTheSameVariant() {
        RetailCart existingCart = new RetailCart(99L);
        existingCart.addLine(101L, 10L, 70);
        when(cataloguePort.isEligibleForCart(101L, 20L)).thenReturn(true);
        when(cartRepository.findByMemberAccountId(99L)).thenReturn(Optional.of(existingCart));

        assertThatThrownBy(() -> cartService.addLine(99L, new CartDtos.AddCartLineRequest(101L, 20L, 30)))
            .isInstanceOf(RetailConflictException.class);
    }

    @Test
    void keepsTheOriginalLocationWhenTheSameVariantIsAddedForAnotherLocation() {
        RetailCart existingCart = new RetailCart(99L);
        existingCart.addLine(101L, 10L, 1);
        when(cataloguePort.isEligibleForCart(101L, 20L)).thenReturn(true);
        when(cartRepository.findByMemberAccountId(99L)).thenReturn(Optional.of(existingCart));
        when(cartRepository.save(existingCart)).thenReturn(existingCart);

        CartDtos.RetailCartView result = cartService.addLine(99L, new CartDtos.AddCartLineRequest(101L, 20L, 2));

        assertThat(result.lines())
            .extracting(CartDtos.CartLineView::locationId, CartDtos.CartLineView::quantity)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(10L, 1),
                org.assertj.core.groups.Tuple.tuple(20L, 2)
            );
    }

    @Test
    void rejectsTheFiftyFirstDistinctCartLine() {
        RetailCart existingCart = new RetailCart(99L);
        for (int index = 1; index <= 50; index++) {
            existingCart.addLine((long) index, 10L, 1);
        }
        when(cataloguePort.isEligibleForCart(51L, 10L)).thenReturn(true);
        when(cartRepository.findByMemberAccountId(99L)).thenReturn(Optional.of(existingCart));

        assertThatThrownBy(() -> cartService.addLine(99L, new CartDtos.AddCartLineRequest(51L, 10L, 1)))
            .isInstanceOf(RetailConflictException.class);

        verify(cartRepository, never()).save(any(RetailCart.class));
    }

    @Test
    void updatesOnlyTheRequestedLineInTheMembersOwnCart() {
        RetailCart existingCart = new RetailCart(99L);
        existingCart.addLine(101L, 10L, 1);
        ReflectionTestUtils.setField(existingCart.getLines().getFirst(), "id", "line-01");
        when(cartRepository.findByMemberAccountId(99L)).thenReturn(Optional.of(existingCart));
        when(cartRepository.save(existingCart)).thenReturn(existingCart);

        CartDtos.RetailCartView result = cartService.updateLine(99L, "line-01", new CartDtos.UpdateCartLineRequest(3));

        assertThat(result.lines()).singleElement().satisfies(line -> {
            assertThat(line.id()).isEqualTo("line-01");
            assertThat(line.quantity()).isEqualTo(3);
        });
        verify(cartRepository).save(existingCart);
    }

    @Test
    void doesNotAllowAnotherMemberToUpdateAnExistingCartLine() {
        RetailCart ownerCart = new RetailCart(99L);
        ownerCart.addLine(101L, 10L, 1);
        ReflectionTestUtils.setField(ownerCart.getLines().getFirst(), "id", "line-01");
        when(cartRepository.findByMemberAccountId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateLine(100L, "line-01", new CartDtos.UpdateCartLineRequest(2)))
            .isInstanceOf(RetailNotFoundException.class);

        assertThat(ownerCart.getLines().getFirst().getQuantity()).isEqualTo(1);
        verify(cartRepository, never()).save(any(RetailCart.class));
    }

    @Test
    void removesOnlyTheRequestedLineFromTheMembersOwnCart() {
        RetailCart existingCart = new RetailCart(99L);
        existingCart.addLine(101L, 10L, 1);
        existingCart.addLine(102L, 10L, 1);
        ReflectionTestUtils.setField(existingCart.getLines().getFirst(), "id", "line-01");
        ReflectionTestUtils.setField(existingCart.getLines().get(1), "id", "line-02");
        when(cartRepository.findByMemberAccountId(99L)).thenReturn(Optional.of(existingCart));

        cartService.removeLine(99L, "line-01");

        assertThat(existingCart.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getId()).isEqualTo("line-02");
            assertThat(line.getVariantId()).isEqualTo(102L);
        });
        verify(cartRepository).save(existingCart);
    }
}
