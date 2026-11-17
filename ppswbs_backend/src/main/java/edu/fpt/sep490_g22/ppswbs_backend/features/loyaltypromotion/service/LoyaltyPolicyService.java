package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyPolicyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyRegistryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyPolicy;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PolicyTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCommandService.CommandKey;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCommandService.CommandResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Points policy for Managers (T032, FR-024, FR-026, FR-027, US8.1-US8.5).
 *
 * <p>The policy is a chain of immutable revisions; the registry points to the current one. Its {@code version} is
 * the revision number, which the Manager sends back as {@code expectedVersion}. An update, under the exclusive
 * registry lock, checks that version, inserts a new revision and moves the pointer, together with the audit row
 * and the command receipt. Wallets and accepted transactions are never touched or recomputed: a transaction keeps
 * the policy it was accepted under, even a workshop completed after the change, and a missing old policy is never
 * replaced by the new one. An invalid or unconfirmed update leaves the current valid policy in place.
 */
@Service
public class LoyaltyPolicyService {

    public static final String ACTION = "policy.update";

    /** Requested values, already parsed and range-checked. */
    public record PolicyUpdate(long expectedVersion, PolicyTerms terms) {
    }

    private final LoyaltyCommandService commands;
    private final LoyaltyLockCoordinator locks;
    private final LoyaltyRegistryRepository registries;
    private final LoyaltyPolicyRepository policies;
    private final Clock clock;

    public LoyaltyPolicyService(LoyaltyCommandService commands, LoyaltyLockCoordinator locks,
                                LoyaltyRegistryRepository registries, LoyaltyPolicyRepository policies,
                                ObjectProvider<Clock> clock) {
        this.commands = commands;
        this.locks = locks;
        this.registries = registries;
        this.policies = policies;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    @Transactional(readOnly = true)
    public LoyaltyPolicy current() {
        LoyaltyRegistry registry = registries.findById(LoyaltyRegistry.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("The loyalty registry row is missing"));
        return policies.findById(registry.getCurrentPolicyId())
                .orElseThrow(() -> new IllegalStateException("The current loyalty policy is missing"));
    }

    /** Returns the id of the new current policy, or of the one created earlier with the same command key. */
    public long update(String managerRef, UUID commandId, PolicyUpdate request) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("expectedVersion", request.expectedVersion());
        canonical.put("earnUnitAmount", request.terms().earnUnitAmount().toString());
        canonical.put("redeemPointValue", request.terms().redeemPointValue().toString());
        canonical.put("minRedeemPoints", request.terms().minRedeemPoints().toString());
        canonical.put("maxRedeemPercent", request.terms().maxRedeemPercent().stripTrailingZeros().toPlainString());
        CommandKey key = new CommandKey(managerRef, ACTION, commandId, LoyaltyCommandService.digest(canonical));
        return commands.execute(key, () -> write(managerRef, request)).result().path("policyId").asLong();
    }

    private CommandResult write(String managerRef, PolicyUpdate request) {
        LoyaltyRegistry registry = locks.writeRegistry();
        LoyaltyPolicy current = policies.findById(registry.getCurrentPolicyId())
                .orElseThrow(() -> new IllegalStateException("The current loyalty policy is missing"));
        LoyaltyLockCoordinator.requireVersion(current.getRevisionNo(), request.expectedVersion());
        long nextRevision = policies.findMaxRevisionNo() + 1;
        LoyaltyPolicy next = policies.saveAndFlush(LoyaltyPolicy.create(nextRevision, request.terms(),
                LoyaltyValues.truncateToMicros(clock.instant()), Long.parseLong(managerRef)));
        registry.pointTo(next.getId());
        registries.flush();
        String detail = "{\"fromRevision\":" + current.getRevisionNo() + ",\"toRevision\":" + nextRevision + "}";
        return new CommandResult("policy:" + next.getId(), detail, Map.of("policyId", next.getId()));
    }
}
