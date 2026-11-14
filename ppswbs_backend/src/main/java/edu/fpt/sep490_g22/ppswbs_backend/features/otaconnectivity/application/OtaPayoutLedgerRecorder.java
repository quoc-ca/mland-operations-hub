package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutLedgerEntry;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutLedgerEntryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutLedgerKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutPosition;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutPositionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * T110 / T114: opens the supplier payout position and writes the append-only sale facts of an OTA booking in the
 * transaction that confirms it, and the customer-refund fact when the OTA reports a refund.
 */
@Service
@RequiredArgsConstructor
public class OtaPayoutLedgerRecorder {

    private final OtaPayoutPositionRepository positionRepository;
    private final OtaPayoutLedgerEntryRepository ledgerRepository;

    /**
     * Creates the booking's single payout position ({@code NOT_ELIGIBLE}, expected net and currency from the price
     * snapshot) and records {@code GROSS_SALE} (amount collected by the OTA, as reported) and
     * {@code COMMISSION_FEE} (snapshot commission) against it.
     */
    @Transactional
    public OtaPayoutPosition openPositionWithSaleFacts(OtaChannelBooking booking, OtaChannelPriceSnapshot snapshot,
                                                       String paymentEvidenceReference, Instant occurredAt,
                                                       String correlationReference) {
        OtaPayoutPosition position = positionRepository.save(OtaPayoutPosition.create(
                booking.getId(), snapshot.getId(), snapshot.getTargetNetAmountMinor(),
                snapshot.getCurrencyCode(), OtaPayoutStatus.NOT_ELIGIBLE));
        String related = booking.getPublicReference();
        if (booking.getAcceptedAmountMinor() != null) {
            ledgerRepository.save(OtaPayoutLedgerEntry.record(position.getId(), OtaPayoutLedgerKind.GROSS_SALE,
                    booking.getAcceptedAmountMinor(), booking.getCurrencyCode(), paymentEvidenceReference,
                    occurredAt, "Amount collected by the OTA for the confirmed booking", related,
                    correlationReference));
        }
        long commission = booking.getCommissionAmountMinor() == null ? 0L : booking.getCommissionAmountMinor();
        ledgerRepository.save(OtaPayoutLedgerEntry.record(position.getId(), OtaPayoutLedgerKind.COMMISSION_FEE,
                commission, snapshot.getCurrencyCode(), paymentEvidenceReference, occurredAt,
                "OTA commission from the price snapshot at confirmation", related, correlationReference));
        return position;
    }

    /** Records a refund reported by the OTA against the booking's payout position (no position: nothing to link). */
    @Transactional
    public void recordCustomerRefund(OtaChannelBooking booking, String refundEvidenceReference,
                                     long amountMinor, String currencyCode, String correlationReference) {
        List<OtaPayoutPosition> positions = positionRepository
                .findAllByChannelBookingIdOrderByCreatedAtAscIdAsc(booking.getId());
        if (positions.isEmpty()) {
            return;
        }
        ledgerRepository.save(OtaPayoutLedgerEntry.record(positions.getFirst().getId(),
                OtaPayoutLedgerKind.CUSTOMER_REFUND, amountMinor, currencyCode, refundEvidenceReference,
                Instant.now(), "Refund reported by the OTA", booking.getPublicReference(), correlationReference));
    }
}
