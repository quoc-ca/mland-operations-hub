package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingAuditEvidenceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingInvoiceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingNotificationIntentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingSessionCommitmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * T002 & T007 & T014: Named implementation of WorkshopBookingFacade.
 * Implements the cross-feature contract boundary.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopBookingFacadeImpl implements WorkshopBookingFacade {

    private final WorkshopBookingRepository bookingRepository;
    private final BookingInvoiceRepository invoiceRepository;
    private final BookingSessionCommitmentRepository commitmentRepository;
    private final BookingNotificationIntentRepository notificationIntentRepository;
    private final BookingAuditEvidenceRepository auditEvidenceRepository;
    private final BookingLifecycleService bookingLifecycleService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public ConfirmedBookingFinancialProjection getConfirmedBookingFinancialProjection(ConfirmedBookingFinancialProjectionQuery query) {
        log.info("WorkshopBookingFacade.getConfirmedBookingFinancialProjection: bookingReference={}", query.bookingReference());
        Optional<WorkshopBooking> optBooking = bookingRepository.findByBookingCode(query.bookingReference());
        if (optBooking.isEmpty()) {
            return null;
        }

        WorkshopBooking booking = optBooking.get();
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            return null;
        }

        Optional<BookingInvoice> optInvoice = invoiceRepository.findByBookingId(booking.getId());
        if (optInvoice.isEmpty()) {
            return null;
        }
        BookingInvoice invoice = optInvoice.get();

        List<BookingSessionCommitment> commitments = commitmentRepository.findByBookingIdOrderByOrdinalAsc(booking.getId());
        List<String> sessionRefs = commitments.stream()
                .map(c -> String.valueOf(c.getSessionId()))
                .toList();

        Long locationId = commitments.isEmpty() ? 1L : commitments.get(0).getLocationId();

        return new ConfirmedBookingFinancialProjection(
                String.valueOf(booking.getPackageId()),
                invoice.getPricePerParticipant() != null ? invoice.getPricePerParticipant() * booking.getParticipantCount() : invoice.getPackageTotal(),
                invoice.getPostPromotionAmount(),
                invoice.getDepositAmount(),
                invoice.getRemainingAmount(),
                booking.getParticipantCount(),
                sessionRefs,
                locationId
        );
    }

    @Override
    @Transactional
    public FinalBalanceResolutionOutcome recordFinalBalanceResolved(RecordFinalBalanceResolvedCommand command) {
        log.info("WorkshopBookingFacade.recordFinalBalanceResolved: bookingReference={}, settlementReference={}, opRef={}",
                command.bookingReference(), command.settlementReference(), command.operationReference());

        Optional<WorkshopBooking> optBooking = bookingRepository.findByBookingCode(command.bookingReference());
        if (optBooking.isEmpty()) {
            return FinalBalanceResolutionOutcome.rejected(BookingRejectionReason.NOT_READY);
        }

        WorkshopBooking booking = optBooking.get();
        if (booking.getPaymentStatus() == PaymentStatus.FULLY_PAID) {
            return FinalBalanceResolutionOutcome.accepted();
        }

        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            return FinalBalanceResolutionOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }

        booking.setPaymentStatus(PaymentStatus.FULLY_PAID);
        bookingRepository.save(booking);

        BookingAuditEvidence audit = BookingAuditEvidence.builder()
                .bookingCode(booking.getBookingCode())
                .locationId(1L)
                .actorType("SETTLEMENT")
                .action("FINAL_BALANCE_RESOLVED")
                .outcome("ACCEPTED")
                .correlationReference(command.correlationReference())
                .occurredAt(Instant.now())
                .build();
        auditEvidenceRepository.save(audit);

        return FinalBalanceResolutionOutcome.accepted();
    }

    @Override
    @Transactional
    public DepositPaymentEvidenceOutcome applyVerifiedDepositPaymentEvidence(ApplyVerifiedDepositPaymentEvidenceCommand command) {
        log.info("WorkshopBookingFacade.applyVerifiedDepositPaymentEvidence: bookingReference={}, evidenceRef={}, opRef={}",
                command.bookingReference(), command.verifiedEvidenceReference(), command.operationReference());

        Optional<WorkshopBooking> optBooking = bookingRepository.findByBookingCode(command.bookingReference());
        if (optBooking.isEmpty()) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY);
        }

        WorkshopBooking booking = optBooking.get();
        if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            return DepositPaymentEvidenceOutcome.accepted();
        }

        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }

        if (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt().isBefore(Instant.now())) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.HOLD_MISSING_OR_EXPIRED);
        }

        Optional<BookingInvoice> optInvoice = invoiceRepository.findByBookingId(booking.getId());
        if (optInvoice.isEmpty()) {
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.NOT_READY);
        }

        try {
            bookingLifecycleService.confirmBookingPaymentAndCommitHolds(
                    booking.getBookingCode(),
                    command.verifiedEvidenceReference(),
                    "VERIFIED_EVIDENCE",
                    optInvoice.get().getDepositAmount(),
                    "PAYLOAD_HASH_" + command.verifiedEvidenceReference(),
                    command.operationReference(),
                    command.correlationReference()
            );
            return DepositPaymentEvidenceOutcome.accepted();
        } catch (Exception e) {
            log.warn("Failed to apply deposit payment evidence for booking {}: {}", command.bookingReference(), e.getMessage());
            return DepositPaymentEvidenceOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }
    }

    @Override
    @Transactional
    public TicketRedeliveryOutcome requestBookingTicketRedelivery(RequestBookingTicketRedeliveryCommand command) {
        log.info("WorkshopBookingFacade.requestBookingTicketRedelivery: bookingReference={}, ticketsCount={}, opRef={}",
                command.bookingReference(), command.activeUnusedTicketReferences().size(), command.operationReference());

        Optional<WorkshopBooking> optBooking = bookingRepository.findByBookingCode(command.bookingReference());
        if (optBooking.isEmpty()) {
            return TicketRedeliveryOutcome.rejected(BookingRejectionReason.NOT_READY);
        }

        WorkshopBooking booking = optBooking.get();
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            return TicketRedeliveryOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }

        try {
            bookingLifecycleService.requestTicketRedelivery(
                    booking.getBookingCode(),
                    command.activeUnusedTicketReferences(),
                    command.operationReference(),
                    command.correlationReference()
            );
            return TicketRedeliveryOutcome.accepted();
        } catch (Exception e) {
            log.warn("Failed to request ticket redelivery for booking {}: {}", command.bookingReference(), e.getMessage());
            return TicketRedeliveryOutcome.rejected(BookingRejectionReason.INVALID_STATE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BookingNotificationDeliveryPayload getBookingNotificationDeliveryPayload(BookingNotificationDeliveryPayloadQuery query) {
        log.info("WorkshopBookingFacade.getBookingNotificationDeliveryPayload: notificationReference={}", query.notificationReference());

        Optional<BookingNotificationIntent> optIntent = notificationIntentRepository.findByNotificationReference(query.notificationReference());
        if (optIntent.isEmpty()) {
            return null;
        }

        BookingNotificationIntent intent = optIntent.get();
        if (intent.getDeliverySnapshot() == null || intent.getDeliverySnapshot().isBlank()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(intent.getDeliverySnapshot());
            // Hibernate/H2 may expose a JSON-typed String column as a JSON string
            // containing the serialized object. Accept that representation while
            // continuing to read object-valued JSON columns directly.
            if (node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }
            String kind = node.path("notificationKind").asText(intent.getNotificationKind());
            String recipient = node.path("storedRecipient").asText("");
            String content = node.path("renderedBookingContent").asText("");
            List<String> artifacts = new ArrayList<>();
            if (node.has("renderedTicketArtifacts") && node.get("renderedTicketArtifacts").isArray()) {
                for (JsonNode item : node.get("renderedTicketArtifacts")) {
                    artifacts.add(item.asText());
                }
            }
            String accessLink = node.path("bookingAccessLink").asText("");
            String correlationRef = node.path("correlationReference").asText(intent.getCorrelationReference());

            return new BookingNotificationDeliveryPayload(kind, recipient, content, artifacts, accessLink, correlationRef);
        } catch (Exception e) {
            log.error("Failed to parse delivery snapshot JSON for notificationReference={}", query.notificationReference(), e);
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingEligibilitySnapshot> getBookingEligibilityForDesign(String bookingCode, Long memberAccountId) {
        if (bookingCode == null || bookingCode.isBlank() || memberAccountId == null) {
            return Optional.empty();
        }
        return bookingRepository.findByBookingCode(bookingCode.trim())
                .filter(b -> memberAccountId.equals(b.getMemberAccountId()))
                .map(b -> {
                    boolean eligible = b.getBookingStatus() != BookingStatus.CANCELLED
                            && b.getBookingStatus() != BookingStatus.EXPIRED;
                    return BookingEligibilitySnapshot.builder()
                            .bookingId(b.getId())
                            .bookingCode(b.getBookingCode())
                            .memberAccountId(b.getMemberAccountId())
                            .packageId(b.getPackageId())
                            .packageName(b.getPackageNameSnapshot())
                            .bookingStatus(b.getBookingStatus().name())
                            .paymentStatus(b.getPaymentStatus().name())
                            .eligibleForDesign(eligible)
                            .build();
                });
    }
}
