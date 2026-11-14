package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitPreview;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.BenefitSelection;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SelectionChoice;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceLine;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TrustedSourceContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailBenefitPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local-only FE-06 substitute for the simple checkout path. Discounts and points are calculated by the real
 * FE-06 preview (saved vouchers, point balance and policy from the database), so demos show real numbers, but
 * nothing is reserved, consumed or earned: the loyalty ledger is never written. A size the catalogue does not
 * know still gets the old demo rule (voucher MLAND100 = 100,000 VND off).
 */
@Service
@Profile("local")
public class LocalRetailBenefitAdapter implements RetailBenefitPort {
    private final Map<String, StoredPreview> previews = new ConcurrentHashMap<>();
    private final LoyaltyFacade loyalty;
    private final LocalCatalogueFacts catalogue;
    // The FE-06 preview runs in its own read-only transaction, so a rejected voucher never marks the caller's
    // transaction for rollback.
    private final TransactionTemplate isolatedRead;

    LocalRetailBenefitAdapter(LoyaltyFacade loyalty, LocalCatalogueFacts catalogue, PlatformTransactionManager transactions) {
        this.loyalty = loyalty;
        this.catalogue = catalogue;
        this.isolatedRead = new TransactionTemplate(transactions);
        this.isolatedRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.isolatedRead.setReadOnly(true);
    }

    @Override
    public RetailCheckoutPorts.BenefitPreview preview(Long memberAccountId, List<RetailCheckoutPorts.PricedLine> lines,
                                                       RetailCheckoutPorts.BenefitSelection selection) {
        BigDecimal subtotal = lines.stream().map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        RetailCheckoutPorts.BenefitPreview calculated = loyaltyPreview(memberAccountId, lines, selection, subtotal, expiresAt);
        if (calculated == null) calculated = demoPreview(selection, subtotal, expiresAt);
        String id = calculated.eligible() ? "local-benefit-" + UUID.randomUUID() : null;
        RetailCheckoutPorts.BenefitPreview preview = new RetailCheckoutPorts.BenefitPreview(id, calculated.discountAmount(),
            calculated.pointsDiscountAmount(), expiresAt, calculated.maxPoints(), calculated.minRedeemPoints(),
            calculated.eligible(), calculated.reasons(), calculated.eligibleLineIndexes(), calculated.appliedVoucherCode());
        if (id != null) previews.put(id, new StoredPreview(memberAccountId, selection, subtotal, preview));
        return preview;
    }

    @Override
    public RetailCheckoutPorts.BenefitPreview requireValidPreview(Long memberAccountId, String previewId,
                                                                    RetailCheckoutPorts.BenefitSelection selection, BigDecimal subtotal) {
        StoredPreview stored = previewId == null ? null : previews.get(previewId);
        if (stored == null || !stored.memberAccountId.equals(memberAccountId) || stored.preview.expiresAt().isBefore(Instant.now())
            || stored.subtotal.compareTo(subtotal) != 0 || !stored.selection.equals(selection)) {
            throw new RetailConflictException("Benefit preview is no longer valid for this cart");
        }
        return stored.preview;
    }

    /** Real FE-06 calculation, or null when a line is not a catalogue size (demo data) or FE-06 cannot answer. */
    private RetailCheckoutPorts.BenefitPreview loyaltyPreview(Long memberId, List<RetailCheckoutPorts.PricedLine> lines,
                                                               RetailCheckoutPorts.BenefitSelection selection,
                                                               BigDecimal subtotal, Instant expiresAt) {
        try {
            List<SourceLine> sourceLines = lines.stream().map(line -> {
                var fact = catalogue.read(line.variantId(), line.locationId()).orElseThrow();
                return new SourceLine(fact.productId(), line.variantId(), line.quantity(),
                    line.unitPrice().toBigIntegerExact(), line.locationId());
            }).toList();
            BigInteger points = selection.requestedPoints() == null ? BigInteger.ZERO : selection.requestedPoints().toBigIntegerExact();
            BenefitSelection loyaltySelection = new BenefitSelection(SelectionChoice.valueOf(selection.choice()),
                "VOUCHER".equals(selection.choice()) ? selection.voucherCode() : null, points);
            TrustedSourceContext context = new TrustedSourceContext(SourceKind.RETAIL, memberId,
                Instant.now().truncatedTo(ChronoUnit.MICROS), sourceLines, null, BigInteger.ZERO);
            BenefitPreview result = isolatedRead.execute(status -> loyalty.previewBenefits(context, loyaltySelection));
            if (result == null) return null;
            boolean eligible = (loyaltySelection.choice() != SelectionChoice.VOUCHER || result.programId() != null)
                && points.equals(result.pointsUsed());
            return new RetailCheckoutPorts.BenefitPreview(null, new BigDecimal(result.programDiscount()),
                new BigDecimal(result.pointDiscount()), expiresAt, result.maxPoints(), result.minRedeemPoints(),
                eligible, result.reasons(), result.eligibleLineIndexes(),
                loyaltySelection.choice() == SelectionChoice.VOUCHER ? result.normalizedCode() : null);
        } catch (LoyaltyException invalidSelection) {
            return new RetailCheckoutPorts.BenefitPreview(null, BigDecimal.ZERO, BigDecimal.ZERO, expiresAt, null, null,
                false, List.of(invalidSelection.getMessage() == null ? "Ưu đãi không hợp lệ." : invalidSelection.getMessage()));
        } catch (RuntimeException notACatalogueSize) {
            return null;
        }
    }

    private static RetailCheckoutPorts.BenefitPreview demoPreview(RetailCheckoutPorts.BenefitSelection selection,
                                                                  BigDecimal subtotal, Instant expiresAt) {
        BigDecimal discount = "VOUCHER".equals(selection.choice()) && "MLAND100".equalsIgnoreCase(selection.voucherCode())
            ? subtotal.min(BigDecimal.valueOf(100_000)) : BigDecimal.ZERO;
        return new RetailCheckoutPorts.BenefitPreview(null, discount, BigDecimal.ZERO, expiresAt);
    }

    private record StoredPreview(Long memberAccountId, RetailCheckoutPorts.BenefitSelection selection,
                                 BigDecimal subtotal, RetailCheckoutPorts.BenefitPreview preview) { }
}
