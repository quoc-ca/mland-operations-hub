package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.CategoryDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.PublicCatalogueDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PublicCatalogueQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PublicMediaService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.PublicSellingLocationService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.ok;

/**
 * Public catalogue (T069) and public product media (T070). No identity is needed: these routes are matched by
 * {@code CatalogueSecurityConfiguration}. A product or image that is hidden, deleted or otherwise ineligible is
 * 404 like one that never existed; a missing FE-08 hold source is 503, never "zero holds".
 */
@RestController
@RequestMapping("/api/v1")
class PublicProductApiController {

    private final PublicCatalogueQueryService catalogue;
    private final PublicMediaService media;
    private final PublicSellingLocationService locations;

    PublicProductApiController(PublicCatalogueQueryService catalogue, PublicMediaService media,
                               PublicSellingLocationService locations) {
        this.catalogue = catalogue;
        this.media = media;
        this.locations = locations;
    }

    /** Branches to choose before "Add to cart" (decision A, 2026-11-01); 503 when the branch source is unavailable. */
    @GetMapping("/product-selling-locations")
    ResponseEntity<PublicCatalogueDtos.SellingLocationList> sellingLocations() {
        return ok(locations.list());
    }

    @GetMapping("/products")
    ResponseEntity<PublicCatalogueDtos.PublicProductPage> list(@RequestParam(required = false) String q,
                                                               @RequestParam(required = false) String categoryId,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) String minimumPrice,
                                                               @RequestParam(required = false) String maximumPrice,
                                                               @RequestParam(required = false) Boolean canBuy,
                                                               @RequestParam(required = false) String sort) {
        return ok(catalogue.list(new PublicCatalogueQueryService.Filter(q, categoryId, minimumPrice, maximumPrice,
                canBuy, sort, page)));
    }

    @GetMapping("/products/{productId}")
    ResponseEntity<PublicCatalogueDtos.PublicProduct> get(@PathVariable String productId) {
        return ok(catalogue.get(productId));
    }

    @GetMapping("/product-categories")
    ResponseEntity<CategoryDtos.CategoryPage> categories(@RequestParam(required = false) Integer page) {
        return ok(catalogue.categories(page));
    }

    @GetMapping("/product-media/{mediaId}")
    ResponseEntity<InputStreamResource> image(@PathVariable String mediaId) {
        PublicMediaService.PublicMedia image = media.open(mediaId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(image.content()));
    }
}
