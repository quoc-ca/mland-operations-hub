package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller;

import com.fasterxml.jackson.databind.JsonNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.CatalogueRequests;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueAuthorization;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueAuthorization.Capability;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PriceProposalService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantQuantityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantSaleStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.committed;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.flag;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.required;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.version;

/**
 * Size and price commands (T039 apply, T049 quantity, T056 proposals, T063 sale status):
 * {@code /variants/{variantId}/...} and {@code /price-proposals/{proposalId}/...}. Only a Manager may decide a
 * price; applying is a separate command with no amount in the body.
 */
@RestController
@RequestMapping("/api/v1/catalogue-management")
class ManagementPriceApiController {

    private static final String KEY = "Idempotency-Key";

    private final CatalogueAuthorization authorization;
    private final VariantQuantityService quantities;
    private final VariantSaleStatusService saleStatus;
    private final PriceProposalService proposals;
    private final PriceApplicationService applications;

    ManagementPriceApiController(CatalogueAuthorization authorization, VariantQuantityService quantities,
                                 VariantSaleStatusService saleStatus, PriceProposalService proposals,
                                 PriceApplicationService applications) {
        this.authorization = authorization;
        this.quantities = quantities;
        this.saleStatus = saleStatus;
        this.proposals = proposals;
        this.applications = applications;
    }

    @PutMapping("/variants/{variantId}/quantity")
    ResponseEntity<JsonNode> setQuantity(@PathVariable String variantId, @RequestHeader(KEY) UUID key,
                                         @RequestBody CatalogueRequests.QuantityRequest body) {
        String actor = authorization.require(Capability.VARIANT_QUANTITY);
        return committed(quantities.setQuantity(actor, key, variantId, version(body.expectedVersion()),
                required("quantity", body.quantity())));
    }

    @PutMapping("/variants/{variantId}/sale-status")
    ResponseEntity<JsonNode> setSaleStatus(@PathVariable String variantId, @RequestHeader(KEY) UUID key,
                                           @RequestBody CatalogueRequests.SaleStatusRequest body) {
        String actor = authorization.require(Capability.VARIANT_SALE_STATUS);
        return committed(saleStatus.setSaleEnabled(actor, key, variantId, version(body.expectedVersion()),
                flag("saleEnabled", body.saleEnabled())));
    }

    @PostMapping("/variants/{variantId}/price-proposals")
    ResponseEntity<JsonNode> propose(@PathVariable String variantId, @RequestHeader(KEY) UUID key,
                                     @RequestBody CatalogueRequests.PriceRequest body) {
        String actor = authorization.require(Capability.PRICE_PROPOSE);
        return committed(proposals.create(actor, key, variantId, version(body.expectedVersion()),
                required("proposedPrice", body.proposedPrice())));
    }

    @PostMapping("/price-proposals/{proposalId}/revisions")
    ResponseEntity<JsonNode> revise(@PathVariable String proposalId, @RequestHeader(KEY) UUID key,
                                    @RequestBody CatalogueRequests.PriceRequest body) {
        String actor = authorization.require(Capability.PRICE_PROPOSE);
        return committed(proposals.revise(actor, key, proposalId, version(body.expectedVersion()),
                required("proposedPrice", body.proposedPrice())));
    }

    @PostMapping("/price-proposals/{proposalId}/submissions")
    ResponseEntity<JsonNode> submit(@PathVariable String proposalId, @RequestHeader(KEY) UUID key,
                                    @RequestBody CatalogueRequests.VersionRequest body) {
        String actor = authorization.require(Capability.PRICE_PROPOSE);
        return committed(proposals.submit(actor, key, proposalId, version(body.expectedVersion())));
    }

    @PostMapping("/price-proposals/{proposalId}/decisions")
    ResponseEntity<JsonNode> decide(@PathVariable String proposalId, @RequestHeader(KEY) UUID key,
                                    @RequestBody CatalogueRequests.PriceDecision body) {
        String actor = authorization.require(Capability.PRICE_APPROVE);
        PriceProposalService.Decision decision = switch (required("decision", body.decision())) {
            case "APPROVE" -> PriceProposalService.Decision.APPROVE;
            case "REJECT" -> PriceProposalService.Decision.REJECT;
            default -> throw CatalogueException.invalidField("decision", "Must be APPROVE or REJECT.");
        };
        return committed(proposals.decide(actor, key, proposalId, new PriceProposalService.DecisionCommand(
                version(body.expectedVersion()), decision, body.approvedPrice(), body.reason())));
    }

    @PostMapping("/price-proposals/{proposalId}/applications")
    ResponseEntity<JsonNode> apply(@PathVariable String proposalId, @RequestHeader(KEY) UUID key,
                                   @RequestBody CatalogueRequests.VersionRequest body) {
        String actor = authorization.require(Capability.PRICE_APPLY);
        return committed(applications.apply(actor, key, proposalId, version(body.expectedVersion())));
    }
}
