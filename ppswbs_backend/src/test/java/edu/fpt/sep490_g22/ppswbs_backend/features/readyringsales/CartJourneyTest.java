package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailCartRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CartDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CartService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailNotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CartJourneyTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private RetailCartRepository cartRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private RetailCataloguePort cataloguePort;

    @BeforeEach
    void setUp() {
        cartRepository.deleteAll();
        accountRepository.deleteAll();
        when(cataloguePort.isEligibleForCart(anyLong(), anyLong())).thenReturn(true);
        org.mockito.Mockito.lenient().when(cataloguePort.readCartProduct(anyLong())).thenReturn(
            new edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort.CartProductFact("Ring", "SKU", 10, "100", "VND", "99", true));
    }

    @Test
    void persistsACartForAnExistingMember() {
        Long memberId = createMember().getId();

        CartDtos.RetailCartView cart = cartService.addLine(
            memberId,
            new CartDtos.AddCartLineRequest(101L, 10L, 2)
        );

        assertThat(cart.lines()).singleElement().satisfies(line -> {
            assertThat(line.variantId()).isEqualTo(101L);
            assertThat(line.locationId()).isEqualTo(10L);
            assertThat(line.quantity()).isEqualTo(2);
        });
        assertThat(cartRepository.findByMemberAccountId(memberId)).isPresent();
    }

    @Test
    void keepsExistingLocationAndRejectsEditsFromAnotherMembersCart() {
        Long ownerId = createMember().getId();
        Long otherMemberId = createMember().getId();
        cartService.addLine(ownerId, new CartDtos.AddCartLineRequest(101L, 10L, 1));
        cartService.addLine(ownerId, new CartDtos.AddCartLineRequest(101L, 20L, 2));

        RetailCart ownerCart = cartRepository.findByMemberAccountId(ownerId).orElseThrow();
        String ownerLineId = ownerCart.getLines().getFirst().getId();

        assertThat(ownerCart.getLines())
            .extracting(line -> line.getLocationId(), line -> line.getQuantity())
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(10L, 1),
                org.assertj.core.groups.Tuple.tuple(20L, 2)
            );
        assertThatThrownBy(() -> cartService.updateLine(
            otherMemberId,
            ownerLineId,
            new CartDtos.UpdateCartLineRequest(3)
        )).isInstanceOf(RetailNotFoundException.class);
    }

    @Test
    void enforcesMaximumLineAndVariantQuantityLimitsAgainstPersistedCartData() {
        Long memberId = createMember().getId();
        RetailCart fiftyLineCart = new RetailCart(memberId);
        for (int index = 1; index <= 50; index++) {
            fiftyLineCart.addLine((long) index, 10L, 1);
        }
        cartRepository.saveAndFlush(fiftyLineCart);

        assertThatThrownBy(() -> cartService.addLine(
            memberId,
            new CartDtos.AddCartLineRequest(51L, 10L, 1)
        )).isInstanceOf(RetailConflictException.class);

        Long secondMemberId = createMember().getId();
        cartService.addLine(secondMemberId, new CartDtos.AddCartLineRequest(99L, 10L, 99));

        assertThatThrownBy(() -> cartService.addLine(
            secondMemberId,
            new CartDtos.AddCartLineRequest(99L, 20L, 1)
        )).isInstanceOf(RetailConflictException.class);
    }

    @Test
    void rejectsAStaleCartWriteInsteadOfOverwritingANewerVersion() {
        Long memberId = createMember().getId();
        cartService.addLine(memberId, new CartDtos.AddCartLineRequest(101L, 10L, 1));

        RetailCart staleCopy = loadCartInSeparateTransaction(memberId);
        transactionTemplate.executeWithoutResult(status -> {
            RetailCart currentCart = cartRepository.findByMemberAccountId(memberId).orElseThrow();
            currentCart.addLine(102L, 10L, 1);
            cartRepository.saveAndFlush(currentCart);
        });

        staleCopy.addLine(103L, 10L, 1);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> cartRepository.saveAndFlush(staleCopy)))
            .isInstanceOfAny(OptimisticLockingFailureException.class, OptimisticLockException.class);
    }

    private RetailCart loadCartInSeparateTransaction(Long memberId) {
        return transactionTemplate.execute(status -> cartRepository.findByMemberAccountId(memberId).orElseThrow());
    }

    private Account createMember() {
        return accountRepository.saveAndFlush(Account.builder()
            .firebaseUid("cart-test-" + UUID.randomUUID())
            .loginEmail("cart-test-" + UUID.randomUUID() + "@example.test")
            .build());
    }
}
