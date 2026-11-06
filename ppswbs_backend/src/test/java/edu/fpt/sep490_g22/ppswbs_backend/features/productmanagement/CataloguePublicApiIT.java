package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantSaleStatusService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailAvailabilityGuardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * T074 over HTTP on MySQL 8.4 with FE-08's REAL hold source (RetailAvailabilityGuardService): what a Guest's browser
 * receives from {@code GET /api/v1/products}, {@code /products/{id}}, {@code /product-categories} and the media URL.
 * Only public fields leave the server, responses are no-store, browsing writes nothing, the price range and "can buy"
 * hold for the same size, a disabled size keeps its label, invalid criteria are refused, the keyword limit is 200/201,
 * pages hold 20, ties sort by id, old URLs of hidden or deleted products read as not found, and a hold source that
 * cannot be verified answers 503 until it recovers. Service-level rules are in {@link CataloguePublicIT} (T066).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CataloguePublicApiIT {

    private static final Set<String> PRODUCT_FIELDS = Set.of("id", "name", "description", "category", "images", "variants",
            "priceRange", "canBuy", "createdAt");
    private static final Set<String> VARIANT_FIELDS = Set.of("id", "size", "sku", "appliedPrice", "currency", "saleEnabled", "canBuy");
    private static final Set<String> IMAGE_FIELDS = Set.of("id", "url", "altText", "position");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private ProductVariantRepository variants;
    @Autowired private VariantSaleStatusService saleStatus;
    @Autowired private ProductPublicationService publication;
    @Autowired private DeleteProductService deletion;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transaction;
    @MockitoSpyBean private RetailAvailabilityGuardService holdSource;

    @Test
    void onlyPublicFieldsLeaveTheServerAndBrowsingWritesNothing() throws Exception {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        Map<String, Long> before = rowCounts();

        JsonNode detail = read(get("/api/v1/products/" + productId), 200);
        assertThat(fields(detail)).isEqualTo(PRODUCT_FIELDS);
        assertThat(fields(detail.get("category"))).isEqualTo(Set.of("id", "name"));
        detail.get("variants").forEach(variant -> assertThat(fields(variant)).isEqualTo(VARIANT_FIELDS));
        detail.get("images").forEach(image -> assertThat(fields(image)).isEqualTo(IMAGE_FIELDS));
        assertThat(detail.toString()).doesNotContain("proposal", "review", "remainingUnsold", "validHeld", "\"version\"", "mediaId");
        JsonNode list = read(get("/api/v1/products").param("categoryId", detail.get("category").get("id").asText()), 200);
        assertThat(fields(list)).isEqualTo(Set.of("items", "page", "pageSize", "totalElements"));
        list.get("items").forEach(item -> assertThat(fields(item)).isEqualTo(PRODUCT_FIELDS));

        // Images are same-origin media paths; the provider key or a draft URL never leaves the server.
        assertThat(detail.get("images").get(0).get("url").asText()).matches("/api/v1/product-media/[0-9a-f-]{36}");
        read(get("/api/v1/product-categories"), 200);

        assertThat(rowCounts()).as("a Guest browsing creates no cart, hold, order, receipt or audit").isEqualTo(before);
    }

    @Test
    void thePriceRangeAndCanBuyHoldForTheSameSizeAndADisabledSizeKeepsItsLabel() throws Exception {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        String category = Long.toString(fixture.categoryOf(productId));
        // size 5: 150.000, quantity 3; size 12: 180.000, sold out.
        JsonNode detail = read(get("/api/v1/products/" + productId), 200);
        assertThat(detail.get("priceRange").get("minimum").asText()).isEqualTo("150000");
        assertThat(detail.get("priceRange").get("maximum").asText()).isEqualTo("180000");
        assertThat(detail.get("variants").get(1).get("canBuy").asBoolean()).as("size 12 sold out").isFalse();
        assertThat(ids(read(get("/api/v1/products").param("categoryId", category).param("minimumPrice", "170000").param("canBuy", "true"), 200)))
                .as("the buyable size is not the one in the price range").isEmpty();
        assertThat(ids(read(get("/api/v1/products").param("categoryId", category).param("maximumPrice", "160000").param("canBuy", "true"), 200)))
                .containsExactly(Long.toString(productId));

        ProductVariant size5 = transaction.execute(status -> variants.findByProductIdOrderByRingSizeAsc(productId)).get(0);
        saleStatus.setSaleEnabled(STAFF, UUID.randomUUID(), size5.getId().toString(), size5.getVersion(), false);

        JsonNode after = read(get("/api/v1/products/" + productId), 200);
        assertThat(after.get("variants")).hasSize(2);
        assertThat(after.get("variants").get(0).get("saleEnabled").asBoolean()).isFalse();
        assertThat(after.get("variants").get(0).get("canBuy").asBoolean()).isFalse();
        assertThat(after.get("canBuy").asBoolean()).isFalse();
    }

    @Test
    void invalidCriteriaAreRefusedAndTheKeywordLimitIs200() throws Exception {
        assertThat(code(get("/api/v1/products").param("sort", "cheapest"))).isEqualTo("INVALID_SORT");
        assertThat(code(get("/api/v1/products").param("minimumPrice", "1.5"))).isEqualTo("VALIDATION_ERROR");
        assertThat(code(get("/api/v1/products").param("minimumPrice", "200000").param("maximumPrice", "100000"))).isNotBlank();
        assertThat(code(get("/api/v1/products").param("categoryId", "abc"))).isEqualTo("VALIDATION_ERROR");
        assertThat(code(get("/api/v1/products").param("page", "-1"))).isNotBlank();
        assertThat(read(get("/api/v1/products").param("q", "n".repeat(200)), 200).get("items")).isEmpty();
        assertThat(code(get("/api/v1/products").param("q", "n".repeat(201)))).isEqualTo("VALIDATION_ERROR");
        assertThat(read(get("/api/v1/products/abc"), 400).get("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void pagesHoldTwentyAndTiesSortById() throws Exception {
        long category = fixture.activeCategory();
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            long productId = fixture.publishedProduct(STAFF, MANAGER);
            jdbc.update("update catalogue_products set category_id = ? where id = ?", category, productId);
            created.add(productId);
        }
        JsonNode first = read(get("/api/v1/products").param("categoryId", Long.toString(category)).param("sort", "price-asc"), 200);
        JsonNode second = read(get("/api/v1/products").param("categoryId", Long.toString(category)).param("sort", "price-asc").param("page", "1"), 200);
        assertThat(first.get("pageSize").asInt()).isEqualTo(20);
        assertThat(first.get("totalElements").asText()).isEqualTo("21");
        assertThat(first.get("items")).hasSize(20);
        assertThat(second.get("items")).hasSize(1);
        List<String> order = new ArrayList<>(ids(first));
        order.addAll(ids(second));
        // Every product has the same minimum price, so the order is by id.
        assertThat(order).containsExactlyElementsOf(created.stream().sorted().map(String::valueOf).toList());
    }

    @Test
    void oldUrlsOfHiddenOrDeletedProductsReadAsNotFound() throws Exception {
        long hidden = fixture.publishedProduct(STAFF, MANAGER);
        long deleted = fixture.publishedProduct(STAFF, MANAGER);
        String hiddenImage = read(get("/api/v1/products/" + hidden), 200).get("images").get(0).get("url").asText();

        publication.setPublication(STAFF, UUID.randomUUID(), Long.toString(hidden), fixture.productVersion(hidden), false);
        deletion.delete(MANAGER, UUID.randomUUID(), Long.toString(deleted), fixture.productVersion(deleted));

        assertThat(read(get("/api/v1/products/" + hidden), 404).get("code").asText()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(read(get("/api/v1/products/" + deleted), 404).get("code").asText()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(mvc.perform(get(hiddenImage)).andReturn().getResponse().getStatus()).as("draft-only media is not served").isEqualTo(404);
    }

    @Test
    void anUnverifiableHoldSourceAnswers503UntilItRecovers() throws Exception {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        // The proxied (transactional) spy is stubbed inside a transaction so the call reaches Mockito.
        transaction.executeWithoutResult(status -> doReturn(Map.of()).when(holdSource).readSnapshots(any(), any()));
        try {
            JsonNode failure = read(get("/api/v1/products/" + productId), 503);
            assertThat(failure.get("code").asText()).isEqualTo("AVAILABILITY_DEPENDENCY_UNAVAILABLE");
            assertThat(failure.get("correlationId").asText()).isNotBlank();
            assertThat(failure.toString()).as("never an empty or zero-held answer").doesNotContain("\"canBuy\"");
        } finally {
            reset(holdSource);
        }
        assertThat(read(get("/api/v1/products/" + productId), 200).get("id").asText()).isEqualTo(Long.toString(productId));
    }

    // ---- helpers ----

    private JsonNode read(org.springframework.test.web.servlet.RequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String code(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(400);
        return json.readTree(result.getResponse().getContentAsString()).get("code").asText();
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> names = new TreeSet<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private Map<String, Long> rowCounts() {
        Map<String, Long> counts = new java.util.TreeMap<>();
        for (String table : List.of("catalogue_audit_events", "catalogue_command_receipts", "retail_variant_holds",
                "retail_carts", "retail_cart_lines", "retail_grouped_checkouts", "retail_orders")) {
            Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
            counts.put(table, count);
        }
        return counts;
    }
}
