package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller;

import com.fasterxml.jackson.databind.JsonNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.CatalogueRequests;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.CommandReceiptView;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueAuthorization;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueAuthorization.Capability;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CommandReceiptQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ManagementProductQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductImageService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductReviewService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.UpdateProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.committed;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.flag;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.ok;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.required;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.version;

/**
 * Product management API (T027, T033, T039 publication, T049 update/images, T063 delete; command receipts):
 * {@code /api/v1/catalogue-management/products/**} and {@code /command-receipts/{commandId}}.
 * Every call authorizes the current actor for the action first (T007); mutations need {@code Idempotency-Key}.
 */
@RestController
@RequestMapping("/api/v1/catalogue-management")
class ManagementProductApiController {

    private static final String KEY = "Idempotency-Key";

    private final CatalogueAuthorization authorization;
    private final ManagementProductQueryService query;
    private final CreateProductService createProducts;
    private final UpdateProductService updates;
    private final DeleteProductService deletion;
    private final ProductImageService images;
    private final ProductReviewService reviews;
    private final ProductPublicationService publication;
    private final CommandReceiptQueryService receipts;

    ManagementProductApiController(CatalogueAuthorization authorization, ManagementProductQueryService query,
                                   CreateProductService createProducts, UpdateProductService updates,
                                   DeleteProductService deletion, ProductImageService images,
                                   ProductReviewService reviews, ProductPublicationService publication,
                                   CommandReceiptQueryService receipts) {
        this.authorization = authorization;
        this.query = query;
        this.createProducts = createProducts;
        this.updates = updates;
        this.deletion = deletion;
        this.images = images;
        this.reviews = reviews;
        this.publication = publication;
        this.receipts = receipts;
    }

    @GetMapping("/products")
    ResponseEntity<ManagementProductDtos.ManagementProductPage> list(@RequestParam(required = false) String q,
                                                                     @RequestParam(required = false) String categoryId,
                                                                     @RequestParam(required = false) Boolean published,
                                                                     @RequestParam(required = false) Integer page) {
        authorization.require(Capability.READ_INTERNAL);
        return ok(query.list(q, categoryId, published, page));
    }

    @PostMapping("/products")
    ResponseEntity<JsonNode> create(@RequestHeader(KEY) UUID key,
                                    @RequestBody CreateProductService.CreateProductCommand body) {
        String actor = authorization.require(Capability.PRODUCT_CREATE);
        return committed(createProducts.create(actor, key, body));
    }

    @GetMapping("/products/{productId}")
    ResponseEntity<ManagementProductDtos.ManagementProduct> get(@PathVariable String productId) {
        authorization.require(Capability.READ_INTERNAL);
        return ok(query.get(productId));
    }

    @PutMapping("/products/{productId}")
    ResponseEntity<JsonNode> update(@PathVariable String productId, @RequestHeader(KEY) UUID key,
                                    @RequestBody CatalogueRequests.ProductUpdate body) {
        String actor = authorization.require(Capability.PRODUCT_EDIT);
        return committed(updates.update(actor, key, productId, version(body.expectedVersion()),
                new UpdateProductService.UpdateCommand(body.name(), body.description(), body.categoryId())));
    }

    @DeleteMapping("/products/{productId}")
    ResponseEntity<JsonNode> delete(@PathVariable String productId, @RequestHeader(KEY) UUID key,
                                    @RequestParam Long expectedVersion) {
        String actor = authorization.require(Capability.PRODUCT_DELETE);
        return committed(deletion.delete(actor, key, productId, version(expectedVersion)));
    }

    @PutMapping("/products/{productId}/images")
    ResponseEntity<JsonNode> replaceImages(@PathVariable String productId, @RequestHeader(KEY) UUID key,
                                           @RequestBody CatalogueRequests.ImagesUpdate body) {
        String actor = authorization.require(Capability.MEDIA_MANAGE);
        List<CreateProductService.ImageInput> wanted = required("images", body.images()).stream()
                .map(image -> new CreateProductService.ImageInput(image.mediaId(), image.altText(), image.position()))
                .toList();
        return committed(images.replace(actor, key, productId, version(body.expectedVersion()), wanted));
    }

    @PostMapping("/products/{productId}/review-submissions")
    ResponseEntity<JsonNode> submitReview(@PathVariable String productId, @RequestHeader(KEY) UUID key,
                                          @RequestBody CatalogueRequests.VersionRequest body) {
        String actor = authorization.require(Capability.PRODUCT_SUBMIT);
        return committed(reviews.submit(actor, key, productId, version(body.expectedVersion())));
    }

    @PostMapping("/products/{productId}/review-decisions")
    ResponseEntity<JsonNode> decideReview(@PathVariable String productId, @RequestHeader(KEY) UUID key,
                                          @RequestBody CatalogueRequests.ReviewDecision body) {
        String actor = authorization.require(Capability.PRODUCT_APPROVE);
        ProductReviewService.Decision decision = switch (required("decision", body.decision())) {
            case "APPROVE" -> ProductReviewService.Decision.APPROVE;
            case "REJECT" -> ProductReviewService.Decision.REJECT;
            default -> throw CatalogueException.invalidField("decision", "Must be APPROVE or REJECT.");
        };
        if (decision == ProductReviewService.Decision.APPROVE && (body.prices() == null || body.prices().isEmpty())) {
            throw CatalogueException.invalidField("prices", "An approval lists every size.");
        }
        List<ProductReviewService.DecisionPrice> prices = body.prices() == null ? List.of() : body.prices().stream()
                .map(price -> new ProductReviewService.DecisionPrice(price.variantId(), price.proposalId(),
                        price.approvedPrice()))
                .toList();
        return committed(reviews.decide(actor, key, productId, new ProductReviewService.DecisionCommand(
                body.reviewId(), body.reviewRevision(), body.contentVersion(), decision, body.reason(), prices)));
    }

    @PutMapping("/products/{productId}/publication")
    ResponseEntity<JsonNode> setPublication(@PathVariable String productId, @RequestHeader(KEY) UUID key,
                                            @RequestBody CatalogueRequests.PublicationRequest body) {
        String actor = authorization.require(Capability.PRODUCT_PUBLISH);
        return committed(publication.setPublication(actor, key, productId, version(body.expectedVersion()),
                flag("published", body.published())));
    }

    @GetMapping("/command-receipts/{commandId}")
    ResponseEntity<CommandReceiptView> receipt(@PathVariable String commandId, @RequestParam String action) {
        String actor = authorization.require(Capability.READ_INTERNAL);
        return ok(receipts.find(actor, commandId, action));
    }
}
