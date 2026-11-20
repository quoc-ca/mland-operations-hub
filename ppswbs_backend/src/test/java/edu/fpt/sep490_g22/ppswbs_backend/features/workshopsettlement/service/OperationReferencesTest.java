package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationReferencesTest {

    @Test
    void shortReferenceIsKeptReadable() {
        assertThat(OperationReferences.of("CANCEL-GRP-X-L0-T1-", "k".repeat(36)))
                .isEqualTo("CANCEL-GRP-X-L0-T1-" + "k".repeat(36));
    }

    @Test
    void referenceWithMaxLengthKeyAndMaxLengthTicketCodeStillFits128AndIsStable() {
        String prefix = "MLAND-REPLACE-" + "T".repeat(40) + "-";
        String key = "k".repeat(128);

        String ref = OperationReferences.of(prefix, key);

        assertThat(ref.length()).isLessThanOrEqualTo(128);
        assertThat(ref).startsWith(prefix).isEqualTo(OperationReferences.of(prefix, key));
        assertThat(ref).isNotEqualTo(OperationReferences.of(prefix, "j".repeat(128)));
    }
}
