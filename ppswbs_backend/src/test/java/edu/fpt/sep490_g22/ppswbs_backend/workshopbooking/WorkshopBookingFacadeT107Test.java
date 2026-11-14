package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.WorkshopBookingGroupController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingGroupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingGroupCreationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingValidationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BuyerVerificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 003.1 T107:
 * 1. WorkshopBookingFacade.getTicketFolioContext(ticketCode)
 * 2. WorkshopBookingFacade.consumeBuyerProof(ConsumeBuyerProofCommand)
 * 3. WorkshopBookingGroupController Idempotency-Key tightened to 16–128 characters.
 */
@ExtendWith(MockitoExtension.class)
class WorkshopBookingFacadeT107Test {

    @Nested
    @DisplayName("Ticket Folio Context")
    class TicketFolioContextTests {

        @Mock
        private TicketBookingRepository ticketBookingRepository;
        @Mock
        private BookingGroupLineRepository bookingGroupLineRepository;
        @InjectMocks
        private WorkshopBookingFacadeImpl facade;

        @Test
        @DisplayName("Unknown, blank or null ticketCode returns empty")
        void unknownOrBlankTicketCodeReturnsEmpty() {
            assertThat(facade.getTicketFolioContext(null)).isEmpty();
            assertThat(facade.getTicketFolioContext("")).isEmpty();
            assertThat(facade.getTicketFolioContext("   ")).isEmpty();

            when(ticketBookingRepository.findByTicketCode("TK-UNKNOWN")).thenReturn(Optional.empty());
            assertThat(facade.getTicketFolioContext("TK-UNKNOWN")).isEmpty();
            verifyNoInteractions(bookingGroupLineRepository);
        }

        @Test
        @DisplayName("Known ticket with lines and sessions returns full TicketFolioContext with sessions sorted by ordinal")
        void knownTicketReturnsTicketFolioContext() {
            BookingGroup group = BookingGroup.builder()
                    .id(10L)
                    .groupCode("GRP-20261114-001")
                    .buyerMemberAccountId(88L)
                    .build();

            TicketBooking ticket = TicketBooking.builder()
                    .id(100L)
                    .ticketCode("TK-20261114-001")
                    .group(group)
                    .lineIndex(0)
                    .packageId(456L)
                    .build();

            BookingGroupLineSession session2 = BookingGroupLineSession.builder()
                    .ordinal(2)
                    .sessionReference("SES-002")
                    .locationReference(15L)
                    .build();

            BookingGroupLineSession session1 = BookingGroupLineSession.builder()
                    .ordinal(1)
                    .sessionReference("SES-001")
                    .locationReference(15L)
                    .build();

            BookingGroupLine line0 = BookingGroupLine.builder()
                    .id(20L)
                    .lineIndex(0)
                    .lineReference("LINE-REF-456")
                    .packageId(456L)
                    .sessions(List.of(session2, session1)) // unordered in list to test sorting
                    .build();

            when(ticketBookingRepository.findByTicketCode("TK-20261114-001")).thenReturn(Optional.of(ticket));
            when(bookingGroupLineRepository.findByGroupIdOrderByLineIndexAsc(10L)).thenReturn(List.of(line0));

            Optional<TicketFolioContext> result = facade.getTicketFolioContext("TK-20261114-001");
            assertThat(result).isPresent();

            TicketFolioContext context = result.get();
            assertThat(context.ticketCode()).isEqualTo("TK-20261114-001");
            assertThat(context.groupCode()).isEqualTo("GRP-20261114-001");
            assertThat(context.lineReference()).isEqualTo("LINE-REF-456");
            assertThat(context.packageReference()).isEqualTo("456");
            assertThat(context.locationReference()).isEqualTo(15L);
            assertThat(context.sessionReferences()).containsExactly("SES-001", "SES-002");
            assertThat(context.buyerMemberAccountId()).isEqualTo(88L);
        }

        @Test
        @DisplayName("Guest ticket with no buyerMemberAccountId and empty sessions returns null location and empty sessions")
        void guestTicketWithEmptySessions() {
            BookingGroup group = BookingGroup.builder()
                    .id(11L)
                    .groupCode("GRP-GUEST-001")
                    .buyerMemberAccountId(null)
                    .build();

            TicketBooking ticket = TicketBooking.builder()
                    .id(101L)
                    .ticketCode("TK-GUEST-001")
                    .group(group)
                    .lineIndex(0)
                    .packageId(789L)
                    .build();

            BookingGroupLine line = BookingGroupLine.builder()
                    .id(21L)
                    .lineIndex(0)
                    .lineReference("LINE-REF-789")
                    .packageId(789L)
                    .sessions(List.of())
                    .build();

            when(ticketBookingRepository.findByTicketCode("TK-GUEST-001")).thenReturn(Optional.of(ticket));
            when(bookingGroupLineRepository.findByGroupIdOrderByLineIndexAsc(11L)).thenReturn(List.of(line));

            Optional<TicketFolioContext> result = facade.getTicketFolioContext("TK-GUEST-001");
            assertThat(result).isPresent();

            TicketFolioContext context = result.get();
            assertThat(context.ticketCode()).isEqualTo("TK-GUEST-001");
            assertThat(context.groupCode()).isEqualTo("GRP-GUEST-001");
            assertThat(context.lineReference()).isEqualTo("LINE-REF-789");
            assertThat(context.packageReference()).isEqualTo("789");
            assertThat(context.locationReference()).isNull();
            assertThat(context.sessionReferences()).isEmpty();
            assertThat(context.buyerMemberAccountId()).isNull();
        }

        @Test
        @DisplayName("Ticket whose line cannot be found returns empty")
        void ticketWithMissingLineReturnsEmpty() {
            BookingGroup group = BookingGroup.builder().id(12L).groupCode("GRP-002").build();
            TicketBooking ticket = TicketBooking.builder().ticketCode("TK-002").group(group).lineIndex(5).build();

            when(ticketBookingRepository.findByTicketCode("TK-002")).thenReturn(Optional.of(ticket));
            when(bookingGroupLineRepository.findByGroupIdOrderByLineIndexAsc(12L)).thenReturn(List.of());

            assertThat(facade.getTicketFolioContext("TK-002")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Buyer Proof Verification and Consumption")
    class BuyerProofConsumptionTests {

        @Mock
        private BookingGroupRepository groups;
        @Mock
        private BookingBuyerVerificationRepository verifications;
        @InjectMocks
        private BuyerVerificationService buyerVerificationService;

        @Test
        @DisplayName("Null or blank groupCode returns BUYER_VERIFICATION_REQUIRED")
        void nullOrBlankGroupCodeRefused() {
            ConsumeBuyerProofResult res1 = buyerVerificationService.consumeProof(null, 1L, "ref");
            assertThat(res1.isAccepted()).isFalse();
            assertThat(res1.errorCode()).isEqualTo(ConsumeBuyerProofResult.CODE_REQUIRED);

            ConsumeBuyerProofResult res2 = buyerVerificationService.consumeProof("  ", 1L, "ref");
            assertThat(res2.isAccepted()).isFalse();
            assertThat(res2.errorCode()).isEqualTo(ConsumeBuyerProofResult.CODE_REQUIRED);
        }

        @Test
        @DisplayName("Unknown group returns BUYER_VERIFICATION_REQUIRED without leaking existence")
        void unknownGroupRefused() {
            when(groups.findByGroupCodeForUpdate("GRP-UNKNOWN")).thenReturn(Optional.empty());

            ConsumeBuyerProofResult res = buyerVerificationService.consumeProof("GRP-UNKNOWN", 1L, "ref");
            assertThat(res.isAccepted()).isFalse();
            assertThat(res.errorCode()).isEqualTo(ConsumeBuyerProofResult.CODE_REQUIRED);
            verifyNoInteractions(verifications);
        }

        @Test
        @DisplayName("Member owning the group is ACCEPTED without requiring buyerVerificationReference")
        void memberOwningGroupAccepted() {
            BookingGroup group = BookingGroup.builder()
                    .id(1L)
                    .groupCode("GRP-OWNED")
                    .buyerMemberAccountId(42L)
                    .build();
            when(groups.findByGroupCodeForUpdate("GRP-OWNED")).thenReturn(Optional.of(group));

            ConsumeBuyerProofResult res = buyerVerificationService.consumeProof("GRP-OWNED", 42L, null);
            assertThat(res.isAccepted()).isTrue();
            assertThat(res.outcome()).isEqualTo(ConsumeBuyerProofResult.ACCEPTED);
            assertThat(res.errorCode()).isNull();
            verifyNoInteractions(verifications);
        }

        @Test
        @DisplayName("Guest group or non-owning Member without proof returns BUYER_VERIFICATION_REQUIRED")
        void missingProofRefused() {
            BookingGroup guestGroup = BookingGroup.builder()
                    .id(2L)
                    .groupCode("GRP-GUEST")
                    .buyerMemberAccountId(null)
                    .build();
            when(groups.findByGroupCodeForUpdate("GRP-GUEST")).thenReturn(Optional.of(guestGroup));

            ConsumeBuyerProofResult res1 = buyerVerificationService.consumeProof("GRP-GUEST", null, null);
            assertThat(res1.isAccepted()).isFalse();
            assertThat(res1.errorCode()).isEqualTo(ConsumeBuyerProofResult.CODE_REQUIRED);

            ConsumeBuyerProofResult res2 = buyerVerificationService.consumeProof("GRP-GUEST", 99L, "  ");
            assertThat(res2.isAccepted()).isFalse();
            assertThat(res2.errorCode()).isEqualTo(ConsumeBuyerProofResult.CODE_REQUIRED);

            BookingGroup memberGroup = BookingGroup.builder()
                    .id(3L)
                    .groupCode("GRP-MEMBER")
                    .buyerMemberAccountId(50L)
                    .build();
            when(groups.findByGroupCodeForUpdate("GRP-MEMBER")).thenReturn(Optional.of(memberGroup));

            // Member 99 is not owner 50
            ConsumeBuyerProofResult res3 = buyerVerificationService.consumeProof("GRP-MEMBER", 99L, null);
            assertThat(res3.isAccepted()).isFalse();
            assertThat(res3.errorCode()).isEqualTo(ConsumeBuyerProofResult.CODE_REQUIRED);
        }

        @Test
        @DisplayName("Unknown or foreign buyerVerificationReference returns BUYER_VERIFICATION_REQUIRED")
        void invalidOrForeignProofRefused() {
            BookingGroup groupA = BookingGroup.builder().id(10L).groupCode("GRP-A").build();
            BookingGroup groupB = BookingGroup.builder().id(20L).groupCode("GRP-B").build();

            when(groups.findByGroupCodeForUpdate("GRP-A")).thenReturn(Optional.of(groupA));
            when(verifications.findByBuyerVerificationReferenceForUpdate("REF-UNKNOWN")).thenReturn(Optional.empty());

            ConsumeBuyerProofResult res1 = buyerVerificationService.consumeProof("GRP-A", null, "REF-UNKNOWN");
            assertThat(res1.errorCode()).isEqualTo(ConsumeBuyerProofResult.CODE_REQUIRED);

            BookingBuyerVerification foreign = BookingBuyerVerification.builder()
                    .id(100L)
                    .group(groupB)
                    .status(BookingBuyerVerification.VERIFIED)
                    .build();
            when(verifications.findByBuyerVerificationReferenceForUpdate("REF-FOREIGN")).thenReturn(Optional.of(foreign));

            ConsumeBuyerProofResult res2 = buyerVerificationService.consumeProof("GRP-A", null, "REF-FOREIGN");
            assertThat(res2.errorCode()).isEqualTo(ConsumeBuyerProofResult.CODE_REQUIRED);
        }

        @Test
        @DisplayName("Locked verification returns BUYER_VERIFICATION_FAILED")
        void lockedVerificationRefused() {
            BookingGroup group = BookingGroup.builder().id(1L).groupCode("GRP-1").build();
            BookingBuyerVerification locked = BookingBuyerVerification.builder()
                    .id(101L)
                    .group(group)
                    .status(BookingBuyerVerification.LOCKED)
                    .build();

            when(groups.findByGroupCodeForUpdate("GRP-1")).thenReturn(Optional.of(group));
            when(verifications.findByBuyerVerificationReferenceForUpdate("REF-LOCKED")).thenReturn(Optional.of(locked));

            ConsumeBuyerProofResult res = buyerVerificationService.consumeProof("GRP-1", null, "REF-LOCKED");
            assertThat(res.isAccepted()).isFalse();
            assertThat(res.errorCode()).isEqualTo(ConsumeBuyerProofResult.CODE_FAILED);
        }

        @Test
        @DisplayName("Expired or already consumed verification returns BUYER_VERIFICATION_EXPIRED")
        void expiredOrConsumedVerificationRefused() {
            BookingGroup group = BookingGroup.builder().id(1L).groupCode("GRP-1").build();

            // Already consumed
            BookingBuyerVerification consumed = BookingBuyerVerification.builder()
                    .id(102L)
                    .group(group)
                    .status(BookingBuyerVerification.CONSUMED)
                    .referenceExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                    .build();

            when(groups.findByGroupCodeForUpdate("GRP-1")).thenReturn(Optional.of(group));
            when(verifications.findByBuyerVerificationReferenceForUpdate("REF-CONSUMED")).thenReturn(Optional.of(consumed));

            ConsumeBuyerProofResult res1 = buyerVerificationService.consumeProof("GRP-1", null, "REF-CONSUMED");
            assertThat(res1.errorCode()).isEqualTo(ConsumeBuyerProofResult.CODE_EXPIRED);

            // Expired timestamp
            BookingBuyerVerification expired = BookingBuyerVerification.builder()
                    .id(103L)
                    .group(group)
                    .status(BookingBuyerVerification.VERIFIED)
                    .referenceExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                    .build();
            when(verifications.findByBuyerVerificationReferenceForUpdate("REF-EXPIRED")).thenReturn(Optional.of(expired));

            ConsumeBuyerProofResult res2 = buyerVerificationService.consumeProof("GRP-1", null, "REF-EXPIRED");
            assertThat(res2.errorCode()).isEqualTo(ConsumeBuyerProofResult.CODE_EXPIRED);
        }

        @Test
        @DisplayName("Valid buyerVerificationReference is ACCEPTED and transitioned to CONSUMED")
        void validProofAcceptedAndConsumed() {
            BookingGroup group = BookingGroup.builder().id(1L).groupCode("GRP-1").build();
            BookingBuyerVerification valid = BookingBuyerVerification.builder()
                    .id(104L)
                    .group(group)
                    .status(BookingBuyerVerification.VERIFIED)
                    .referenceExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                    .build();

            when(groups.findByGroupCodeForUpdate("GRP-1")).thenReturn(Optional.of(group));
            when(verifications.findByBuyerVerificationReferenceForUpdate("REF-VALID")).thenReturn(Optional.of(valid));

            ConsumeBuyerProofResult res = buyerVerificationService.consumeProof("GRP-1", null, "REF-VALID");
            assertThat(res.isAccepted()).isTrue();
            assertThat(res.outcome()).isEqualTo(ConsumeBuyerProofResult.ACCEPTED);
            assertThat(res.errorCode()).isNull();

            assertThat(valid.getStatus()).isEqualTo(BookingBuyerVerification.CONSUMED);
            assertThat(valid.getConsumedAt()).isNotNull();
            verify(verifications).save(valid);
        }
    }

    @Nested
    @DisplayName("WorkshopBookingFacade consumeBuyerProof delegation")
    class FacadeConsumeBuyerProofTests {

        @Mock
        private BuyerVerificationService buyerVerificationService;
        @InjectMocks
        private WorkshopBookingFacadeImpl facade;

        @Test
        @DisplayName("Null command returns BUYER_VERIFICATION_REQUIRED without calling service")
        void nullCommandReturnsRequired() {
            ConsumeBuyerProofResult res = facade.consumeBuyerProof(null);
            assertThat(res.isAccepted()).isFalse();
            assertThat(res.errorCode()).isEqualTo(ConsumeBuyerProofResult.CODE_REQUIRED);
            verifyNoInteractions(buyerVerificationService);
        }

        @Test
        @DisplayName("Valid command delegates to BuyerVerificationService")
        void commandDelegatesToService() {
            ConsumeBuyerProofCommand cmd = new ConsumeBuyerProofCommand("GRP-1", 42L, "PROOF-REF");
            when(buyerVerificationService.consumeProof("GRP-1", 42L, "PROOF-REF"))
                    .thenReturn(ConsumeBuyerProofResult.accepted());

            ConsumeBuyerProofResult res = facade.consumeBuyerProof(cmd);
            assertThat(res.isAccepted()).isTrue();
            verify(buyerVerificationService).consumeProof("GRP-1", 42L, "PROOF-REF");
        }
    }

    @Nested
    @DisplayName("WorkshopBookingGroupController Idempotency-Key validation (16–128 characters)")
    class ControllerIdempotencyValidationTests {

        private WorkshopBookingGroupController controller;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            BookingGroupCreationService creationService = mock(BookingGroupCreationService.class);
            BookingIdempotencyService idempotency = mock(BookingIdempotencyService.class);
            ObjectMapper mapper = new ObjectMapper();
            controller = new WorkshopBookingGroupController(creationService, idempotency, mapper);
        }

        @Test
        @DisplayName("Keys shorter than 16 characters are refused with 16 to 128 message")
        void keysShorterThan16Refused() {
            CreateBookingGroupRequest request = new CreateBookingGroupRequest(
                    "QR-001", "customer@example.com", "0901234567");

            assertThatThrownBy(() -> controller.create(null, null, null, request))
                    .isInstanceOf(BookingValidationException.class)
                    .hasMessage("Idempotency-Key must contain 16 to 128 characters");

            assertThatThrownBy(() -> controller.create("", null, null, request))
                    .isInstanceOf(BookingValidationException.class)
                    .hasMessage("Idempotency-Key must contain 16 to 128 characters");

            assertThatThrownBy(() -> controller.create("   ", null, null, request))
                    .isInstanceOf(BookingValidationException.class)
                    .hasMessage("Idempotency-Key must contain 16 to 128 characters");

            // 15 characters
            assertThatThrownBy(() -> controller.create("123456789012345", null, null, request))
                    .isInstanceOf(BookingValidationException.class)
                    .hasMessage("Idempotency-Key must contain 16 to 128 characters");
        }

        @Test
        @DisplayName("Keys longer than 128 characters are refused with 16 to 128 message")
        void keysLongerThan128Refused() {
            CreateBookingGroupRequest request = new CreateBookingGroupRequest(
                    "QR-001", "customer@example.com", "0901234567");

            String key129 = "a".repeat(129);
            assertThatThrownBy(() -> controller.create(key129, null, null, request))
                    .isInstanceOf(BookingValidationException.class)
                    .hasMessage("Idempotency-Key must contain 16 to 128 characters");
        }
    }
}

