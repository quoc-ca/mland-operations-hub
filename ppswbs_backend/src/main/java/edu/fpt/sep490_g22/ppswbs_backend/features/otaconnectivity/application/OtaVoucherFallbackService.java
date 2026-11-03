package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaChannelBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItem;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaReconciliationWorkItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Records a controlled incident when an OTA supplies only one shared voucher.
 * It never converts the voucher into a whole-booking check-in.
 */
@Service
public class OtaVoucherFallbackService {
    public record IncidentCommand(
            String authorization,
            String channelBookingReference,
            String locationReference,
            String incidentReason,
            String evidenceReference,
            String correlationReference,
            boolean individualTicketProofPresent) {}

    public record IncidentOutcome(String status, String workItemReference, String correlationReference) {}

    private final OtaAuthorizationService authorizationService;
    private final OtaReconciliationWorkItemRepository workItems;
    private final OtaAuditCorrelationService audit;
    private final OtaChannelBookingRepository bookingRepository;

    public OtaVoucherFallbackService(OtaAuthorizationService authorizationService,
                                     OtaReconciliationWorkItemRepository workItems,
                                     OtaAuditCorrelationService audit) {
        this(authorizationService, workItems, audit, null);
    }

    @Autowired
    public OtaVoucherFallbackService(OtaAuthorizationService authorizationService,
                                     OtaReconciliationWorkItemRepository workItems,
                                     OtaAuditCorrelationService audit,
                                     OtaChannelBookingRepository bookingRepository) {
        this.authorizationService = authorizationService;
        this.workItems = workItems;
        this.audit = audit;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public IncidentOutcome recordSharedVoucherIncident(IncidentCommand command) {
        if (command == null || blank(command.channelBookingReference()) || blank(command.locationReference())
                || blank(command.incidentReason()) || blank(command.evidenceReference())) {
            throw new IllegalArgumentException("booking, location, reason and evidence are required");
        }
        if (command.individualTicketProofPresent()) {
            throw new OtaForbiddenException("Shared-voucher fallback is unavailable when individual ticket proof exists");
        }

        AccountSummary actor = authorizationService.authenticate(command.authorization());
        Long requiredLocationId;
        try {
            requiredLocationId = Long.valueOf(command.locationReference().trim());
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Location reference must be a valid numeric ID");
        }

        if (actor.role() == ActorRole.MANAGER || actor.role() == ActorRole.ADMIN) {
            // Managers and Admins have global authority for OTA reconciliation
        } else if (actor.role() == ActorRole.STAFF) {
            authorizationService.requireStaffLocation(command.authorization(), requiredLocationId);
        } else {
            throw new OtaForbiddenException("Access requires an active Manager, Admin, or scoped Staff");
        }

        if (bookingRepository != null) {
            Optional<OtaChannelBooking> bookingOpt = bookingRepository.findByPublicReference(command.channelBookingReference().trim());
            if (bookingOpt.isPresent()) {
                OtaChannelBooking booking = bookingOpt.get();
                String bookingLoc = booking.getMapping().getLocationReference();
                if (!command.locationReference().trim().equals(bookingLoc)) {
                    throw new OtaLocationScopeException("Location reference " + command.locationReference()
                            + " does not match booking location " + bookingLoc);
                }
            }
        }

        String correlation = audit.safeCorrelationReference(command.correlationReference());

        Optional<OtaReconciliationWorkItem> existing = workItems.findByAuditReference(correlation);
        if (existing.isPresent()) {
            return new IncidentOutcome(existing.get().getStatus(), existing.get().getPublicReference(), correlation);
        }

        OtaReconciliationWorkItem item = workItems.save(OtaReconciliationWorkItem.builder()
                .locationReference(command.locationReference().trim())
                .category("SHARED_VOUCHER_INCIDENT")
                .status("OPEN")
                .assignedManagerReference(actor.accountId().toString())
                .resolutionReference("BOOKING=" + command.channelBookingReference().trim()
                        + ";EVIDENCE=" + command.evidenceReference().trim()
                        + ";REASON=" + command.incidentReason().trim())
                .auditReference(correlation)
                .build());

        audit.record(OtaAuditCorrelationService.IntegrationAction.RECONCILIATION_REQUIRED,
                null, null, correlation, OtaAuditCorrelationService.IntegrationOutcome.ACCEPTED);

        return new IncidentOutcome("OPEN", item.getPublicReference(), correlation);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
