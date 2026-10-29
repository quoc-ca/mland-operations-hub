package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MemberWorkshopBookingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopBookingQuoteRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopBookingQuoteResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MemberWorkshopBookingQuote;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkshopBookingQuoteService {
    private final WorkshopBookingAuthorizationService authorization;
    private final WorkshopOperationsAdapter operations;
    private final LoyaltyFacade loyalty;
    private final MemberWorkshopBookingQuoteRepository quotes;
    private final ObjectMapper mapper;

    @Transactional
    public WorkshopBookingQuoteResponse preview(String authorizationHeader, WorkshopBookingQuoteRequest request) {
        var member = authorization.requireMemberOwnership(authorizationHeader, null);
        List<UUID> references = request.sessionReferences().stream().map(WorkshopBookingQuoteService::canonicalUuid).toList();
        if (new HashSet<>(references).size() != references.size()) {
            throw new BookingValidationException("sessionReferences must be distinct");
        }
        PublishedPackageSelectionSnapshot pkg = operations.getPublishedPackageSelection(request.packageId().toString());
        if (pkg == null || pkg.packageName() == null || pkg.packageName().isBlank()) {
            throw new BookingConflictException("Package is not currently available");
        }
        if (request.participantCount() < pkg.minParticipants() || request.participantCount() > pkg.maxParticipants()
                || references.size() != pkg.requiredSessionCount()) {
            throw new BookingValidationException("Selection does not match the published Package requirements");
        }
        List<WorkshopSessionFact> sessionFacts = new ArrayList<>();
        for (UUID reference : references) {
            SaleEligibilitySnapshot session = operations.getSaleEligibility(reference);
            if (session == null || !session.customerSelectable() || session.locationReference() == null
                    || session.workshopDate() == null || session.remainingCapacity() < request.participantCount()) {
                throw new BookingConflictException("A selected session is not currently available");
            }
            sessionFacts.add(WorkshopSessionFact.of(reference.toString(), session.locationReference(), session.workshopDate()));
        }
        BigInteger gross = BigInteger.valueOf(pkg.publicPerParticipantPrice())
                .multiply(BigInteger.valueOf(request.participantCount()));
        var selected = request.benefitSelection();
        BenefitSelection selection = new BenefitSelection(
                SelectionChoice.valueOf(selected.choice().name()), selected.voucherCode(), new BigInteger(selected.requestedPoints()));
        WorkshopFact facts = new WorkshopFact(request.packageId(), request.participantCount(), gross, sessionFacts);
        BenefitPreview preview = loyalty.previewBenefits(
                new TrustedSourceContext(SourceKind.WORKSHOP, member.accountId(), Instant.now(), List.of(), facts, BigInteger.ZERO),
                selection);
        String quoteRef = UUID.randomUUID().toString();
        MemberWorkshopBookingQuote quote = MemberWorkshopBookingQuote.builder()
                .quoteRef(quoteRef).memberAccountId(member.accountId()).packageId(request.packageId())
                .packageNameSnapshot(pkg.packageName()).pricePerParticipant(pkg.publicPerParticipantPrice())
                .participantCount(request.participantCount()).sessionFactsJson(write(sessionFacts))
                .benefitSelectionJson(write(selection)).grossAmount(exact(preview.grossAmount()))
                .programDiscount(exact(preview.programDiscount())).pointsUsed(exact(preview.pointsUsed()))
                .pointDiscount(exact(preview.pointDiscount())).postPromotionAmount(exact(preview.postPromotionAmount()))
                .earnBase(exact(preview.earnBase())).termsDigest(preview.termsDigest()).build();
        quotes.save(quote);
        long post = quote.getPostPromotionAmount();
        long deposit = Math.floorDiv(post, 2) + (post % 2);
        return new WorkshopBookingQuoteResponse(quoteRef, str(quote.getGrossAmount()), str(quote.getProgramDiscount()),
                str(quote.getPointsUsed()), str(quote.getPointDiscount()), str(post), str(deposit), str(post - deposit),
                str(quote.getEarnBase()), quote.getTermsDigest(), preview.reasons());
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not persist quote facts", e); }
    }
    private static UUID canonicalUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equals(value)) throw new IllegalArgumentException();
            return uuid;
        } catch (RuntimeException e) { throw new BookingValidationException("sessionReferences must be canonical UUIDs"); }
    }
    private static long exact(BigInteger value) {
        try { return value.longValueExact(); }
        catch (ArithmeticException e) { throw new BookingValidationException("Quote amount is outside the supported VND range"); }
    }
    private static String str(long value) { return Long.toString(value); }
}
