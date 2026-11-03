package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs a Manager or Member command at most once per actor, action and command key (interfaces "Wire rules",
 * T018).
 *
 * <ol>
 *   <li>The caller authorizes the actor <em>before</em> calling; a receipt never grants authority, and a replay
 *       happens only after the same authorization.</li>
 *   <li>A committed receipt with the same canonical digest is replayed; a different digest is
 *       {@code IDEMPOTENCY_CONFLICT} (409).</li>
 *   <li>Otherwise the work (state, ledger, counters), its audit and the receipt commit in one transaction. If a
 *       concurrent duplicate wins the unique key, this transaction is rolled back first and the winner's receipt
 *       is read in a new transaction; the rolled-back transaction is never reused.</li>
 * </ol>
 * The digest covers the normalized request, target references, versions and verified amounts; never a token,
 * correlation id or personal data. Facade effects (reserve, settle, release, restore, earn) do not use this class:
 * they join the source owner's transaction and are made idempotent by outcome keys and ledger unique keys.
 */
@Service
public class LoyaltyCommandService {

    /** Identity of one command. {@code actorKey} is an opaque actor reference, never an email or token. */
    public record CommandKey(String actorKey, String action, UUID commandId, String canonicalDigest) {
        public CommandKey {
            Objects.requireNonNull(actorKey, "actorKey");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(commandId, "commandId");
            Objects.requireNonNull(canonicalDigest, "canonicalDigest");
        }
    }

    /** What the work committed: the object it changed, a safe audit detail and the minimal result to replay. */
    public record CommandResult(String objectRef, String safeDetail, Object minimalResult) {
    }

    /** Result returned to the controller; {@code replayed} is true when an earlier commit answered. */
    public record Outcome(String objectRef, JsonNode result, boolean replayed) {
    }

    private static final ObjectMapper CANONICAL = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final LoyaltyAuditRepository audits;
    private final LoyaltyAuditService auditService;
    private final TransactionTemplate transaction;

    public LoyaltyCommandService(LoyaltyAuditRepository audits, LoyaltyAuditService auditService,
                                 PlatformTransactionManager transactionManager) {
        this.audits = audits;
        this.auditService = auditService;
        this.transaction = new TransactionTemplate(transactionManager);
        // Reads made after waiting for a row lock must see the latest committed state, not the snapshot taken
        // before the wait (MySQL REPEATABLE READ), or two commands could act on stale counters.
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** SHA-256 (hex) of the canonical JSON of the normalized request. */
    public static String digest(Object normalizedRequest) {
        try {
            byte[] json = CANONICAL.writeValueAsBytes(normalizedRequest);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot compute the request digest", e);
        }
    }

    public Outcome execute(CommandKey key, Supplier<CommandResult> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A loyalty command must own its transaction");
        }
        Optional<Outcome> committed = findCommitted(key);
        if (committed.isPresent()) {
            return committed.get();
        }
        try {
            return transaction.execute(status -> {
                CommandResult result = work.get();
                String body = toJson(result.minimalResult());
                auditService.recordCommand(key.actorKey(), key.action(), result.objectRef(), result.safeDetail(),
                        key.actorKey(), key.commandId().toString(), key.canonicalDigest(), body);
                return new Outcome(result.objectRef(), readJson(body), false);
            });
        } catch (DataIntegrityViolationException duplicate) {
            // The transaction above is already rolled back; read the concurrent winner in a fresh one.
            return findCommitted(key).orElseThrow(() -> duplicate);
        }
    }

    /** Committed result for the key, or empty when none exists (which does not prove the command never ran). */
    public Optional<Outcome> findCommitted(CommandKey key) {
        return transaction.execute(status -> audits
                .findByActorKeyAndActionAndCommandId(key.actorKey(), key.action(), key.commandId().toString())
                .map(receipt -> {
                    if (!receipt.getCanonicalDigest().equals(key.canonicalDigest())) {
                        throw new LoyaltyException(LoyaltyErrorCode.IDEMPOTENCY_CONFLICT,
                                "This command key was already used with a different request.");
                    }
                    return new Outcome(receipt.getObjectRef(), readJson(receipt.getMinimalResult()), true);
                }));
    }

    private static String toJson(Object value) {
        try {
            return CANONICAL.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the command result", e);
        }
    }

    private static JsonNode readJson(String json) {
        try {
            return CANONICAL.readTree(json == null ? "null" : json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored command result is not valid JSON", e);
        }
    }
}
