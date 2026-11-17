package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReadinessCheckOutcome;
import org.springframework.stereotype.Component;

/**
 * Evaluates ticket delivery capabilities against Mland's gate requirements.
 * A shared booking voucher CANNOT satisfy the booking-enabled ticket/scan gate;
 * exactly one Mland-scannable ticket per participant is required.
 */
@Component
public class OtaTicketDeliveryGate {

    public enum TicketDeliveryMode {
        INDIVIDUAL_TICKET_PER_PERSON,
        SHARED_BOOKING_VOUCHER
    }

    public record ProviderTicketDeliveryProfile(
            OtaProvider provider,
            TicketDeliveryMode mode,
            boolean individualQrSupported,
            boolean individualScanVerified,
            String evidenceReference
    ) {}

    /**
     * Evaluates whether the provider's ticket delivery capability satisfies
     * Mland's mandatory individual-ticket scan gate.
     */
    public OtaReadinessCheckOutcome evaluateTicketDeliveryGate(ProviderTicketDeliveryProfile profile) {
        if (profile == null) {
            return OtaReadinessCheckOutcome.FAILED;
        }

        // Shared voucher mode alone is strictly rejected for booking enablement
        if (profile.mode() == TicketDeliveryMode.SHARED_BOOKING_VOUCHER) {
            return OtaReadinessCheckOutcome.FAILED;
        }

        // Must be individual ticket per person, with individual QR and scan verification proof
        if (profile.mode() == TicketDeliveryMode.INDIVIDUAL_TICKET_PER_PERSON
                && profile.individualQrSupported()
                && profile.individualScanVerified()
                && profile.evidenceReference() != null
                && !profile.evidenceReference().isBlank()) {
            return OtaReadinessCheckOutcome.VERIFIED;
        }

        return OtaReadinessCheckOutcome.FAILED;
    }
}
