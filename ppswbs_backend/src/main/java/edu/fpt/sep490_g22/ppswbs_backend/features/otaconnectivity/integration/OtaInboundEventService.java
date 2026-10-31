package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaInboundEventOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaInboundEventReceipt;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelConnectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaInboundEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaInboundEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Persists authenticated, minimized callback receipts before downstream business effects. */
@Service
@RequiredArgsConstructor
public class OtaInboundEventService {

    private static final Pattern SHA_256 = Pattern.compile("[a-fA-F0-9]{64}");
    private static final Pattern EVENT_KIND = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");
    private static final Pattern SAFE_OUTCOME_REFERENCE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final OtaChannelConnectionRepository connectionRepository;
    private final OtaInboundEventRepository inboundEventRepository;
    private final OtaAuditCorrelationService auditCorrelationService;

    @Transactional
    public OtaInboundEventReceipt receive(AuthenticatedOtaInboundEvent event) {
        validate(event);
        var connection = connectionRepository.findByProviderCodeAndEnvironment(event.provider(), event.environment())
                .orElseThrow(() -> new IllegalArgumentException("OTA connection not found"));
        String dedupeKey = sha256(event.externalEventReference() == null
                ? "PAYLOAD:" + event.payloadFingerprint().toLowerCase()
                : "EVENT:" + event.provider().name() + ":" + event.externalEventReference());

        var existing = inboundEventRepository.findByConnectionIdAndDedupeKey(connection.getId(), dedupeKey);
        if (existing.isPresent()) {
            OtaInboundEvent prior = existing.orElseThrow();
            return receipt(prior, true);
        }

        OtaInboundEvent saved = inboundEventRepository.save(OtaInboundEvent.builder()
                .connection(connection)
                .externalEventReference(event.externalEventReference())
                .payloadFingerprint(event.payloadFingerprint().toLowerCase())
                .dedupeKey(dedupeKey)
                .eventKind(event.eventKind())
                .authenticationResult("VERIFIED")
                .processingStatus("RECEIVED")
                .receivedAt(Instant.now())
                .build());
        auditCorrelationService.record(OtaAuditCorrelationService.IntegrationAction.INBOUND_EVENT_RECEIVED,
                event.provider(), null, null, OtaAuditCorrelationService.IntegrationOutcome.ACCEPTED);
        return receipt(saved, false);
    }

    /** Fingerprints adapter-canonicalized, allowlisted JSON without retaining or logging its content. */
    public String fingerprintCanonicalAllowlistedPayload(String canonicalAllowlistedPayload) {
        if (blank(canonicalAllowlistedPayload)) {
            throw new IllegalArgumentException("Canonical allowlisted payload is required");
        }
        return sha256(canonicalAllowlistedPayload);
    }

    /** Stores the safe semantic outcome so replays can return the same acknowledgement. */
    @Transactional
    public OtaInboundEventReceipt recordOutcome(Long eventId, OtaInboundEventOutcome outcome) {
        if (eventId == null || outcome == null || outcome.status() == null
                || outcome.outcomeReference() == null
                || !SAFE_OUTCOME_REFERENCE.matcher(outcome.outcomeReference()).matches()) {
            throw new IllegalArgumentException("Inbound event outcome is invalid");
        }
        OtaInboundEvent event = inboundEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Inbound event not found"));
        if ("RECEIVED".equals(event.getProcessingStatus())) {
            event.setProcessingStatus(outcome.status().name());
            event.setOutcomeReference(outcome.outcomeReference());
            event.setProcessedAt(Instant.now());
            inboundEventRepository.save(event);
        }
        return receipt(event, false);
    }

    private static OtaInboundEventReceipt receipt(OtaInboundEvent event, boolean duplicate) {
        return new OtaInboundEventReceipt(event.getId(), duplicate, event.getProcessingStatus(), event.getOutcomeReference());
    }

    private static void validate(AuthenticatedOtaInboundEvent event) {
        if (event == null || event.provider() == null || event.environment() == null
                || !validFingerprint(event.payloadFingerprint())
                || event.eventKind() == null || !EVENT_KIND.matcher(event.eventKind()).matches()
                || (event.externalEventReference() != null && !validExternalReference(event.externalEventReference()))) {
            throw new IllegalArgumentException("Authenticated OTA event is invalid or not canonicalized");
        }
    }

    private static boolean validFingerprint(String value) {
        return value != null && SHA_256.matcher(value).matches();
    }

    private static boolean validExternalReference(String value) {
        if (value.isBlank() || value.length() > 255) {
            return false;
        }
        return value.chars().noneMatch(Character::isISOControl);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
