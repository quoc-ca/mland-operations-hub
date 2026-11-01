package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaMappingLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaResourceNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutEvidenceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutLedgerEntry;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutLedgerEntryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutLedgerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutPosition;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutPositionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtaPayoutReconciliationService {
    private final OtaChannelBookingRepository bookingRepository;
    private final OtaPayoutPositionRepository positionRepository;
    private final OtaPayoutLedgerEntryRepository ledgerRepository;
    private final OtaReconciliationWorkItemRepository workItemRepository;

    @Transactional
    public OtaPayoutReconciliationResult record(String bookingPublicReference,
                                                OtaPayoutEvidenceCommand command,
                                                AccountSummary actor) {
        requireManager(actor);
        if (command == null || command.evidenceType() == null) {
            throw new IllegalArgumentException("Payout evidence type is required");
        }
        String bookingReference = parseUuid(bookingPublicReference);
        OtaChannelBooking booking = bookingRepository.findByPublicReferenceForUpdate(bookingReference)
                .orElseThrow(() -> new OtaResourceNotFoundException("OTA channel booking was not found"));
        List<OtaPayoutPosition> positions = positionRepository.findByChannelBookingIdForUpdate(booking.getId());
        if (positions.size() != 1) {
            throw new OtaMappingLifecycleException("Exactly one payout position is required for this booking");
        }
        OtaPayoutPosition position = positions.getFirst();
        String currency = normalizeCurrency(command.currencyCode());
        String reference = required(command.evidenceReference(), 200, "Payout evidence reference is required");
        String reason = required(command.reason(), 1000, "Payout reason is required");
        if (command.occurredAt() == null) {
            throw new IllegalArgumentException("Payout evidence time is required");
        }
        OtaPayoutEvidenceType type = command.evidenceType();
        OtaPayoutLedgerKind kind = kindFor(type);
        String relatedReference = relatedReference(position, bookingReference, command);
        String allocation = command.allocationReference() == null ? null
                : required(command.allocationReference(), 128, "Allocation reference is invalid");
        String payoutReference = command.payoutReference() == null ? null
                : required(command.payoutReference(), 200, "Payout reference is invalid");
        if ((type == OtaPayoutEvidenceType.REMITTANCE || type == OtaPayoutEvidenceType.BANK_RECEIPT)
                != (payoutReference != null)) {
            throw new IllegalArgumentException("Remittance and bank receipt require a payout reference");
        }
        if (type == OtaPayoutEvidenceType.BANK_RECEIPT && allocation == null) {
            throw new IllegalArgumentException("Bank receipt requires a booking-specific allocation reference");
        }
        if (type != OtaPayoutEvidenceType.BANK_RECEIPT && allocation != null) {
            throw new IllegalArgumentException("Allocation reference is only valid for a bank receipt");
        }
        if (!currency.equals(position.getCurrencyCode()) && type != OtaPayoutEvidenceType.REVERSAL
                && type != OtaPayoutEvidenceType.VARIANCE
                && type != OtaPayoutEvidenceType.DISPUTE && type != OtaPayoutEvidenceType.BANK_RECEIPT) {
            throw new OtaMappingLifecycleException("Payout evidence currency differs from the position");
        }
        var previous = ledgerRepository.findByPayoutPositionIdAndEntryKindAndExternalEvidenceReference(
                position.getId(), kind.name(), reference);
        if (previous.isPresent()) {
            OtaPayoutLedgerEntry existing = previous.orElseThrow();
            if (!Objects.equals(existing.getEvidenceType(), type.name())
                    || existing.getAmountMinor() != command.amountMinor()
                    || !existing.getCurrencyCode().equals(currency)
                    || !Objects.equals(existing.getReason(), reason)
                    || !Objects.equals(existing.getRelatedRecordReference(), relatedReference)
                    || !Objects.equals(existing.getAllocationReference(), allocation)
                    || !Objects.equals(existing.getPayoutReference(), payoutReference)
                    || !existing.getOccurredAt().equals(command.occurredAt())) {
                throw new OtaMappingLifecycleException("Conflicting payout evidence replay");
            }
            return new OtaPayoutReconciliationResult(existing.getPublicReference(), position.getStatus(),
                    adjustedExpected(position), null);
        }
        if (type == OtaPayoutEvidenceType.REVERSAL) {
            boolean alreadyReversed = ledgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(
                    position.getId()).stream().anyMatch(entry -> OtaPayoutLedgerKind.REVERSAL.name()
                    .equals(entry.getEntryKind()) && relatedReference.equals(entry.getRelatedRecordReference()));
            if (alreadyReversed) throw new OtaMappingLifecycleException("Payout entry was already reversed");
        }
        if (allocation != null && ledgerRepository.findByAllocationReference(allocation).isPresent()) {
            throw new OtaMappingLifecycleException("Payout allocation reference is already assigned");
        }

        validateStageAndAmount(position, command);
        OtaPayoutLedgerEntry entry = ledgerRepository.save(OtaPayoutLedgerEntry.record(
                position.getId(), kind, command.amountMinor(), currency, reference,
                command.occurredAt(), reason, relatedReference, actor.accountId().toString(), type,
                allocation, payoutReference));
        long expected = adjustedExpected(position);
        String dispute = null;
        switch (type) {
            case ELIGIBILITY -> position.transitionTo(OtaPayoutStatus.ACCRUED);
            case INVOICE -> {
                if (command.amountMinor() == expected) position.transitionTo(OtaPayoutStatus.INVOICED);
                else dispute = openDispute(booking, position, actor);
            }
            case REMITTANCE -> {
                if (command.amountMinor() == expected) position.transitionTo(OtaPayoutStatus.PAYOUT_PENDING);
                else dispute = openDispute(booking, position, actor);
            }
            case BANK_RECEIPT -> {
                long received = totalBankReceipts(position);
                if (currency.equals(position.getCurrencyCode()) && received == expected) {
                    position.transitionTo(OtaPayoutStatus.PAID_OUT);
                } else {
                    dispute = openDispute(booking, position, actor);
                }
            }
            case ADJUSTMENT, REVERSAL -> {
                if (position.getStatus() == OtaPayoutStatus.PAID_OUT) {
                    dispute = openDispute(booking, position, actor);
                }
            }
            case VARIANCE, DISPUTE -> dispute = openDispute(booking, position, actor);
        }
        return new OtaPayoutReconciliationResult(entry.getPublicReference(), position.getStatus(),
                expected, dispute);
    }

    private String relatedReference(OtaPayoutPosition position, String bookingReference,
                                    OtaPayoutEvidenceCommand command) {
        if (command.evidenceType() != OtaPayoutEvidenceType.ADJUSTMENT
                && command.evidenceType() != OtaPayoutEvidenceType.REVERSAL) {
            if (command.relatedEntryPublicReference() != null) {
                throw new IllegalArgumentException("Only a correction may reference a prior ledger entry");
            }
            return bookingReference;
        }
        String priorReference = parseUuid(command.relatedEntryPublicReference());
        OtaPayoutLedgerEntry prior = ledgerRepository.findByPublicReference(priorReference)
                .orElseThrow(() -> new OtaResourceNotFoundException("Related payout entry was not found"));
        if (!prior.getPayoutPositionId().equals(position.getId())) {
            throw new OtaMappingLifecycleException("Correction must reference the same payout position");
        }
        if (command.evidenceType() == OtaPayoutEvidenceType.REVERSAL) {
            if ((!OtaPayoutLedgerKind.ADJUSTMENT.name().equals(prior.getEntryKind())
                    && !OtaPayoutLedgerKind.RECEIVED_PAYOUT.name().equals(prior.getEntryKind()))
                    || command.amountMinor() != Math.negateExact(prior.getAmountMinor())
                    || !normalizeCurrency(command.currencyCode()).equals(prior.getCurrencyCode())) {
                throw new OtaMappingLifecycleException("Reversal must negate its original adjustment or bank receipt");
            }
        }
        return priorReference;
    }

    private void validateStageAndAmount(OtaPayoutPosition position, OtaPayoutEvidenceCommand command) {
        OtaPayoutEvidenceType type = command.evidenceType();
        OtaPayoutStatus required = switch (type) {
            case ELIGIBILITY -> OtaPayoutStatus.NOT_ELIGIBLE;
            case INVOICE -> OtaPayoutStatus.ACCRUED;
            case REMITTANCE -> OtaPayoutStatus.INVOICED;
            case BANK_RECEIPT -> OtaPayoutStatus.PAYOUT_PENDING;
            default -> null;
        };
        if (required != null && position.getStatus() != required) {
            throw new OtaMappingLifecycleException("Payout evidence is out of lifecycle order");
        }
        if (type == OtaPayoutEvidenceType.ELIGIBILITY
                && command.amountMinor() != adjustedExpected(position)) {
            throw new OtaMappingLifecycleException("Eligibility amount must match expected payout");
        }
        if ((type == OtaPayoutEvidenceType.BANK_RECEIPT
                || type == OtaPayoutEvidenceType.INVOICE
                || type == OtaPayoutEvidenceType.REMITTANCE) && command.amountMinor() <= 0) {
            throw new IllegalArgumentException("Payout stage amount must be positive");
        }
        if ((type == OtaPayoutEvidenceType.ADJUSTMENT || type == OtaPayoutEvidenceType.REVERSAL)
                && command.amountMinor() == 0) {
            throw new IllegalArgumentException("Correction amount must be nonzero");
        }
    }

    private long adjustedExpected(OtaPayoutPosition position) {
        long total = position.getExpectedAmountMinor();
        List<OtaPayoutLedgerEntry> entries = ledgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(
                position.getId());
        Set<String> adjustmentReferences = new HashSet<>();
        for (OtaPayoutLedgerEntry entry : entries) {
            if (OtaPayoutLedgerKind.ADJUSTMENT.name().equals(entry.getEntryKind())) {
                adjustmentReferences.add(entry.getPublicReference());
                total = Math.addExact(total, entry.getAmountMinor());
            }
        }
        for (OtaPayoutLedgerEntry entry : entries) {
            if (OtaPayoutLedgerKind.REVERSAL.name().equals(entry.getEntryKind())
                    && adjustmentReferences.contains(entry.getRelatedRecordReference())) {
                total = Math.addExact(total, entry.getAmountMinor());
            }
        }
        if (total < 0) throw new OtaMappingLifecycleException("Adjusted expected payout cannot be negative");
        return total;
    }

    private long totalBankReceipts(OtaPayoutPosition position) {
        long total = 0;
        List<OtaPayoutLedgerEntry> entries = ledgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(
                position.getId());
        Set<String> reversedReferences = new HashSet<>();
        Set<String> remittanceReferences = new HashSet<>();
        for (OtaPayoutLedgerEntry entry : entries) {
            if (OtaPayoutLedgerKind.REVERSAL.name().equals(entry.getEntryKind())) {
                reversedReferences.add(entry.getRelatedRecordReference());
            }
            if (OtaPayoutEvidenceType.REMITTANCE.name().equals(entry.getEvidenceType())
                    && entry.getPayoutReference() != null) {
                remittanceReferences.add(entry.getPayoutReference());
            }
        }
        for (OtaPayoutLedgerEntry entry : entries) {
            if (OtaPayoutEvidenceType.BANK_RECEIPT.name().equals(entry.getEvidenceType())) {
                if (reversedReferences.contains(entry.getPublicReference())) continue;
                if (!remittanceReferences.contains(entry.getPayoutReference())) return Long.MIN_VALUE;
                if (!position.getCurrencyCode().equals(entry.getCurrencyCode())) return Long.MIN_VALUE;
                total = Math.addExact(total, entry.getAmountMinor());
            }
        }
        return total;
    }

    private String openDispute(OtaChannelBooking booking, OtaPayoutPosition position, AccountSummary actor) {
        OtaReconciliationWorkItem item = workItemRepository.save(OtaReconciliationWorkItem.builder()
                .mapping(booking.getMapping())
                .channelBookingId(booking.getId())
                .payoutPositionId(position.getId())
                .locationReference(booking.getMapping().getLocationReference())
                .category("PAYOUT_DISPUTE")
                .status("OPEN")
                .assignedManagerReference(actor.accountId().toString())
                .build());
        return item.getPublicReference();
    }

    private static OtaPayoutLedgerKind kindFor(OtaPayoutEvidenceType type) {
        return switch (type) {
            case ELIGIBILITY, INVOICE, REMITTANCE -> OtaPayoutLedgerKind.EXPECTED_PAYOUT;
            case BANK_RECEIPT -> OtaPayoutLedgerKind.RECEIVED_PAYOUT;
            case ADJUSTMENT -> OtaPayoutLedgerKind.ADJUSTMENT;
            case REVERSAL -> OtaPayoutLedgerKind.REVERSAL;
            case VARIANCE -> OtaPayoutLedgerKind.FX_VARIANCE;
            case DISPUTE -> OtaPayoutLedgerKind.DISPUTE;
        };
    }

    private static void requireManager(AccountSummary actor) {
        if (actor == null || actor.accountId() == null || actor.status() != ActorStatus.ACTIVE
                || actor.role() != ActorRole.MANAGER) {
            throw new OtaForbiddenException("Payout reconciliation requires an active Manager");
        }
    }

    private static String parseUuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Reference must be a UUID");
        }
    }

    private static String normalizeCurrency(String value) {
        if (value == null) throw new IllegalArgumentException("Currency is required");
        String currency = value.trim().toUpperCase(Locale.ROOT);
        OtaCurrencyMoney.fractionDigits(currency);
        return currency;
    }

    private static String required(String value, int maxLength, String message) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
