package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingInvoiceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingPaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingPaymentAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.PaymentAttemptStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ApplyVerifiedDepositPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.DepositPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingDepositReconciliationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityCommitOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityReleaseOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SessionEntitlementProjection;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopBookingQuoteRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopBookingQuoteResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingInvoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.MemberQuoteBookingAcceptanceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingQuoteService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityHoldOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Plays a Member booking a workshop through FE-03's REAL quote and acceptance services. The test class declares
 * {@code @MockitoBean} for the two boundaries owned by other features, {@link WorkshopOperationsAdapter} (003.2:
 * package, sessions, capacity) and {@link WorkshopBookingAuthorizationService} (001 identity); this helper only
 * gives them a published package with one open session. Everything else is FE-03's and FE-06's own code.
 */
@TestComponent
public class RealWorkshopBookings {

    public static final long PACKAGE_ID = 9_772L;
    private static final long BRANCH_ID = 3L;

    private final WorkshopBookingQuoteService quotes;
    private final MemberQuoteBookingAcceptanceService acceptance;
    private final WorkshopOperationsAdapter operations;
    private final WorkshopBookingAuthorizationService authorization;
    private final WorkshopBookingRepository bookings;
    private final BookingInvoiceRepository invoices;
    private final LoyaltyWalletRepository wallets;
    private final PromotionProgramRepository programs;
    private final PromotionRevisionRepository revisions;
    private final PromotionTargetRepository targets;
    private final TransactionTemplate transaction;
    private final WorkshopBookingFacade bookingFacade;
    private final BookingPaymentAttemptRepository attempts;
    private final BookingDepositReconciliationService reconciliation;
    private UUID session;
    private Instant sessionStart;

    public RealWorkshopBookings(WorkshopBookingQuoteService quotes, MemberQuoteBookingAcceptanceService acceptance,
                                WorkshopOperationsAdapter operations, WorkshopBookingAuthorizationService authorization,
                                WorkshopBookingRepository bookings, BookingInvoiceRepository invoices,
                                LoyaltyWalletRepository wallets, PromotionProgramRepository programs,
                                PromotionRevisionRepository revisions, PromotionTargetRepository targets,
                                TransactionTemplate transaction, WorkshopBookingFacade bookingFacade,
                                BookingPaymentAttemptRepository attempts, BookingDepositReconciliationService reconciliation) {
        this.bookingFacade = bookingFacade;
        this.attempts = attempts;
        this.reconciliation = reconciliation;
        this.quotes = quotes;
        this.acceptance = acceptance;
        this.operations = operations;
        this.authorization = authorization;
        this.bookings = bookings;
        this.invoices = invoices;
        this.wallets = wallets;
        this.programs = programs;
        this.revisions = revisions;
        this.targets = targets;
        this.transaction = transaction;
    }

    /**
     * Pays the deposit through FE-03's REAL {@link WorkshopBookingFacade#applyVerifiedDepositPaymentEvidence}: the
     * booking is confirmed, the holds committed and loyalty settled by FE-03. The payment attempt row stands for the
     * payment-gateway feature (it is created there in production); 003.2 commits the holds and issues the tickets.
     */
    public DepositPaymentEvidenceOutcome payDeposit(WorkshopBooking booking) {
        try {
            return prepareDeposit(booking).call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Creates the payment attempt now and returns the verified-evidence call, to be run (also concurrently) later. */
    public Callable<DepositPaymentEvidenceOutcome> prepareDeposit(WorkshopBooking booking) {
        BookingInvoice invoice = invoice(booking.getBookingCode());
        String merchantReference = "M-" + UUID.randomUUID();
        transaction.executeWithoutResult(status -> attempts.saveAndFlush(BookingPaymentAttempt.builder()
                .booking(bookings.findByBookingCode(booking.getBookingCode()).orElseThrow())
                .invoice(invoices.findById(invoice.getId()).orElseThrow())
                .attemptReference(UUID.randomUUID().toString()).merchantReference(merchantReference)
                .providerCreateDate("20261101000000").attemptAmount(invoice.getDepositAmount())
                .status(PaymentAttemptStatus.PENDING).paymentUrlSnapshot("https://sandbox.example.test/pay")
                .expiresAt(Instant.now().plusSeconds(600)).createdAt(Instant.now()).updatedAt(Instant.now()).build()));
        when(operations.commitBookingHolds(eq(booking.getBookingCode()), anyList(), anyString(), any()))
                .thenReturn(CapacityCommitOutcome.accepted());
        when(operations.getBookingTicketProjection(booking.getBookingCode())).thenAnswer(call -> {
            List<DeliverySafeTicket> tickets = new ArrayList<>();
            for (int i = 0; i < booking.getParticipantCount(); i++) {
                tickets.add(new DeliverySafeTicket(UUID.randomUUID(), "QR-" + UUID.randomUUID(), "TK-" + UUID.randomUUID(),
                        List.of(new SessionEntitlementProjection(session, BRANCH_ID, sessionStart, sessionStart.plusSeconds(7_200)))));
            }
            return new DeliverySafeTicketSet(tickets);
        });
        return () -> bookingFacade.applyVerifiedDepositPaymentEvidence(new ApplyVerifiedDepositPaymentEvidenceCommand(
                booking.getBookingCode(), "ipn-" + booking.getBookingCode(), merchantReference, invoice.getDepositAmount(), "VND",
                "op-" + UUID.randomUUID(), "corr-" + UUID.randomUUID()));
    }

    /** Lets the hold run out and runs FE-03's REAL expiry reconciliation. */
    public void expireUnpaid(WorkshopBooking booking) {
        holdRunsOut(booking);
        reconciliation.reconcileDue();
    }

    /** Moves the capacity hold into the past; 003.2 accepts the release. */
    public void holdRunsOut(WorkshopBooking booking) {
        transaction.executeWithoutResult(status -> {
            WorkshopBooking locked = bookings.findByBookingCode(booking.getBookingCode()).orElseThrow();
            locked.setHoldExpiresAt(Instant.now().minusSeconds(60));
        });
        when(operations.releaseBookingHolds(eq(booking.getBookingCode()), anyList(), anyString(), any()))
                .thenReturn(CapacityReleaseOutcome.accepted());
    }

    /** FE-03's scheduled expiry/reconciliation pass, run once now. */
    public void runExpiryReconciliation() {
        reconciliation.reconcileDue();
    }

    /** A published package at the given price per participant with one open session; returns the session. */
    public UUID openSession(long pricePerParticipant) {
        UUID session = UUID.randomUUID();
        Instant start = Instant.now().plus(20, ChronoUnit.DAYS);
        this.session = session;
        this.sessionStart = start;
        when(operations.getPublishedPackageSelection(Long.toString(PACKAGE_ID))).thenReturn(new PublishedPackageSelectionSnapshot(
                Long.toString(PACKAGE_ID), pricePerParticipant, 1, 4, 1, false, 0, "Workshop nhẫn bạc"));
        when(operations.getSaleEligibility(session)).thenReturn(new SaleEligibilitySnapshot(session, true, "OPEN", BRANCH_ID,
                start, start.plusSeconds(7_200), 10, LocalDate.now().plusDays(20)));
        when(operations.reserveBookingCapacity(anyString(), eq(Long.toString(PACKAGE_ID)), anyList(), anyInt(), anyString(), any()))
                .thenAnswer(call -> CapacityHoldOutcome.accepted(List.of(UUID.randomUUID()), Instant.now().plusSeconds(900)));
        return session;
    }

    public WorkshopBookingQuoteResponse quote(long member, UUID session, int participants, String choice, String code, String points) {
        when(authorization.requireMemberOwnership(anyString(), isNull())).thenReturn(
                new AccountSummary(member, null, null, "member@example.test", Instant.now(), Instant.now()));
        return quotes.preview("Bearer test", new WorkshopBookingQuoteRequest(PACKAGE_ID, List.of(session.toString()), participants,
                new WorkshopBookingQuoteRequest.BenefitSelectionRequest(
                        WorkshopBookingQuoteRequest.BenefitSelectionRequest.Choice.valueOf(choice), code, points)));
    }

    /** Accepts the quote as the Member; returns the booking FE-03 stored. */
    public WorkshopBooking accept(long member, String quoteRef) {
        CreateBookingRequest request = CreateBookingRequest.builder().quoteRef(quoteRef)
                .contactEmail("member@example.test").contactPhone("0900000000").build();
        String code = acceptance.accept(request, member, "corr-" + UUID.randomUUID(), null).getBookingCode();
        return transaction.execute(status -> bookings.findByBookingCode(code).orElseThrow());
    }

    public WorkshopBooking reload(String bookingCode) {
        return transaction.execute(status -> bookings.findByBookingCode(bookingCode).orElseThrow());
    }

    public BookingInvoice invoice(String bookingCode) {
        return transaction.execute(status -> invoices.findByBookingId(bookings.findByBookingCode(bookingCode).orElseThrow().getId()).orElseThrow());
    }

    /** Fixed discount on the package, combinable with points, 10 uses, 1 per Member. */
    public String voucher(long amount) {
        String code = "WR" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.saveAndFlush(PromotionProgram.voucher(TargetScope.WORKSHOP, code, code, 10, 1, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1, ProgramKind.VOUCHER,
                    "Workshop source IT", now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                    DiscountTerms.fixed(BigInteger.valueOf(amount), BigInteger.valueOf(amount), BigInteger.ZERO), true, now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.WORKSHOP, TargetDimension.PACKAGE, Long.toString(PACKAGE_ID)));
            program.pointToRevision(revision.getId());
        });
        return code;
    }

    public PromotionProgram program(String code) {
        return transaction.execute(status -> programs.findByCodeKey(code).orElseThrow());
    }

    public long member(long points) {
        long id = ThreadLocalRandom.current().nextLong(100_000_000L, 900_000_000L);
        transaction.executeWithoutResult(status -> {
            LoyaltyWallet wallet = LoyaltyWallet.open(id);
            wallet.earn(BigInteger.valueOf(points));
            wallets.saveAndFlush(wallet);
        });
        return id;
    }

    public LoyaltyWallet wallet(long member) {
        return transaction.execute(status -> wallets.findById(member).orElseThrow());
    }
}
