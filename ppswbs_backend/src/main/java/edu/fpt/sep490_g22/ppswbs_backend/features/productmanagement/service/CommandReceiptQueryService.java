package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueCommandReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ActionResult;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.CommandReceiptView;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueCommandReceipt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * {@code getCommandReceipt} (FR-028): lets a client that lost a response find out whether its command committed.
 * Only the actor's own receipt for the given action is visible; the request payload is never returned, and a
 * receipt grants nothing by itself (the caller re-authorizes the actor first). Missing → COMMAND_RESULT_NOT_FOUND.
 */
@Service
public class CommandReceiptQueryService {

    private final CatalogueCommandReceiptRepository receipts;
    private final ObjectMapper objectMapper;

    public CommandReceiptQueryService(CatalogueCommandReceiptRepository receipts, ObjectMapper objectMapper) {
        this.receipts = receipts;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CommandReceiptView find(String actorId, String commandId, String action) {
        UUID id;
        try {
            id = UUID.fromString(commandId);
        } catch (RuntimeException e) {
            throw new CatalogueException(CatalogueErrorCode.COMMAND_RESULT_NOT_FOUND);
        }
        if (action == null || action.isBlank() || action.length() > 100) {
            throw CatalogueException.invalidField("action", "The action is required.");
        }
        CatalogueCommandReceipt receipt = receipts.findByActorIdAndActionAndCommandId(actorId, action, id)
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.COMMAND_RESULT_NOT_FOUND));
        return new CommandReceiptView(receipt.getCommandId(), receipt.getAction(), result(receipt),
                receipt.getCommittedAt());
    }

    /** Every receipt is shown as an ActionResult, whatever minimal body the command stored. */
    private ActionResult result(CatalogueCommandReceipt receipt) {
        JsonNode body = read(receipt.getSafeResult());
        String status = body.hasNonNull("status") && body.get("status").isTextual() ? body.get("status").asText()
                : body.hasNonNull("state") && body.get("state").isTextual() ? body.get("state").asText() : "COMMITTED";
        boolean changed = !body.has("changed") || body.get("changed").asBoolean(true);
        return new ActionResult(receipt.getCommandId(), receipt.getResourceId(), receipt.getResourceType(),
                receipt.getAction(), status, receipt.getResultVersion(), changed);
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Stored command result is not valid JSON", e);
        }
    }
}
