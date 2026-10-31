package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtaReconciliationCorrelationTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void newMappedItemGetsPersistedCorrelationWhenNoneWasSupplied(String missing) {
        var item = mappedItem(missing);
        item.onCreate();
        String correlation = item.getAuditReference();
        assertThat(UUID.fromString(correlation).toString()).isEqualTo(correlation);
        assertThat(correlation).isNotEqualTo(item.getPublicReference());
        item.onUpdate();
        assertThat(item.getAuditReference()).isEqualTo(correlation);
    }

    @Test
    void preservesAnExistingCorrelationAcrossPersistenceCallbacks() {
        String correlation = UUID.randomUUID().toString();
        var item = mappedItem(correlation);
        item.onCreate();
        item.onUpdate();
        assertThat(item.getAuditReference()).isEqualTo(correlation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1", "12345678-1234-1234-1234-123456789abz"})
    void rejectsMalformedCorrelationInsteadOfSilentlyDiscardingEvidence(String malformed) {
        var item = mappedItem(malformed);
        assertThatThrownBy(item::onCreate).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(item::onUpdate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotRemoveMappedCorrelationOnUpdate() {
        var item = mappedItem(UUID.randomUUID().toString());
        item.onCreate();
        item.setAuditReference(null);
        assertThatThrownBy(item::onUpdate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mappingIndependentItemsKeepTheirExistingNullableAuditContract() {
        var item = OtaReconciliationWorkItem.builder().build();
        item.onCreate();
        item.onUpdate();
        assertThat(item.getAuditReference()).isNull();
    }

    private static OtaReconciliationWorkItem mappedItem(String audit) {
        return OtaReconciliationWorkItem.builder().mapping(OtaChannelMapping.builder().id(1L).build())
                .auditReference(audit).build();
    }
}
