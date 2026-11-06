package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller;

import com.fasterxml.jackson.databind.JsonNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.CatalogueRequests;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.CategoryDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueAuthorization;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueAuthorization.Capability;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductCategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.committed;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.flag;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.ok;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.version;

/**
 * Category management API (T078): Staff and Manager with {@code category.manage} (decision 2026-10-26).
 * The impact read must be shown before an activation change is confirmed.
 */
@RestController
@RequestMapping("/api/v1/catalogue-management/categories")
class ManagementCategoryApiController {

    private static final String KEY = "Idempotency-Key";

    private final CatalogueAuthorization authorization;
    private final ProductCategoryService categories;

    ManagementCategoryApiController(CatalogueAuthorization authorization, ProductCategoryService categories) {
        this.authorization = authorization;
        this.categories = categories;
    }

    @GetMapping
    ResponseEntity<CategoryDtos.CategoryPage> list(@RequestParam(required = false) Integer page) {
        authorization.require(Capability.CATEGORY_MANAGE);
        return ok(categories.list(page));
    }

    @PostMapping
    ResponseEntity<JsonNode> create(@RequestHeader(KEY) UUID key, @RequestBody CatalogueRequests.CategoryCreate body) {
        String actor = authorization.require(Capability.CATEGORY_MANAGE);
        return committed(categories.create(actor, key, body.name(), body.description()));
    }

    @PutMapping("/{categoryId}")
    ResponseEntity<JsonNode> update(@PathVariable String categoryId, @RequestHeader(KEY) UUID key,
                                    @RequestBody CatalogueRequests.CategoryUpdate body) {
        String actor = authorization.require(Capability.CATEGORY_MANAGE);
        return committed(categories.update(actor, key, categoryId, version(body.expectedVersion()), body.name(),
                body.description()));
    }

    @GetMapping("/{categoryId}/impact")
    ResponseEntity<CategoryDtos.CategoryImpact> impact(@PathVariable String categoryId,
                                                       @RequestParam(required = false) Integer page) {
        authorization.require(Capability.CATEGORY_MANAGE);
        return ok(categories.impact(categoryId, page));
    }

    @PutMapping("/{categoryId}/activation")
    ResponseEntity<JsonNode> setActivation(@PathVariable String categoryId, @RequestHeader(KEY) UUID key,
                                           @RequestBody CatalogueRequests.ActivationRequest body) {
        String actor = authorization.require(Capability.CATEGORY_MANAGE);
        return committed(categories.setActive(actor, key, categoryId, version(body.expectedVersion()),
                flag("active", body.active())));
    }
}
