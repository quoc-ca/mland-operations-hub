package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueCommandReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueCommandReceipt;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs a catalogue command at most once per actor/action/command key (FR-026, FR-028).
 *
 * <ol>
 *   <li>The caller authorizes the actor <em>before</em> calling; a receipt never grants authority.</li>
 *   <li>A committed receipt with the same request digest is replayed; a different digest is
 *       {@code IDEMPOTENCY_KEY_REUSED}.</li>
 *   <li>Otherwise the work, its audit and the receipt commit in one transaction. If a concurrent
 *       duplicate wins the unique key, this transaction is rolled back first and the winner's receipt
 *       is read in a new transaction; the failed transaction is never reused.</li>
 * </ol>
 * The work itself re-checks state, version and deletion, so a missing receipt can never resurrect data.
 */
@Service
public class CatalogueCommandExecutor {

    public record CommandKey(String actorId, String action, UUID commandId, String requestDigest) {
        public CommandKey {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(commandId, "commandId");
            Objects.requireNonNull(requestDigest, "requestDigest");
        }
    }

    /** Confirmed result of the work; {@code safeResult} is the response body, without secrets. */
    public record CommandResult(int status, String resourceType, String resourceId, Long resultVersion,
                                Object safeResult) {
    }

    public record Outcome(int status, String resourceType, String resourceId, Long resultVersion,
                          JsonNode body, boolean replayed) {
    }

    private static final ObjectMapper CANONICAL = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final CatalogueCommandReceiptRepository receipts;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;

    public CatalogueCommandExecutor(CatalogueCommandReceiptRepository receipts,
                                    PlatformTransactionManager transactionManager,
                                    ObjectMapper objectMapper) {
        this.receipts = receipts;
        this.transaction = new TransactionTemplate(transactionManager);
        // Commands serialize through pessimistic row locks. Under MySQL's default REPEATABLE READ, plain reads
        // made after waiting for a lock would still see the snapshot taken before the wait (the scalar parent
        // lookup runs first), so a command could act on data another command has already changed. READ
        // COMMITTED makes every read after the lock see the latest committed state.
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.objectMapper = objectMapper;
    }

    /** SHA-256 (hex) of the canonical JSON of the normalized request: target, expectedVersion and payload. */
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
            throw new IllegalStateException("A catalogue command must own its transaction");
        }
        Optional<Outcome> committed = findCommitted(key);
        if (committed.isPresent()) {
            return committed.get();
        }
        try {
            return transaction.execute(status -> {
                CommandResult result = work.get();
                String body = toJson(result.safeResult());
                receipts.saveAndFlush(new CatalogueCommandReceipt(key.actorId(), key.action(), key.commandId(),
                        key.requestDigest(), result.resourceType(), result.resourceId(), result.status(),
                        result.resultVersion(), body, CatalogueValues.toStoredInstant(Instant.now())));
                return new Outcome(result.status(), result.resourceType(), result.resourceId(),
                        result.resultVersion(), readJson(body), false);
            });
        } catch (DataIntegrityViolationException duplicate) {
            // The transaction above is already rolled back; read the concurrent winner in a fresh one.
            return findCommitted(key).orElseThrow(() -> duplicate);
        }
    }

    /** Committed result for the key, or empty when none exists (which does not prove the command never ran). */
    public Optional<Outcome> findCommitted(CommandKey key) {
        return transaction.execute(status -> receipts
                .findByActorIdAndActionAndCommandId(key.actorId(), key.action(), key.commandId())
                .map(receipt -> {
                    if (!receipt.getRequestDigest().equals(key.requestDigest())) {
                        throw new CatalogueException(CatalogueErrorCode.IDEMPOTENCY_KEY_REUSED);
                    }
                    return new Outcome(receipt.getResultStatus(), receipt.getResourceType(),
                            receipt.getResourceId(), receipt.getResultVersion(),
                            readJson(receipt.getSafeResult()), true);
                }));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the command result", e);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json.getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Stored command result is not valid JSON", e);
        }
    }
}
