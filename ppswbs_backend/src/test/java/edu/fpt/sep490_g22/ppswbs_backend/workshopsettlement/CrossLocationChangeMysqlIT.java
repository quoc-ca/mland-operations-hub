package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TicketVoucherScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConsumeBuyerProofResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.FinalBalanceResolutionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.EquivalentReplacementSessions;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.RejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReplaceTicketSessionsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.TicketSessionReplacementOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementBusinessAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketChangeRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketMlandRemedyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.FolioCollectionDtos.CashCloseRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestDecision;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeRequestDecisionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.ChangeResultDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketChangeDtos.TicketChangeType;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketRemedyDtos.TicketRemedyAnswer;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketRemedyDtos.TicketRemedyAnswerRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFolioCollection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.CashDayCloseService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCollectionApiService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FolioCollectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.RefundAccountCipher;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeRequestService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketChangeService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketMlandRemedyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementConflictException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 003.3 T191 (FR-059, FR-060, FR-061; SC-021, SC-022): MySQL acceptance of changing a ticket to another location. The
 * settlement services and repositories are real and run on the Flyway-built schema of the
 * {@code workshopoperations-mysql-test} profile; the 003.1 and 003.2 facades are mocked, so the 003.2 rules (a
 * two-session package that must stay in one location, a pair that is INACTIVE at a location) are <b>answered by the
 * mock here</b>. Their real MySQL evidence is {@code FacadeOperationsMySqlIT} (P18) and
 * {@code WorkshopPackageAvailabilityIT}; the 003.1 move of the ticket's location is {@code TicketFolioContextMoveMySqlIT}.
 */
@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {
        "vnpay.merchant-api-ip-address=127.0.0.1",
        "vnpay.merchant-api-created-by=it-merchant",
        "workshop.operations.visible-location-ids="})
class CrossLocationChangeMysqlIT {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final CurrentAccountActor MANAGER =
            new CurrentAccountActor(300L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private static final CurrentAccountActor BUYER =
            new CurrentAccountActor(100L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of());

    @MockitoBean PaymentGatewayCheckoutPort checkout;
    @MockitoBean VnpayMerchantOperationsPort merchantOperations;
    @MockitoBean WorkshopOperationsFacade operationsFacade;
    @MockitoBean LoyaltyFacade loyalty;
    @MockitoBean WorkshopBookingFacade bookingFacade;
    @MockitoBean edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade bookingNotifications;

    @Autowired SettlementFinalBillRepository bills;
    @Autowired SettlementFolioCollectionRepository collections;
    @Autowired SettlementTicketChangeRepository changes;
    @Autowired SettlementTicketChangeRequestRepository changeRequests;
    @Autowired SettlementTicketMlandRemedyRepository remedies;
    @Autowired SettlementBusinessAuditRepository audits;
    @Autowired FolioCollectionService collectionService;
    @Autowired FolioCollectionApiService collectionApi;
    @Autowired CashDayCloseService cashClose;
    @Autowired TicketChangeService changeService;
    @Autowired TicketChangeRequestService changeRequestService;
    @Autowired TicketMlandRemedyService remedyService;

    @BeforeAll
    static void refundAccountKey() {
        ReflectionTestUtils.invokeMethod(RefundAccountCipher.class, "useKeyForTests",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }

    @BeforeEach
    void commonStubs() {
        doReturn(FinalBalanceResolutionOutcome.accepted()).when(bookingFacade).recordTicketFinalBalanceResolved(any());
        doReturn(ConsumeBuyerProofResult.accepted()).when(bookingFacade).consumeBuyerProof(any());
        when(checkout.createFinalPaymentUrl(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), any(), any(),
                anyString())).thenReturn("https://pay.example.invalid/checkout");
        when(loyalty.checkTicketVoucherScope(any())).thenReturn(new TicketVoucherScope(true, false, BigInteger.ZERO));
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static String unique(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }

    private static String key() {
        return "it-xloc-" + UUID.randomUUID();
    }

    /** Branch ids far above the seeded locations, so the shared test schema never mixes with real rows. */
    private static long branch() {
        return 9_300_000L + SEQ.incrementAndGet() * 2;
    }

    private void folio(String ticket, String group, long deposit, long remaining) {
        bills.save(SettlementFinalBill.builder().billReference("BILL-" + ticket).bookingReference(ticket)
                .groupReference(group).postPromotionAmountVnd(deposit + remaining).acceptedDepositAmountVnd(deposit)
                .remainingAmountVnd(remaining).currencyCode("VND").lifecycleStatus("OPEN").build());
    }

    /** What 003.1 {@code getTicketFolioContext} answers now: the location follows the ticket's current session. */
    private void ticketAt(String ticket, String group, long location, UUID opsRef, List<String> sessions) {
        doReturn(Optional.of(new TicketFolioContext(ticket, group, group + "-L1", "7", location, sessions, 100L,
                opsRef.toString(), Instant.now().plus(72, ChronoUnit.HOURS), 0L, "L1-" + ticket, 0L)))
                .when(bookingFacade).getTicketFolioContext(ticket);
    }

    private void targetsAt(long location) {
        Instant start = Instant.now().plus(96, ChronoUnit.HOURS);
        when(operationsFacade.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(UUID.randomUUID(), true,
                "ACTIVE", location, start, start.plus(2, ChronoUnit.HOURS), 5, LocalDate.now().plusDays(4)));
    }

    private List<SettlementFolioCollection> collectionsOf(String ticket) {
        return collections.findByBookingReferenceOrderByIdAsc(ticket);
    }

    // ---- tests ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("T191/FR-059: a two-session ticket moves both sessions together to another location with no Manager; the audit records both ids; a lone session is refused as the facade answers, nothing counted")
    void bothSessionsMoveTogether_loneSessionPassedOnUnchanged() {
        String group = unique("G-XL-");
        String ticket = unique("T-XL-");
        long from = branch();
        long to = from + 1;
        UUID ops = UUID.randomUUID();
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        folio(ticket, group, 100_000, 100_000);
        ticketAt(ticket, group, from, ops, List.of(f1.toString(), f2.toString()));
        targetsAt(to);

        // One session alone to another location: 003.2 answers INVALID_REPLACEMENT (real rule: FacadeOperationsMySqlIT P18).
        when(operationsFacade.replaceTicketSessions(any())).thenReturn(
                TicketSessionReplacementOutcome.rejected(RejectionReason.INVALID_REPLACEMENT, List.of(0)));
        assertThatThrownBy(() -> changeService.changeTicketSessions(ticket, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(t1), List.of(f1), null, null, null), key(), "CORR-XL-LONE", BUYER))
                .hasMessageContaining("INVALID_REPLACEMENT");
        assertThat(changes.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket)).isZero();

        // Both sessions together: accepted, one counted change, no queued request.
        when(operationsFacade.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
        String corr = "CORR-XL-" + SEQ.incrementAndGet();
        ChangeResultDto result = changeService.changeTicketSessions(ticket, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(t1, t2), List.of(f1, f2), null, null, null), key(), corr, BUYER);

        assertThat(result.applied()).isTrue();
        assertThat(result.changeRequestReference()).as("no Manager needed for another location").isNull();
        assertThat(changes.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket)).isEqualTo(1L);
        ArgumentCaptor<ReplaceTicketSessionsCommand> command = ArgumentCaptor.forClass(ReplaceTicketSessionsCommand.class);
        verify(operationsFacade, atLeastOnce()).replaceTicketSessions(command.capture());
        ReplaceTicketSessionsCommand last = command.getValue();
        assertThat(last.lines()).hasSize(1);
        assertThat(last.lines().get(0).targetSessionReferences()).containsExactly(t1, t2);
        assertThat(audits.findAll()).filteredOn(a -> corr.equals(a.getCorrelationReference())
                        && "TICKET_LOCATION_CHANGED".equals(a.getActionCode()))
                .isNotEmpty()
                .allSatisfy(a -> assertThat(a.getReasonReference()).isEqualTo("from=" + from + ";to=" + to));
    }

    @Test
    @DisplayName("T191/FR-060: collections before the move keep branch A, later ones take branch B; the cash book of each branch counts its own cash")
    void collectionsAndCashCloseFollowTheLocationOfTheMoment() {
        String group = unique("G-ATTR-");
        String ticket = unique("T-ATTR-");
        long a = branch();
        long b = a + 1;
        UUID ops = UUID.randomUUID();
        folio(ticket, group, 0, 400_000);
        List<String> sessions = List.of(UUID.randomUUID().toString());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        ticketAt(ticket, group, a, ops, sessions);
        collectionService.recordBankTransfer(ticket, 100_000L, "TXN-A-" + ticket, "payer", 300L, "CORR");
        collectionService.collectCash(ticket, 60_000L, 60_000L, "RCPT-A-" + ticket, "payer", a, 300L, "CORR");

        // 003.1 now reads the new current session: the ticket belongs to B.
        ticketAt(ticket, group, b, ops, sessions);
        collectionService.recordBankTransfer(ticket, 50_000L, "TXN-B-" + ticket, "payer", 300L, "CORR");
        collectionService.collectCash(ticket, 40_000L, 40_000L, "RCPT-B-" + ticket, "payer", b, 300L, "CORR");

        List<SettlementFolioCollection> rows = collectionsOf(ticket);
        assertThat(rows).hasSize(4);
        assertThat(rows).filteredOn(r -> r.getExternalReference() != null && r.getExternalReference().startsWith("TXN-A-"))
                .singleElement().satisfies(r -> assertThat(r.getBranchId()).as("before the move").isEqualTo(a));
        assertThat(rows).filteredOn(r -> r.getExternalReference() != null && r.getExternalReference().startsWith("TXN-B-"))
                .singleElement().satisfies(r -> assertThat(r.getBranchId()).as("after the move").isEqualTo(b));
        assertThat(cashClose.expectedCash(a, today)).isEqualTo(60_000L);
        assertThat(cashClose.expectedCash(b, today)).isEqualTo(40_000L);

        // Closing A's day leaves B's open and does not move A's old money.
        collectionApi.closeCashDay(new CashCloseRequest(a, today, "60000", null), key(), "CORR", MANAGER);
        assertThat(cashClose.expectedCash(b, today)).isEqualTo(40_000L);
        assertThat(collectionsOf(ticket)).filteredOn(r -> r.getBranchId() != null && r.getBranchId() == a).hasSize(2);
    }

    @Test
    @DisplayName("T191/FR-059(d): a pair INACTIVE at the target is refused at commit and again when an older queued request is approved; the request stays unapplied and no allowance is used")
    void inactivePairRefusedAtCommitAndAtApproval() {
        String group = unique("G-INACT-");
        String ticket = unique("T-INACT-");
        long from = branch();
        UUID ops = UUID.randomUUID();
        UUID f1 = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        folio(ticket, group, 100_000, 100_000);
        ticketAt(ticket, group, from, ops, List.of(f1.toString()));
        targetsAt(from + 1);

        // At commit.
        when(operationsFacade.replaceTicketSessions(any())).thenReturn(
                TicketSessionReplacementOutcome.rejected(RejectionReason.PACKAGE_NOT_AVAILABLE_AT_LOCATION, List.of(0)));
        assertThatThrownBy(() -> changeService.changeTicketSessions(ticket, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(target), List.of(f1), null, null, null), key(), "CORR-INACT-1", BUYER))
                .isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessageContaining("PACKAGE_NOT_AVAILABLE_AT_LOCATION");
        assertThat(changes.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket)).isZero();

        // Use the ticket's one change, then queue a second request for a Manager (the pair was ACTIVE when it was queued).
        when(operationsFacade.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
        assertThat(changeService.changeTicketSessions(ticket, new TicketChangeRequestDto(TicketChangeType.ADVANCE,
                List.of(target), List.of(f1), null, null, null), key(), "CORR-INACT-2", BUYER).applied()).isTrue();
        ChangeResultDto queued = changeService.changeTicketSessions(ticket, new TicketChangeRequestDto(
                TicketChangeType.ADVANCE, List.of(target), List.of(f1), null, null, null), key(), "CORR-INACT-3", BUYER);
        assertThat(queued.changeRequestReference()).isNotBlank();
        long countedBefore = changes.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket);

        // The Manager approves after the pair was set INACTIVE.
        when(operationsFacade.replaceTicketSessions(any())).thenReturn(
                TicketSessionReplacementOutcome.rejected(RejectionReason.PACKAGE_NOT_AVAILABLE_AT_LOCATION, List.of(0)));
        assertThatThrownBy(() -> changeRequestService.decide(queued.changeRequestReference(),
                new ChangeRequestDecisionDto(ChangeRequestDecision.APPROVE, "Customer called the shop"), key(), "CORR-INACT-4", MANAGER))
                .hasMessageContaining("PACKAGE_NOT_AVAILABLE_AT_LOCATION");

        assertThat(changeRequests.findByRequestReference(queued.changeRequestReference()).orElseThrow().getStatus())
                .as("left unapplied").isEqualTo("PENDING");
        assertThat(changes.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket)).as("no allowance used").isEqualTo(countedBefore);
    }

    @Test
    @DisplayName("T191/FR-061: a Mland offer lists candidates at two locations with names; the buyer's pick at another location moves the ticket with no Manager; a candidate turned INACTIVE is refused and the offer stays open")
    void mlandOfferAcrossLocations() {
        String group = unique("G-ML-");
        String ticket = unique("T-ML-");
        String second = unique("T-ML-2-");
        long locA = branch();
        long locB = locA + 1;
        UUID ops = UUID.randomUUID();
        UUID opsSecond = UUID.randomUUID();
        UUID sessionA = UUID.randomUUID();
        UUID sessionB = UUID.randomUUID();
        Instant startA = Instant.now().plus(60, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        Instant startB = Instant.now().plus(100, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        for (String code : List.of(ticket, second)) {
            folio(code, group, 40_000, 0);
            ticketAt(code, group, locA, code.equals(ticket) ? ops : opsSecond, List.of(UUID.randomUUID().toString()));
        }
        var optionA = new TicketMlandRemedyService.ReplacementOption(sessionA.toString(), startA, startA.plus(2, ChronoUnit.HOURS), locA, "Hà Nội");
        var optionB = new TicketMlandRemedyService.ReplacementOption(sessionB.toString(), startB, startB.plus(2, ChronoUnit.HOURS), locB, "Đà Nẵng");
        remedyService.createOffer(ticket, "cancelled-" + ticket, List.of(optionA, optionB), "CORR-ML");
        remedyService.createOffer(second, "cancelled-" + second, List.of(optionA, optionB), "CORR-ML");

        var offer = remedyService.getOffer(ticket, BUYER);
        assertThat(offer.offeredSessions()).extracting(o -> o.locationName()).containsExactly("Hà Nội", "Đà Nẵng");
        assertThat(offer.offeredSessions()).extracting(o -> o.locationReference()).containsExactly(locA, locB);

        // 003.2 still offers both at answer time: choosing B moves the ticket, no Manager, no change count.
        when(operationsFacade.findEquivalentReplacementSessions(any())).thenReturn(new EquivalentReplacementSessions(List.of(
                new EquivalentReplacementSessions.Candidate(sessionA, startA, startA.plus(2, ChronoUnit.HOURS), locA, "Hà Nội"),
                new EquivalentReplacementSessions.Candidate(sessionB, startB, startB.plus(2, ChronoUnit.HOURS), locB, "Đà Nẵng"))));
        when(operationsFacade.replaceTicketSessions(any())).thenReturn(TicketSessionReplacementOutcome.accepted());
        var moved = remedyService.answerOffer(ticket, new TicketRemedyAnswerRequestDto(
                TicketRemedyAnswer.ACCEPT_REPLACEMENT, null, sessionB.toString()), key(), "CORR-ML", BUYER);
        assertThat(moved.outcome()).isEqualTo("MOVED");
        assertThat(moved.selectedReplacementSessionReference()).isEqualTo(sessionB.toString());
        assertThat(changes.countByTicketCodeAndCountedTrueAndAppliedTrue(ticket)).as("Mland replacement is not a counted change").isZero();
        assertThat(changeRequests.findAll()).noneMatch(r -> ticket.equals(r.getTicketCode()));

        // For the second ticket B turned INACTIVE before the answer: 003.2 rejects the replacement at the commit, the buyer
        // gets REPLACEMENT_SESSION_UNAVAILABLE (like a lost last seat) and the offer stays open.
        when(operationsFacade.replaceTicketSessions(any())).thenReturn(
                TicketSessionReplacementOutcome.rejected(RejectionReason.PACKAGE_NOT_AVAILABLE_AT_LOCATION, List.of(0)));
        assertThatThrownBy(() -> remedyService.answerOffer(second, new TicketRemedyAnswerRequestDto(
                TicketRemedyAnswer.ACCEPT_REPLACEMENT, null, sessionB.toString()), key(), "CORR-ML", BUYER))
                .hasMessageContaining("REPLACEMENT_SESSION_UNAVAILABLE");
        assertThat(remedies.findByTicketCodeAndOutcome(second, "PENDING_ANSWER")).isPresent();
    }
}
