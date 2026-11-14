package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
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
import jakarta.validation.constraints.Size;
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
@Slf4j
public class OtaProviderEventController {

    private final OtaProviderEventHandler eventHandler;
    private final OtaChannelConnectionRepository connectionRepository;
    private final org.springframework.core.env.Environment springEnvironment;

    @org.springframework.beans.factory.annotation.Autowired
    public OtaProviderEventController(OtaProviderEventHandler eventHandler,
                                      OtaChannelConnectionRepository connectionRepository,
                                      @org.springframework.beans.factory.annotation.Autowired(required = false)
                                      org.springframework.core.env.Environment springEnvironment) {
        this.eventHandler = eventHandler;
        this.connectionRepository = connectionRepository;
        this.springEnvironment = springEnvironment;
    }

    public OtaProviderEventController(OtaProviderEventHandler eventHandler,
                                      OtaChannelConnectionRepository connectionRepository) {
        this(eventHandler, connectionRepository, null);
    }

    @Value("${ota.auth.allow-test-tokens:false}")
    private boolean allowTestTokens;

    /** Comma-separated providers whose signed production callback is approved; empty means none (fail closed). */
    @Value("${ota.provider-callbacks.approved-production-providers:}")
    private String approvedProductionProviders;

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
            String correlationReference,
            @Size(max = 128) String displayedPolicyReference,
            @Size(max = 64) String displayedPolicyVersion,
            Instant bookingCutoffAt,
            @Size(max = 64) String bookingCutoffTimezone,
            @Min(value = 0, message = "Fee must not be negative") Long feeAmountMinor
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
                correlation,
                request.displayedPolicyReference(),
                request.displayedPolicyVersion(),
                request.bookingCutoffAt(),
                request.bookingCutoffTimezone(),
                request.feeAmountMinor()
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

    /**
     * E3 (2026-11-12), fail closed until a provider's real signed callback is approved in provider-readiness.
     * <ul>
     *   <li>PRODUCTION callbacks are refused (403) unless the provider is listed in
     *       {@code ota.provider-callbacks.approved-production-providers}; that list stays empty until the provider's
     *       signature procedure is approved and implemented. The interim shared-value check is never a production
     *       authentication scheme.</li>
     *   <li>The connection must exist and be ACTIVE.</li>
     *   <li>Test tokens are honoured only for the TEST environment, even when {@code ota.auth.allow-test-tokens} is on.</li>
     *   <li>The shared value is compared in constant time. Replays are deduplicated downstream by the inbound event
     *       service (external event reference + payload fingerprint).</li>
     * </ul>
     */
    private void verifyProviderSignature(String signature, OtaProvider provider, OtaEnvironment environment) {
        if (signature == null || signature.isBlank()) {
            throw new OtaUnauthorizedException("Provider signature is required");
        }

        if (environment == OtaEnvironment.PRODUCTION && !approvedProductionProviders().contains(provider.name())) {
            log.warn("Refused OTA production callback: no approved signed callback for provider={}", provider);
            throw new OtaForbiddenException("Production callbacks are not enabled for this provider");
        }

        if (allowTestTokens && environment == OtaEnvironment.TEST
                && (signature.startsWith("test-") || signature.startsWith("test-sig:"))) {
            return;
        }

        Optional<OtaChannelConnection> connOpt = connectionRepository
                .findByProviderCodeAndEnvironment(provider, environment);

        if (connOpt.isEmpty() || !"ACTIVE".equalsIgnoreCase(connOpt.get().getStatus())) {
            throw new OtaUnauthorizedException("No active OTA connection found for provider " + provider);
        }

        String credentialRef = connOpt.get().getCredentialReference();
        String resolvedSecret = resolveSecret(credentialRef);
        if (resolvedSecret == null || resolvedSecret.isBlank()) {
            log.warn("Refused OTA callback: unresolvable credential reference for provider={}, environment={}",
                    provider, environment);
            throw new OtaUnauthorizedException("Invalid provider signature or credentials");
        }

        if (!java.security.MessageDigest.isEqual(
                resolvedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                signature.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new OtaUnauthorizedException("Invalid provider signature or credentials");
        }
    }

    private String resolveSecret(String credentialReference) {
        if (credentialReference == null || credentialReference.isBlank()) {
            return null;
        }
        String ref = credentialReference.trim();
        if (springEnvironment != null) {
            String val = springEnvironment.getProperty(ref);
            if (val != null && !val.isBlank()) {
                return val;
            }
        }
        String envVal = System.getenv(ref);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }
        return null;
    }

    private java.util.Set<String> approvedProductionProviders() {
        if (approvedProductionProviders == null || approvedProductionProviders.isBlank()) return java.util.Set.of();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String name : approvedProductionProviders.split(",")) {
            if (!name.isBlank()) names.add(name.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return names;
    }
}

