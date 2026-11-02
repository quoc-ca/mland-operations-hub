package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailBenefitPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.facade.RetailCheckoutPorts;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Local-only FE-06 substitute. It never reads or mutates a real loyalty ledger. */
@Service
@Profile("local")
public class LocalRetailBenefitAdapter implements RetailBenefitPort {
    private final Map<String, StoredPreview> previews = new ConcurrentHashMap<>();

    @Override
    public RetailCheckoutPorts.BenefitPreview preview(Long memberAccountId, List<RetailCheckoutPorts.PricedLine> lines,
                                                       RetailCheckoutPorts.BenefitSelection selection) {
        BigDecimal subtotal = lines.stream().map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = "VOUCHER".equals(selection.choice()) && "MLAND100".equalsIgnoreCase(selection.voucherCode())
            ? subtotal.min(BigDecimal.valueOf(100_000)) : BigDecimal.ZERO;
        String id = "local-benefit-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        RetailCheckoutPorts.BenefitPreview preview = new RetailCheckoutPorts.BenefitPreview(id, discount, BigDecimal.ZERO, expiresAt);
        previews.put(id, new StoredPreview(memberAccountId, selection, subtotal, preview));
        return preview;
    }

    @Override
    public RetailCheckoutPorts.BenefitPreview requireValidPreview(Long memberAccountId, String previewId,
                                                                    RetailCheckoutPorts.BenefitSelection selection, BigDecimal subtotal) {
        StoredPreview stored = previews.get(previewId);
        if (stored == null || !stored.memberAccountId.equals(memberAccountId) || stored.preview.expiresAt().isBefore(Instant.now())
            || stored.subtotal.compareTo(subtotal) != 0 || !stored.selection.equals(selection)) {
            throw new RetailConflictException("Benefit preview is no longer valid for this cart");
        }
        return stored.preview;
    }

    private record StoredPreview(Long memberAccountId, RetailCheckoutPorts.BenefitSelection selection,
                                 BigDecimal subtotal, RetailCheckoutPorts.BenefitPreview preview) { }
}
