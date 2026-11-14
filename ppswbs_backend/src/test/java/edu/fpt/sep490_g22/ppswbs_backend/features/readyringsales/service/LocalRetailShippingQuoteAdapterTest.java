package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(adapter.requireValidQuote(9L, 20L, quote.quoteId(), List.of(
            new RetailCheckoutPorts.PricedLine(102L, 20L, 1, BigDecimal.valueOf(250_000)),
            new RetailCheckoutPorts.PricedLine(101L, 20L, 2, BigDecimal.valueOf(500_000))), recipient).quoteId()).isEqualTo(quote.quoteId());
    }

    @Test
    void quotesATwoLevelAddressWithoutDistrictAfterTheMerger() {
        GhtkQuoteProperties properties = enabledProperties();
        AtomicReference<GhtkFeeTransport.FeeRequest> captured = new AtomicReference<>();
        GhtkFeeTransport transport = request -> {
            captured.set(request);
            return new GhtkFeeTransport.FeeResponse(true, true, BigDecimal.valueOf(23_540), "");
        };
        LocalRetailShippingQuoteAdapter adapter = new LocalRetailShippingQuoteAdapter(properties, transport);

        var quote = adapter.quote(9L, 20L, List.of(new RetailCheckoutPorts.PricedLine(101L, 20L, 1, BigDecimal.valueOf(560_000))),
            new RetailCheckoutPorts.RecipientAddress("1 Xuan Thuy", "Ha Noi", null, "Phuong Cau Giay"));

        assertThat(quote.deliveryAvailable()).isTrue();
        assertThat(captured.get().district()).isNull();
        assertThat(captured.get().ward()).isEqualTo("Phuong Cau Giay");
    }

    @Test
    void retriesAnOldDistrictAddressAsTwoLevelWhenGhtkDoesNotMatchIt() {
        GhtkQuoteProperties properties = enabledProperties();
        java.util.List<GhtkFeeTransport.FeeRequest> calls = new java.util.ArrayList<>();
        GhtkFeeTransport transport = request -> {
            calls.add(request);
            return new GhtkFeeTransport.FeeResponse(true, request.district() == null, BigDecimal.valueOf(23_540), "");
        };
        LocalRetailShippingQuoteAdapter adapter = new LocalRetailShippingQuoteAdapter(properties, transport);

        var quote = adapter.quote(9L, 20L, List.of(new RetailCheckoutPorts.PricedLine(101L, 20L, 1, BigDecimal.valueOf(560_000))),
            new RetailCheckoutPorts.RecipientAddress("1 Xuan Thuy", "Ha Noi", "Quan Cau Giay", "Phuong Cau Giay"));

        assertThat(quote.deliveryAvailable()).isTrue();
        assertThat(calls).hasSize(2);
        assertThat(calls.get(1).district()).isNull();
        assertThat(calls.get(1).ward()).isEqualTo("Phuong Cau Giay");
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

    @Test
    void rejectsItemsFromAnotherBranchBeforeCallingTheProvider() {
        LocalRetailShippingQuoteAdapter adapter = new LocalRetailShippingQuoteAdapter(enabledProperties(),
            request -> { throw new AssertionError("Invalid branch must not call GHTK"); });
        assertThatThrownBy(() -> adapter.quote(9L, 20L,
            List.of(new RetailCheckoutPorts.PricedLine(101L, 21L, 1, BigDecimal.valueOf(500_000))),
            new RetailCheckoutPorts.RecipientAddress("1 Street", "Ha Noi", "District", "Ward")))
            .isInstanceOf(RetailConflictException.class);
    }

    @Test
    void rejectsNullQuoteAndOtherMembersOrBranches() {
        LocalRetailShippingQuoteAdapter adapter = new LocalRetailShippingQuoteAdapter(new GhtkQuoteProperties(),
            request -> { throw new AssertionError("Disabled provider must not be called"); });
        var quote = adapter.quote(9L, 20L,
            List.of(new RetailCheckoutPorts.PricedLine(101L, 20L, 1, BigDecimal.TEN)), null);
        var lines = List.of(new RetailCheckoutPorts.PricedLine(101L, 20L, 1, BigDecimal.TEN));
        assertThatThrownBy(() -> adapter.requireValidQuote(9L, 20L, null, lines, null)).isInstanceOf(RetailConflictException.class);
        assertThatThrownBy(() -> adapter.requireValidQuote(10L, 20L, quote.quoteId(), lines, null)).isInstanceOf(RetailConflictException.class);
        assertThatThrownBy(() -> adapter.requireValidQuote(9L, 21L, quote.quoteId(), lines, null)).isInstanceOf(RetailConflictException.class);
        assertThatThrownBy(() -> adapter.requireValidQuote(9L, 20L, quote.quoteId(),
            List.of(new RetailCheckoutPorts.PricedLine(101L, 20L, 2, BigDecimal.TEN)), null)).isInstanceOf(RetailConflictException.class);
        assertThatThrownBy(() -> adapter.requireValidQuote(9L, 20L, quote.quoteId(),
            List.of(new RetailCheckoutPorts.PricedLine(101L, 20L, 1, BigDecimal.valueOf(11))), null)).isInstanceOf(RetailConflictException.class);
    }

    @Test
    void rejectsQuoteWhenRecipientAddressChanges() {
        var adapter = new LocalRetailShippingQuoteAdapter(enabledProperties(), request ->
            new GhtkFeeTransport.FeeResponse(true, true, BigDecimal.valueOf(30000), ""));
        var lines = List.of(new RetailCheckoutPorts.PricedLine(101L, 20L, 1, BigDecimal.TEN));
        var address = new RetailCheckoutPorts.RecipientAddress("1 Street", "Ha Noi", "District", "Ward");
        var changed = new RetailCheckoutPorts.RecipientAddress("2 Street", "Ha Noi", "District", "Ward");
        var quote = adapter.quote(9L, 20L, lines, address);
        assertThat(adapter.requireValidQuote(9L, 20L, quote.quoteId(), lines, address)).isEqualTo(quote);
        assertThatThrownBy(() -> adapter.requireValidQuote(9L, 20L, quote.quoteId(), lines, changed))
            .isInstanceOf(RetailConflictException.class);
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
