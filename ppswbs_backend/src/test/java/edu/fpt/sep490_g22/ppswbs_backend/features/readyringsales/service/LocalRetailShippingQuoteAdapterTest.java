package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRetailShippingQuoteAdapterTest {

    @Test
    void quotesTheProviderFeeUsingFiftyGramsPerRingAndStoresTheServerIssuedQuote() {
        GhtkQuoteProperties properties = enabledProperties();
        AtomicReference<GhtkFeeTransport.FeeRequest> captured = new AtomicReference<>();
        GhtkFeeTransport transport = request -> {
            captured.set(request);
            return new GhtkFeeTransport.FeeResponse(true, true, BigDecimal.valueOf(30_450), "");
        };
        LocalRetailShippingQuoteAdapter adapter = new LocalRetailShippingQuoteAdapter(properties, transport);
        var recipient = new RetailCheckoutPorts.RecipientAddress("1 Le Duc Tho", "Ha Noi", "Quan Cau Giay", "Phuong Mai Dich");

        var quote = adapter.quote(9L, 20L, List.of(
            new RetailCheckoutPorts.PricedLine(101L, 20L, 2, BigDecimal.valueOf(500_000)),
            new RetailCheckoutPorts.PricedLine(102L, 20L, 1, BigDecimal.valueOf(250_000))), recipient);

        assertThat(captured.get().weightGrams()).isEqualTo(150);
        assertThat(captured.get().declaredValue()).isEqualByComparingTo("1250000");
        assertThat(quote.charge()).isEqualByComparingTo("30450");
        assertThat(quote.deliveryAvailable()).isTrue();
        assertThat(adapter.requireValidQuote(9L, 20L, quote.quoteId()).quoteId()).isEqualTo(quote.quoteId());
    }

    @Test
    void returnsAnUnavailableDeliveryChoiceWithoutCreatingAQuoteWhenGhtkDoesNotServeTheAddress() {
        GhtkQuoteProperties properties = enabledProperties();
        GhtkFeeTransport transport = request -> new GhtkFeeTransport.FeeResponse(true, false, BigDecimal.valueOf(39_250), "");
        LocalRetailShippingQuoteAdapter adapter = new LocalRetailShippingQuoteAdapter(properties, transport);

        var quote = adapter.quote(9L, 20L, List.of(new RetailCheckoutPorts.PricedLine(101L, 20L, 1, BigDecimal.valueOf(500_000))),
            new RetailCheckoutPorts.RecipientAddress("Nha so 3", "Ha Noi", "Huyen Thach That", "Xa Ha Bang"));

        assertThat(quote.deliveryAvailable()).isFalse();
        assertThat(quote.quoteId()).isNull();
        assertThat(quote.charge()).isZero();
    }

    @Test
    void retainsTheDeterministicLocalQuoteWhenTheRealProviderIsDisabled() {
        GhtkQuoteProperties properties = new GhtkQuoteProperties();
        LocalRetailShippingQuoteAdapter adapter = new LocalRetailShippingQuoteAdapter(properties,
            request -> { throw new AssertionError("Disabled GHTK must not make an HTTP quote"); });

        var quote = adapter.quote(9L, 20L, List.of(new RetailCheckoutPorts.PricedLine(101L, 20L, 1, BigDecimal.valueOf(500_000))), null);

        assertThat(quote.quoteId()).startsWith("local-ghtk-");
        assertThat(quote.deliveryAvailable()).isTrue();
    }

    private GhtkQuoteProperties enabledProperties() {
        GhtkQuoteProperties properties = new GhtkQuoteProperties();
        properties.setEnabled(true);
        properties.setToken("test-token");
        properties.setPickupAddress("83 Ma May");
        properties.setPickupProvince("Ha Noi");
        properties.setPickupDistrict("Quan Hoan Kiem");
        properties.setPickupWard("Phuong Hoan Kiem");
        return properties;
    }
}
