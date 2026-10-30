package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaMappingLifecycleException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaResourceNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMapping;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelMappingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelPriceSnapshotRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtaPriceTermsService {
    private final OtaChannelMappingRepository mappingRepository;
    private final OtaChannelPriceSnapshotRepository priceSnapshotRepository;
    private final OtaReconciliationDecisionRepository decisionRepository;
    private final OtaAuditCorrelationService auditCorrelationService;

    @Transactional
    public OtaChannelPriceSnapshot recordProviderConfirmedTerms(String mappingPublicReference,
                                                                 String retailAmount,
                                                                 String retailCurrency,
                                                                 String targetNetAmount,
                                                                 String targetNetCurrency,
                                                                 String evidenceReference,
                                                                 Instant effectiveFrom,
                                                                 Instant effectiveTo,
                                                                 String reason,
                                                                 AccountSummary actor) {
        UUID mappingReference = parseUuid(mappingPublicReference);
        requireManager(actor);
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new IllegalArgumentException("A price terms reason is required");
        }
        if (effectiveFrom == null || effectiveFrom.getNano() % 1000 != 0
                || (effectiveTo != null && (effectiveTo.getNano() % 1000 != 0 || !effectiveTo.isAfter(effectiveFrom)))) {
            throw new IllegalArgumentException("Price effective times must be valid microsecond UTC instants");
        }
        String normalizedRetailCurrency = normalizeCurrency(retailCurrency);
        String normalizedNetCurrency = normalizeCurrency(targetNetCurrency);
        if (!normalizedRetailCurrency.equals(normalizedNetCurrency)) {
            throw new IllegalArgumentException("Retail and target-net amounts must use the same currency");
        }
        long retailMinor = OtaCurrencyMoney.toMinor(retailAmount, normalizedRetailCurrency);
        long netMinor = OtaCurrencyMoney.toMinor(targetNetAmount, normalizedNetCurrency);
        if (retailMinor < 0 || netMinor < 0) {
            throw new IllegalArgumentException("Commercial price amounts must be non-negative");
        }
        if (evidenceReference == null || evidenceReference.isBlank() || evidenceReference.trim().length() > 255) {
            throw new IllegalArgumentException("A safe provider confirmation reference is required");
        }

        OtaChannelMapping mapping = mappingRepository.findByPublicReferenceForUpdate(mappingReference.toString())
                .orElseThrow(() -> new OtaResourceNotFoundException("OTA channel mapping was not found"));
        var existingAtStart = priceSnapshotRepository.findByMapping_IdAndEffectiveFrom(mapping.getId(), effectiveFrom);
        if (existingAtStart.isPresent()) {
            OtaChannelPriceSnapshot existing = existingAtStart.get();
            if (existing.getRetailAmountMinor() == retailMinor
                    && existing.getTargetNetAmountMinor() == netMinor
                    && existing.getCurrencyCode().equals(normalizedRetailCurrency)
                    && existing.getTermsReference().equals(evidenceReference.trim())
                    && java.util.Objects.equals(existing.getEffectiveTo(), effectiveTo)) {
                return existing;
            }
            throw new OtaMappingLifecycleException("A different price snapshot already starts at this effectiveFrom");
        }

        OtaChannelPriceSnapshot snapshot = priceSnapshotRepository.saveAndFlush(OtaChannelPriceSnapshot.providerConfirmed(
                mapping, retailMinor, netMinor, null, normalizedRetailCurrency, evidenceReference.trim(),
                effectiveFrom, effectiveTo));
        String actorReference = actor.accountId().toString();
        String operationReference = snapshot.getPublicReference();
        String correlationReference = auditCorrelationService.newCorrelationReference();
        String fingerprint = fingerprint(mappingReference.toString(), retailMinor, netMinor, normalizedRetailCurrency,
                evidenceReference.trim(), effectiveFrom, effectiveTo, reason.trim());
        String naturalIdempotency = fingerprint(mappingReference.toString(), effectiveFrom.toString());
        decisionRepository.save(OtaReconciliationDecision.record(null, mapping,
                mapping.getConnection().getProviderCode().name(), mapping.getLocationReference(), actorReference,
                actor.role().name(), "PRICE_TERMS", "BUSINESS", reason.trim(), "APPROVED", operationReference,
                correlationReference, naturalIdempotency, fingerprint, null));
        return snapshot;
    }

    @Transactional(readOnly = true)
    public OtaChannelPriceSnapshot resolveAt(Long mappingId, Instant bookingInstant) {
        if (mappingId == null || bookingInstant == null) throw new IllegalArgumentException("Mapping and booking instant are required");
        return priceSnapshotRepository.findFirstByMapping_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        mappingId, bookingInstant)
                .filter(snapshot -> snapshot.getEffectiveTo() == null || bookingInstant.isBefore(snapshot.getEffectiveTo()))
                .orElse(null);
    }

    private static UUID parseUuid(String reference) {
        try {
            return UUID.fromString(reference);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Mapping reference must be a UUID");
        }
    }

    private static void requireManager(AccountSummary actor) {
        if (actor == null || actor.accountId() == null || actor.status() != ActorStatus.ACTIVE
                || actor.role() != ActorRole.MANAGER) {
            throw new OtaMappingLifecycleException("Price terms require an active Manager");
        }
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null) throw new IllegalArgumentException("Currency is required");
        String normalized = currency.trim().toUpperCase(java.util.Locale.ROOT);
        OtaCurrencyMoney.fractionDigits(normalized);
        return normalized;
    }

    private static String fingerprint(Object... fields) {
        String value = java.util.Arrays.stream(fields).map(String::valueOf)
                .reduce((a, b) -> a + "\u0000" + b).orElse("");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
