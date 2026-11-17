package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaResourceNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtaChannelManagementService {
    private final OtaChannelCatalogService catalog;
    private final OtaChannelMappingService lifecycle;
    private final OtaPriceTermsService prices;
    private final OtaReconciliationService reconciliation;
    private final OtaChannelMappingRepository mappings;
    private final OtaChannelPriceSnapshotRepository snapshots;
    private final OtaControlOutboxRepository controls;

    @Transactional(readOnly = true)
    public OtaChannelViews.Channels list(AccountSummary actor) {
        require(actor, ActorRole.MANAGER, ActorRole.ADMIN);
        return new OtaChannelViews.Channels(catalog.listMappings().stream().map(this::view).toList());
    }

    @Transactional
    public OtaChannelViews.Mapping create(String channel, String product, String option, String offer,
            String session, AccountSummary actor) {
        require(actor, ActorRole.MANAGER, ActorRole.ADMIN);
        return view(catalog.upsertDraft(publicReference(channel), product, option, offer, session));
    }

    @Transactional
    public OtaChannelViews.Mapping update(String mapping, String product, String option, String offer,
            String session, long version, AccountSummary actor) {
        require(actor, ActorRole.MANAGER);
        return view(catalog.update(publicReference(mapping), product, option, offer, session, version));
    }

    @Transactional
    public OtaChannelViews.PriceTerms recordPrice(String mapping, String retail, String retailCurrency,
            String net, String netCurrency, String evidence, Instant from, Instant to, String reason, AccountSummary actor) {
        require(actor, ActorRole.MANAGER);
        var snapshot = prices.recordProviderConfirmedTerms(publicReference(mapping), retail, retailCurrency, net,
                netCurrency, evidence, from, to, reason, actor);
        return new OtaChannelViews.PriceTerms(UUID.fromString(snapshot.getPublicReference()),
                UUID.fromString(snapshot.getMapping().getPublicReference()),
                money(snapshot.getRetailAmountMinor(), snapshot.getCurrencyCode()),
                money(snapshot.getTargetNetAmountMinor(), snapshot.getCurrencyCode()), snapshot.getTermsReference(),
                snapshot.getEffectiveFrom(), snapshot.getEffectiveTo(), "APPROVED");
    }

    @Transactional
    public OtaOperationAccepted sync(String reference, String mode, String reason, String key, AccountSummary actor) {
        require(actor, "PREPARATION".equals(mode) ? new ActorRole[]{ActorRole.MANAGER}
                : new ActorRole[]{ActorRole.MANAGER, ActorRole.ADMIN});
        return reconciliation.requestSync(mapping(reference).getId(), mode, reason, key, actor);
    }

    @Transactional
    public OtaBookingEnableDecision enable(String reference, String evidence, String reason,
            List<OtaReadinessCheckSubmission> checks, AccountSummary actor) {
        require(actor, ActorRole.ADMIN);
        return lifecycle.recordBookingEnableDecision(new OtaBookingEnableCommand(mapping(reference).getId(), evidence,
                reason, actor.accountId().toString(), checks));
    }

    private OtaChannelMapping mapping(String reference) {
        return mappings.findByPublicReference(publicReference(reference))
                .orElseThrow(() -> new OtaResourceNotFoundException("OTA mapping was not found"));
    }

    private OtaChannelViews.Mapping view(OtaChannelMapping mapping) {
        return new OtaChannelViews.Mapping(UUID.fromString(mapping.getPublicReference()),
                UUID.fromString(mapping.getConnection().getPublicReference()), mapping.getConnection().getProviderCode().name(),
                mapping.getStatus().name(), snapshots.resolveCommercialStatus(mapping.getId()).name(),
                mapping.getStatus() == OtaMappingStatus.BOOKING_ENABLED,
                lifecycle.resolveSafeReadinessStatus(mapping.getId()).name(), mapping.getVersion(),
                controls.findTopByMapping_IdOrderByCreatedAtDesc(mapping.getId()).map(OtaControlOutboxCommand::getStatus)
                        .orElse("NONE"));
    }

    private static OtaChannelViews.Money money(long minor, String currency) {
        return new OtaChannelViews.Money(OtaCurrencyMoney.fromMinor(minor, currency).toPlainString(), currency);
    }

    public static String publicReference(String value) {
        return OtaPublicReference.require(value);
    }

    private static void require(AccountSummary actor, ActorRole... roles) {
        if (actor == null || actor.accountId() == null || actor.status() != ActorStatus.ACTIVE
                || !Arrays.asList(roles).contains(actor.role())) {
            throw new OtaForbiddenException("Actor is not authorized for this OTA action");
        }
    }
}
