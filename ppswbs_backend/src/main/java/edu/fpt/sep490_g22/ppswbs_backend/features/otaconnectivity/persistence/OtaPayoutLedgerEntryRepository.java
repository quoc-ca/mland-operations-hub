package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface OtaPayoutLedgerEntryRepository extends Repository<OtaPayoutLedgerEntry, Long> {
    OtaPayoutLedgerEntry save(OtaPayoutLedgerEntry entry);

    Optional<OtaPayoutLedgerEntry> findByPublicReference(String publicReference);

    Optional<OtaPayoutLedgerEntry> findByAllocationReference(String allocationReference);

    List<OtaPayoutLedgerEntry> findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(Long payoutPositionId);

    Optional<OtaPayoutLedgerEntry> findByPayoutPositionIdAndEntryKindAndExternalEvidenceReference(
            Long payoutPositionId, String entryKind, String externalEvidenceReference);
}
