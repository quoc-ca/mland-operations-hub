package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCancellationService.OtaCancellationEventCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Adapter for incoming GetYourGuide cancellation and refund events.
 * Maps authenticated GYG webhook payloads into domain OtaCancellationEventCommands.
 * Unrecognized actions fail closed to Unmapped, routing to reconciliation without auto-cancellation.
 */
@Component
public class GetYourGuideCancellationAdapter extends BaseOtaCancellationAdapter {

    @Autowired
    public GetYourGuideCancellationAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public OtaProvider supportedProvider() {
        return OtaProvider.GETYOURGUIDE;
    }

    @Override
    protected OtaCancellationMappingResult doMapEvent(
            OtaChannelConnection connection,
            JsonNode rootNode,
            String correlationId,
            Map<String, String> headers
    ) {
        String bookingRef = null;
        if (rootNode.hasNonNull("bookingReference")) {
            bookingRef = rootNode.get("bookingReference").asText().trim();
        } else if (rootNode.hasNonNull("externalBookingReference")) {
            bookingRef = rootNode.get("externalBookingReference").asText().trim();
        }

        if (bookingRef == null || bookingRef.isBlank()) {
            return new OtaCancellationMappingResult.Rejected(
                    "VALIDATION_ERROR",
                    "Missing external booking reference in GetYourGuide payload"
            );
        }

        String eventRef = null;
        if (rootNode.hasNonNull("notificationId")) {
            eventRef = rootNode.get("notificationId").asText().trim();
        } else if (rootNode.hasNonNull("eventId")) {
            eventRef = rootNode.get("eventId").asText().trim();
        } else if (rootNode.hasNonNull("externalEventReference")) {
            eventRef = rootNode.get("externalEventReference").asText().trim();
        } else {
            eventRef = "gyg-ccl-ev-" + UUID.randomUUID().toString().substring(0, 8);
        }

        String event = null;
        if (rootNode.hasNonNull("event")) {
            event = rootNode.get("event").asText().trim();
        } else if (rootNode.hasNonNull("action")) {
            event = rootNode.get("action").asText().trim();
        } else if (rootNode.hasNonNull("status")) {
            event = rootNode.get("status").asText().trim();
        } else if (rootNode.hasNonNull("cancellationKind")) {
            event = rootNode.get("cancellationKind").asText().trim();
        }

        if (event == null || event.isBlank()) {
            return new OtaCancellationMappingResult.Unmapped(
                    "UNKNOWN",
                    "Missing event or action field in GetYourGuide cancellation payload"
            );
        }

        String cancellationKind = switch (event.toUpperCase()) {
            case "BOOKING_CANCEL_REQUEST", "REQUESTED", "CANCELLATION_REQUESTED" -> "REQUESTED";
            case "BOOKING_CANCELLED", "CONFIRMED_CANCELLED", "CANCELLED" -> "CONFIRMED_CANCELLED";
            case "BOOKING_REFUNDED", "REFUNDED" -> "REFUNDED";
            default -> null;
        };

        if (cancellationKind == null) {
            return new OtaCancellationMappingResult.Unmapped(
                    event,
                    "Unmapped GetYourGuide cancellation event: " + event
            );
        }

        Long refundAmountMinor = null;
        if (rootNode.hasNonNull("refundAmountMinor")) {
            refundAmountMinor = rootNode.get("refundAmountMinor").asLong();
        } else if (rootNode.hasNonNull("refundAmount")) {
            refundAmountMinor = rootNode.get("refundAmount").asLong();
        }

        String currency = null;
        if (rootNode.hasNonNull("currency")) {
            currency = rootNode.get("currency").asText().trim().toUpperCase();
        } else if (rootNode.hasNonNull("currencyCode")) {
            currency = rootNode.get("currencyCode").asText().trim().toUpperCase();
        }

        String reason = null;
        if (rootNode.hasNonNull("reason")) {
            reason = rootNode.get("reason").asText().trim();
        }

        OtaCancellationEventCommand command = new OtaCancellationEventCommand(
                connection.getProviderCode(),
                connection.getEnvironment(),
                eventRef,
                bookingRef,
                cancellationKind,
                refundAmountMinor,
                currency,
                reason,
                correlationId
        );

        return new OtaCancellationMappingResult.Mapped(command);
    }
}

