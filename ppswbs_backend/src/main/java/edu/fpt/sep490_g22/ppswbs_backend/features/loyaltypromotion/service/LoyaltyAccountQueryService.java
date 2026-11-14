package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PointLedgerRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyAccountDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyPolicyDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.LoyaltyScalarCodec;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * My Loyalty reads (T038, FR-023, FR-031, FR-041, US6.4, US6.8). Balance, held, available and the current
 * conversion are read in one read-only transaction, so a new balance is never combined with an old held amount.
 * A Member without a wallet reads zero and no wallet is created by a read. History is the append-only ledger,
 * newest first, linked to the source transaction; there is no expiry and no purge.
 */
@Service
public class LoyaltyAccountQueryService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final LoyaltyWalletRepository wallets;
    private final PointLedgerRepository ledger;
    private final AcceptedBenefitRepository benefits;
    private final LoyaltyPolicyService policies;

    public LoyaltyAccountQueryService(LoyaltyWalletRepository wallets, PointLedgerRepository ledger,
                                      AcceptedBenefitRepository benefits, LoyaltyPolicyService policies) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.benefits = benefits;
        this.policies = policies;
    }

    @Transactional(readOnly = true)
    public LoyaltyAccountDtos.Account account(long memberId) {
        LoyaltyWallet wallet = wallets.findById(memberId).orElse(null);
        BigInteger balance = wallet == null ? BigInteger.ZERO : wallet.getBalancePoints();
        BigInteger held = wallet == null ? BigInteger.ZERO : wallet.getHeldPoints();
        return new LoyaltyAccountDtos.Account(balance.toString(), held.toString(), balance.subtract(held).toString(),
                LoyaltyPolicyDtos.Policy.of(policies.current()));
    }

    @Transactional(readOnly = true)
    public LoyaltyAccountDtos.TransactionPage transactions(long memberId, Integer limit, String cursor) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw LoyaltyException.invalidField("limit", "Must be between 1 and " + MAX_LIMIT + ".");
        }
        Instant afterOccurredAt = null;
        Long afterId = null;
        if (cursor != null) {
            String[] parts = decode(cursor);
            afterOccurredAt = parseInstant(parts[0]);
            afterId = LoyaltyScalarCodec.parseId("cursor", parts[1]);
        }
        List<PointLedger> rows = ledger.findMemberPage(memberId, afterOccurredAt, afterId, PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        List<PointLedger> page = more ? rows.subList(0, size) : rows;
        Map<Long, AcceptedBenefit> sources = benefits.findAllById(
                page.stream().map(PointLedger::getAcceptedBenefitId).distinct().toList()).stream()
                .collect(Collectors.toMap(AcceptedBenefit::getId, Function.identity()));
        List<LoyaltyAccountDtos.Transaction> items = page.stream().map(entry -> {
            AcceptedBenefit source = sources.get(entry.getAcceptedBenefitId());
            return new LoyaltyAccountDtos.Transaction(entry.getId().toString(), entry.getEntryKind().name(),
                    source.getSourceKind().name(), source.getSourceRef(), entry.getDeltaBalance().toString(),
                    entry.getDeltaHeld().toString(), entry.getOccurredAt().toString(),
                    entry.getOriginalDebitId() == null ? null : entry.getOriginalDebitId().toString());
        }).toList();
        String next = more ? encode(page.get(page.size() - 1)) : null;
        return new LoyaltyAccountDtos.TransactionPage(items, next);
    }

    private static String encode(PointLedger last) {
        String raw = last.getOccurredAt().toString() + "|" + last.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String[] decode(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2) {
                throw invalidCursor();
            }
            return parts;
        } catch (IllegalArgumentException e) {
            throw invalidCursor();
        }
    }

    private static Instant parseInstant(String raw) {
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw invalidCursor();
        }
    }

    private static LoyaltyException invalidCursor() {
        return LoyaltyException.invalidField("cursor", "The cursor is not valid.");
    }
}
