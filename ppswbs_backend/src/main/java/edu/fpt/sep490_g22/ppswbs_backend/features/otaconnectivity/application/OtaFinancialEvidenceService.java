package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaChannelRequests.Money;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaFinancialEvidenceDtos.Entry;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaFinancialEvidenceDtos.FinancialEvidenceResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaCustomerPaymentEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutEvidenceType;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutLedgerEntryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtaFinancialEvidenceService {
    private final OtaChannelBookingRepository bookingRepository;
    private final OtaCustomerPaymentEvidenceRepository customerPaymentRepository;
    private final OtaPayoutPositionRepository positionRepository;
    private final OtaPayoutLedgerEntryRepository ledgerRepository;
    private final OtaPayoutReconciliationService payoutService;

    @Transactional(readOnly = true)
    public FinancialEvidenceResponse read(String bookingPublicReference, AccountSummary actor) {
        OtaPayoutReconciliationService.requireManager(actor);
        OtaChannelBooking booking = findBooking(bookingPublicReference);
        var payments = customerPaymentRepository.findAllByChannelBookingIdOrderByRecordedAtAscIdAsc(booking.getId());
        var positions = positionRepository.findAllByChannelBookingIdOrderByCreatedAtAscIdAsc(booking.getId());
        String paymentState = payments.isEmpty() ? "PAID_ON_CHANNEL"
                : payments.getLast().getEvidenceStatus().name();
        String payoutState = positions.isEmpty() ? "NOT_ELIGIBLE" : positions.getLast().getStatus().name();
        List<Entry> entries = new ArrayList<>();
        payments.forEach(e -> entries.add(new Entry(
                UUID.nameUUIDFromBytes(("customer:" + e.getExternalEvidenceReference()).getBytes(StandardCharsets.UTF_8)).toString(),
                "CHANNEL_PAYMENT", e.getEvidenceKind(), money(e.getAmountMinor(), e.getCurrencyCode()),
                e.getExternalEvidenceReference(), "", null, null, null, e.getRecordedAt())));
        if (!positions.isEmpty()) {
            ledgerRepository.findAllByPayoutPositionIdOrderByOccurredAtAscIdAsc(positions.getLast().getId()).forEach(e ->
                    entries.add(new Entry(e.getPublicReference(), e.getEntryKind(), e.getEvidenceType(),
                            money(e.getAmountMinor(), e.getCurrencyCode()), e.getExternalEvidenceReference(),
                            e.getReason(), e.getRelatedRecordReference(), e.getAllocationReference(),
                            e.getPayoutReference(), e.getRecordedAt())));
        }
        entries.sort(Comparator.comparing(Entry::recordedAt));
        return new FinancialEvidenceResponse(booking.getPublicReference(), paymentState, payoutState, List.copyOf(entries));
    }

    @Transactional
    public FinancialEvidenceResponse record(String bookingPublicReference,
                                            OtaPayoutEvidenceCommand command,
                                            AccountSummary actor) {
        OtaPayoutReconciliationService.requireManager(actor);
        payoutService.record(bookingPublicReference, command, actor);
        return read(bookingPublicReference, actor);
    }

    private OtaChannelBooking findBooking(String reference) {
        try {
            String normalized = UUID.fromString(reference).toString();
            return bookingRepository.findByPublicReference(normalized)
                    .orElseThrow(() -> new edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaResourceNotFoundException("OTA channel booking was not found"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Reference must be a UUID");
        }
    }

    private static Money money(long minor, String currency) {
        return new Money(OtaCurrencyMoney.fromMinor(minor, currency).toPlainString(), currency);
    }
}
