package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.PriceProposal;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T021 / T083 (part) / V-01 over HTTP with the real security chain on MySQL: 001's {@code IdentityGateFilter}
 * verifies the bearer and 002 authorizes the action. Only Firebase token verification and the actor lookup of the
 * 001 facade are stubbed (one token per role); everything after that is the real stack.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueApiIT {

    private static final Map<String, CurrentAccountActor> ACTORS = Map.of(
            "staff", actor(9101L, ActorRole.STAFF, ActorStatus.ACTIVE),
            "manager", actor(9102L, ActorRole.MANAGER, ActorStatus.ACTIVE),
            "admin", actor(9103L, ActorRole.ADMIN, ActorStatus.ACTIVE),
            "member", actor(9104L, ActorRole.MEMBER, ActorStatus.ACTIVE),
            "pending", actor(9105L, ActorRole.STAFF, ActorStatus.PENDING_SETUP));

    @MockitoBean private TokenVerificationService tokens;
    @MockitoBean private AccountFacade accounts;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CatalogueFlowFixture fixture;

    @BeforeEach
    void stubIdentity() {
        when(tokens.verify(anyString())).thenAnswer(call -> new VerifiedIdentity(call.getArgument(0), "x@example.test",
                true, Instant.now()));
        when(accounts.resolveActor(anyString(), anyBoolean()))
                .thenAnswer(call -> Optional.ofNullable(ACTORS.get((String) call.getArgument(0))));
    }

    @Test
    void managementNeedsABearerAndOnlyActiveStaffOrManager() throws Exception {
        mvc.perform(get("/api/v1/catalogue-management/products")).andExpect(status().isUnauthorized());
        for (String role : List.of("admin", "member", "pending")) {
            mvc.perform(as(role, get("/api/v1/catalogue-management/products")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CATALOGUE_ACCESS_DENIED"));
        }
        long draft = fixture.draftProduct("9101");
        mvc.perform(as("staff", get("/api/v1/catalogue-management/products"))
                        .param("categoryId", Long.toString(fixture.categoryOf(draft))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(draft)));
        // A product that was published reads FE-08 held quantities through the real source (develop b0e7796).
        long published = fixture.publishedProduct("9101", "9102");
        mvc.perform(as("manager", get("/api/v1/catalogue-management/products/" + published)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void publicCatalogueNeedsNoBearer() throws Exception {
        mvc.perform(get("/api/v1/product-categories"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/products/999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        // A write on a public path is not public.
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReplaysTheSameKeyAndRefusesTheSameKeyWithOtherData() throws Exception {
        long categoryId = fixture.activeCategory();
        UUID key = UUID.randomUUID();
        String body = productJson("Nhẫn API " + UUID.randomUUID(), categoryId);

        JsonNode first = read(mvc.perform(as("staff", post("/api/v1/catalogue-management/products"))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceType").value("product"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn());
        JsonNode replay = read(mvc.perform(as("staff", post("/api/v1/catalogue-management/products"))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn());

        assertThat(replay.get("resourceId")).isEqualTo(first.get("resourceId"));
        mvc.perform(as("staff", post("/api/v1/catalogue-management/products"))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Khác " + UUID.randomUUID(), categoryId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mvc.perform(as("staff", get("/api/v1/catalogue-management/command-receipts/" + key))
                        .param("action", "product.create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resourceId").value(first.get("resourceId").asText()));
        mvc.perform(as("manager", get("/api/v1/catalogue-management/command-receipts/" + key))
                        .param("action", "product.create"))
                .andExpect(status().isNotFound());
    }

    @Test
    void strictJsonAndRequiredHeaders() throws Exception {
        long categoryId = fixture.activeCategory();
        String withUnknownField = productJson("Nhẫn " + UUID.randomUUID(), categoryId)
                .replaceFirst("\\{", "{\"sku\":\"SP1-S05\",");
        mvc.perform(as("staff", post("/api/v1/catalogue-management/products"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(withUnknownField))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mvc.perform(as("staff", post("/api/v1/catalogue-management/products"))
                        .contentType(MediaType.APPLICATION_JSON).content(productJson("Nhẫn", categoryId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAManagerDecidesAndTheWholeFlowWorksOverHttp() throws Exception {
        long productId = fixture.draftProduct("9101");
        fixture.addReadyImage(productId, "9101");
        mvc.perform(as("staff", post("/api/v1/catalogue-management/products/" + productId + "/review-submissions"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + fixture.productVersion(productId) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        ProductReview review = fixture.currentReview(productId);
        List<PriceProposal> proposals = fixture.proposalsOf(review.getId());
        String decision = json.writeValueAsString(Map.of("reviewId", review.getId().toString(),
                "reviewRevision", review.getRevision(), "contentVersion", review.getContentVersion(),
                "decision", "APPROVE", "prices", proposals.stream().map(p -> Map.of(
                        "variantId", p.getVariantId().toString(), "proposalId", p.getId().toString(),
                        "approvedPrice", p.getProposedAmount().toString())).toList()));

        mvc.perform(as("staff", post("/api/v1/catalogue-management/products/" + productId + "/review-decisions"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(decision))
                .andExpect(status().isForbidden());
        mvc.perform(as("manager", post("/api/v1/catalogue-management/products/" + productId + "/review-decisions"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(decision))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        for (PriceProposal proposal : fixture.proposalsOf(review.getId())) {
            mvc.perform(as("staff", post("/api/v1/catalogue-management/price-proposals/" + proposal.getId()
                            + "/applications")).header("Idempotency-Key", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":" + proposal.getVersion() + "}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(as("staff", put("/api/v1/catalogue-management/products/" + productId + "/publication"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + fixture.productVersion(productId) + ",\"published\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void productAndSizeCommandsOverHttpUntilDeleteBlocksThem() throws Exception {
        long productId = fixture.draftProduct("9101");
        long categoryId = fixture.categoryOf(productId);
        String base = "/api/v1/catalogue-management";
        long variantId = fixture.proposalsOf(fixture.currentReview(productId).getId()).get(0).getVariantId();

        mvc.perform(as("staff", put(base + "/products/" + productId)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "expectedVersion", fixture.productVersion(productId), "name", "Đổi tên " + UUID.randomUUID(),
                                "description", "Mô tả mới", "categoryId", Long.toString(categoryId)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(true));
        UUID media = fixture.readyMedia("9101", Instant.now());
        mvc.perform(as("staff", put(base + "/products/" + productId + "/images")).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "expectedVersion", fixture.productVersion(productId),
                                "images", List.of(Map.of("mediaId", media.toString(), "altText", "Mặt trước", "position", 0))))))
                .andExpect(status().isOk());
        mvc.perform(as("staff", put(base + "/variants/" + variantId + "/quantity")).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"quantity\":\"9\"}"))
                .andExpect(status().isOk());
        mvc.perform(as("staff", put(base + "/variants/" + variantId + "/sale-status")).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"saleEnabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(as("staff", post(base + "/variants/" + variantId + "/price-proposals")).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2,\"proposedPrice\":\"130000\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"));

        long version = fixture.productVersion(productId);
        mvc.perform(as("staff", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(base + "/products/" + productId)).header("Idempotency-Key", UUID.randomUUID())
                        .param("expectedVersion", Long.toString(version)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELETED"));
        mvc.perform(as("staff", put(base + "/products/" + productId)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "expectedVersion", version + 1, "name", "X", "description", "Y",
                                "categoryId", Long.toString(categoryId)))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_DELETED"));
    }

    @Test
    void anUnknownOriginIsRefusedOnThePublicCatalogue() throws Exception {
        mvc.perform(options("/api/v1/products").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/product-categories").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String productJson(String name, long categoryId) throws Exception {
        return json.writeValueAsString(Map.of("name", name, "description", "Mô tả", "categoryId",
                Long.toString(categoryId), "variants", List.of(Map.of("size", 7, "proposedPrice", "120000",
                        "quantity", "2"))));
    }

    private static CurrentAccountActor actor(long id, ActorRole role, ActorStatus status) {
        return new CurrentAccountActor(id, role, status, true, List.of());
    }
}
