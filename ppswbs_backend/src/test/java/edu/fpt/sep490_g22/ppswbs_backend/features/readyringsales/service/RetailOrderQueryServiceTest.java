package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailOrderRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.GroupedCheckout;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.OrderItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailOrder;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCataloguePort;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RetailOrderQueryServiceTest {
    @Test void deletedCatalogueItemStillShowsSavedPurchaseFacts() {
        var repository = mock(RetailOrderRepository.class);
        var catalogue = mock(RetailCataloguePort.class);
        var checkout = new GroupedCheckout(1L, "key", 0, BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.ZERO);
        var order = new RetailOrder(checkout, 5L, "PICKUP", BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.ZERO);
        order.addItem(new OrderItem(order, 100L, 2, BigDecimal.valueOf(100)));
        when(repository.findOwnedOrder(1L, "order")).thenReturn(Optional.of(order));
        when(catalogue.readCartProduct(100L)).thenReturn(new RetailCataloguePort.CartProductFact(null, null, null, null, null, null, false));
        var result = new RetailOrderQueryService(repository, catalogue).find(1L, "order");
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isNull();
            assertThat(item.lineTotal()).isEqualTo("200");
        });
    }

    @Test void catalogueFailureStillShowsStoredPurchaseFacts() {
        var repository = mock(RetailOrderRepository.class);
        var catalogue = mock(RetailCataloguePort.class);
        var checkout = new GroupedCheckout(1L, "key", 0, BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.ZERO);
        var order = new RetailOrder(checkout, 5L, "PICKUP", BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.ZERO);
        order.addItem(new OrderItem(order, 100L, 2, BigDecimal.valueOf(100)));
        when(repository.findOwnedOrder(1L, "order")).thenReturn(Optional.of(order));
        when(catalogue.readCartProduct(100L)).thenThrow(new RetailIntegrationUnavailableException("down"));
        var result = new RetailOrderQueryService(repository, catalogue).find(1L, "order");
        assertThat(result.payableAmount()).isEqualTo("200");
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isNull();
            assertThat(item.variantId()).isEqualTo(100L);
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.unitPrice()).isEqualTo("100");
        });
    }
}
