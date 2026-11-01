package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaUnauthorizedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler.OtaBookingEventCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaProviderEventHandler.OtaBookingEventResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnection;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaEnvironment;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * T032: Versioned provider-event controller and safe correlation-linked acknowledgement.
 * Provider authentication is independent from Mland user sessions.
 * Never leaks credentials or raw provider payloads in responses.
 */
@RestController
@RequestMapping(value = "/api/v1/ota-provider", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class OtaProviderEventController {

    private final OtaProviderEventHandler eventHandler;
    private final OtaChannelConnectionRepository connectionRepository;

    @Value("${ota.auth.allow-test-tokens:false}")
    private boolean allowTestTokens;

    public record ProviderBookingCallbackRequest(
            @NotNull(message = "Provider is required")
            OtaProvider provider,

            @NotNull(message = "Environment is required")
            OtaEnvironment environment,

            String externalEventReference,

            @NotBlank(message = "External booking reference is required")
            String externalBookingReference,

            @NotBlank(message = "Provider product reference is required")
            String providerProductReference,

            @NotBlank(message = "Provider option reference is required")
            String providerOptionReference,

            @Min(value = 1, message = "Participant count must be at least 1")
            int participantCount,

            @NotNull(message = "Accepted amount is required")
            @Min(value = 1, message = "Accepted amount must be positive")
            Long acceptedAmountMinor,

            @NotBlank(message = "Currency code is required")
            @Pattern(regexp = "[A-Za-z]{3}", message = "Currency code must be a 3-letter ISO code")
            String currencyCode,

            String externalPaymentEvidenceReference,
            String correlationReference
    ) {}

    public record ProviderEventAcknowledgement(
            String status,
            String externalBookingReference,
            String channelBookingReference,
            String mlandBookingReference,
            int participantCount,
            List<String> ticketReferences,
            String rejectionReason,
            String correlationReference,
            Instant acknowledgedAt
    ) {}

    @PostMapping(value = "/booking-events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProviderEventAcknowledgement> handleBookingCallback(
            @RequestHeader(value = "X-Ota-Signature", required = false) String signature,
            @Valid @RequestBody ProviderBookingCallbackRequest request,
            HttpServletRequest httpServletRequest
    ) {
        verifyProviderSignature(signature, request.provider(), request.environment());

        String correlation = request.correlationReference() != null && !request.correlationReference().isBlank()
                ? request.correlationReference().trim()
                : CorrelationIdFilter.current(httpServletRequest);

        OtaBookingEventCommand command = new OtaBookingEventCommand(
                request.provider(),
                request.environment(),
                request.externalEventReference(),
                request.externalBookingReference(),
                request.providerProductReference(),
                request.providerOptionReference(),
                request.participantCount(),
                request.acceptedAmountMinor(),
                request.currencyCode().toUpperCase(),
                request.externalPaymentEvidenceReference(),
                correlation
        );

        OtaBookingEventResult result = eventHandler.handleBookingEvent(command);

        ProviderEventAcknowledgement acknowledgement = new ProviderEventAcknowledgement(
                result.status(),
                request.externalBookingReference(),
                result.channelBookingReference(),
                result.mlandBookingReference(),
                result.participantCount(),
                result.ticketReferences(),
                result.rejectionReason(),
                correlation,
                Instant.now()
        );

        if ("CONFIRMED".equals(result.status())) {
            return ResponseEntity.status(HttpStatus.CREATED).body(acknowledgement);
        } else {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(acknowledgement);
        }
    }

    private void verifyProviderSignature(String signature, OtaProvider provider, OtaEnvironment environment) {
        if (signature == null || signature.isBlank()) {
            throw new OtaUnauthorizedException("Provider signature is required");
        }

        if (allowTestTokens && (signature.startsWith("test-") || signature.startsWith("test-sig:"))) {
            return;
        }

        Optional<OtaChannelConnection> connOpt = connectionRepository
                .findByProviderCodeAndEnvironment(provider, environment);

        if (connOpt.isEmpty()) {
            throw new OtaUnauthorizedException("No active OTA connection found for provider " + provider);
        }

        String expectedCredentialRef = connOpt.get().getCredentialReference();
        if (expectedCredentialRef == null || !expectedCredentialRef.equals(signature.trim())) {
            throw new OtaUnauthorizedException("Invalid provider signature or credentials");
        }
    }
}

