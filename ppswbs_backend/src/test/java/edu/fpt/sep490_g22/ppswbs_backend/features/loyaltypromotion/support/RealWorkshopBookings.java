package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.AcceptedBenefitRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.WorkshopSessionFact;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingInvoiceRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MemberWorkshopBookingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingInvoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MemberWorkshopBookingQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ApplyVerifiedDepositPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.DepositPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.support.legacybooking.LegacyDirectBookings;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays a Member's single workshop booking against FE-06's REAL Loyalty facade. Since the 003.1 decision of
 * 2026-11-13 a direct booking can only be created as a booking group, so the single booking is seeded with
 * {@link LegacyDirectBookings}; the Loyalty side (preview, reservation, deposit settlement, unpaid expiry) runs
 * exactly as the removed single-booking services drove it. The single-booking source stays only as long as the
 * 003.3 booking-wide settlement it serves (open 003.3 decision of 2026-11-13).
 */
@TestComponent
public class RealWorkshopBookings {

    public static final long PACKAGE_ID = 9_772L;
    private static final long BRANCH_ID = 3L;

    /** The persisted quote reference a test confirms with {@link #accept}. */
    public record Quote(String quoteRef, BenefitPreview preview) {
    }

    private record PendingQuote(long member, UUID session, int participants, BenefitSelection selection,
                                TrustedSourceContext source, BenefitPreview preview) {
    }

    private final LoyaltyFacade loyalty;
    private final LegacyDirectBookings legacy;
    private final WorkshopBookingFacade bookingFacade;
    private final WorkshopBookingRepository bookings;
    private final BookingInvoiceRepository invoices;
    private final MemberWorkshopBookingQuoteRepository memberQuotes;
    private final LoyaltyWalletRepository wallets;
    private final PromotionProgramRepository programs;
    private final PromotionRevisionRepository revisions;
    private final PromotionTargetRepository targets;
    private final TransactionTemplate transaction;
    private final Map<String, PendingQuote> openQuotes = new ConcurrentHashMap<>();
    private long pricePerParticipant;
    private Instant sessionStart;

    public RealWorkshopBookings(LoyaltyFacade loyalty, LegacyDirectBookings legacy, WorkshopBookingFacade bookingFacade,
                                WorkshopBookingRepository bookings, BookingInvoiceRepository invoices,
                                MemberWorkshopBookingQuoteRepository memberQuotes, LoyaltyWalletRepository wallets,
                                PromotionProgramRepository programs, PromotionRevisionRepository revisions,
                                PromotionTargetRepository targets, TransactionTemplate transaction) {
        this.loyalty = loyalty;
        this.legacy = legacy;
        this.bookingFacade = bookingFacade;
        this.bookings = bookings;
        this.invoices = invoices;
        this.memberQuotes = memberQuotes;
        this.wallets = wallets;
        this.programs = programs;
        this.revisions = revisions;
        this.targets = targets;
        this.transaction = transaction;
    }

    /** A package at the given price per participant with one session 20 days ahead; returns the session. */
    public UUID openSession(long pricePerParticipant) {
        this.pricePerParticipant = pricePerParticipant;
        this.sessionStart = Instant.now().plus(20, ChronoUnit.DAYS);
        return UUID.randomUUID();
    }

    /** FE-06's real preview for the Member's selection; nothing is reserved yet. */
    public Quote quote(long member, UUID session, int participants, String choice, String code, String points) {
        BenefitSelection selection = new BenefitSelection(SelectionChoice.valueOf(choice), code, new BigInteger(points));
        TrustedSourceContext source = new TrustedSourceContext(SourceKind.WORKSHOP, member, Instant.now(), List.of(),
                new WorkshopFact(PACKAGE_ID, participants,
                        BigInteger.valueOf(Math.multiplyExact((long) participants, pricePerParticipant)),
                        List.of(WorkshopSessionFact.of(session.toString(), BRANCH_ID, LocalDate.now().plusDays(20)))),
                BigInteger.ZERO);
        BenefitPreview preview = loyalty.previewBenefits(source, selection);
        String quoteRef = UUID.randomUUID().toString();
        openQuotes.put(quoteRef, new PendingQuote(member, session, participants, selection, source, preview));
        return new Quote(quoteRef, preview);
    }

    /** Reserves the quote's benefits with FE-06 and stores the single booking it belongs to (PENDING_PAYMENT). */
    public WorkshopBooking accept(long member, String quoteRef) {
        PendingQuote quote = openQuotes.remove(quoteRef);
        if (quote == null || quote.member() != member) throw new IllegalArgumentException("Unknown quote " + quoteRef);
        String bookingCode = "BK-LY-" + UUID.randomUUID().toString().substring(0, 12);
        Instant acceptedAt = Instant.now();
        AcceptedBenefitRef accepted = transaction.execute(status -> loyalty.reserveAcceptedTransaction(
                new AcceptedSourceContext(quote.source(), bookingCode, 1L, true, acceptedAt, acceptedAt.plusSeconds(600),
                        quoteRef, quote.preview().termsDigest()),
                quote.selection(), "create-" + bookingCode));
        long post = accepted.postPromotionAmount().longValueExact();
        legacy.pending(bookingCode, PACKAGE_ID, "Workshop nhẫn bạc", "member@example.test", "0900000000", member,
                quote.participants(), List.of(quote.session()), BRANCH_ID, pricePerParticipant, post);
        BenefitPreview preview = quote.preview();
        transaction.executeWithoutResult(status -> memberQuotes.saveAndFlush(MemberWorkshopBookingQuote.builder()
                .quoteRef(quoteRef).memberAccountId(member).packageId(PACKAGE_ID)
                .packageNameSnapshot("Workshop nhẫn bạc").pricePerParticipant(pricePerParticipant)
                .participantCount(quote.participants()).sessionFactsJson("[]")
                .benefitSelectionJson("{\"choice\":\"" + quote.selection().choice() + "\"}")
                .grossAmount(preview.grossAmount().longValueExact())
                .programDiscount(preview.programDiscount().longValueExact())
                .pointsUsed(preview.pointsUsed().longValueExact())
                .pointDiscount(preview.pointDiscount().longValueExact())
                .postPromotionAmount(post).earnBase(preview.earnBase().longValueExact())
                .termsDigest(preview.termsDigest()).quoteState("ACCEPTED").acceptedBookingCode(bookingCode)
                .createdAt(acceptedAt).expiresAt(acceptedAt.plusSeconds(1_800)).build()));
        return reload(bookingCode);
    }

    public DepositPaymentEvidenceOutcome payDeposit(WorkshopBooking booking) {
        try {
            return prepareDeposit(booking).call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Creates the deposit attempt now and returns the verified-evidence call, to be run (also concurrently) later.
     * A booking that is no longer pending or confirmed goes to the REAL facade, which refuses evidence for any
     * reference that is not a booking group.
     */
    public Callable<DepositPaymentEvidenceOutcome> prepareDeposit(WorkshopBooking booking) {
        String code = booking.getBookingCode();
        BookingStatus current = reload(code).getBookingStatus();
        if (current != BookingStatus.PENDING_PAYMENT && current != BookingStatus.CONFIRMED) {
            BookingInvoice invoice = invoice(code);
            return () -> bookingFacade.applyVerifiedDepositPaymentEvidence(new ApplyVerifiedDepositPaymentEvidenceCommand(
                    code, "ipn-" + code, "M-" + UUID.randomUUID(), invoice.getDepositAmount(), "VND",
                    "op-" + UUID.randomUUID(), "corr-" + UUID.randomUUID()));
        }
        String merchant = legacy.depositAttempt(code, "20261101000000");
        String transactionNumber = Long.toString(ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L));
        return () -> legacy.confirmDeposit(code, merchant, transactionNumber)
                ? DepositPaymentEvidenceOutcome.accepted()
                : DepositPaymentEvidenceOutcome.rejected(edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason.INVALID_STATE);
    }

    /** The hold runs out with no verified payment: the booking expires and the held benefits are released. */
    public void expireUnpaid(WorkshopBooking booking) {
        legacy.expireUnpaid(booking.getBookingCode());
    }

    public WorkshopBooking reload(String bookingCode) {
        return legacy.reload(bookingCode);
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
