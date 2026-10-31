package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenRevokedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T083 on MySQL: the running API against {@code contracts/product-management.openapi.json} — every operation is
 * mapped, ADMIN is refused on every management operation (FR-002), a cookie never replaces the bearer, a revoked
 * token is refused, and the JSON returned by the main operations satisfies its schema (no extra field, every
 * required field, types and the Id/Amount/Quantity/Sku patterns). Firebase verification and the 001 actor lookup
 * are stubbed; Member/workshop regressions run in the full suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueContractIT {

    private static final Path CONTRACT = Path.of("..", "ppswbs-spec", "features", "002-product-management", "contracts",
            "product-management.openapi.json");
    private static final Map<String, CurrentAccountActor> ACTORS = Map.of(
            "staff", new CurrentAccountActor(9301L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of()),
            "admin", new CurrentAccountActor(9303L, ActorRole.ADMIN, ActorStatus.ACTIVE, true, List.of()));

    @MockitoBean private TokenVerificationService tokens;
    @MockitoBean private AccountFacade accounts;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CatalogueFlowFixture fixture;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    private JsonNode contract;

    @BeforeAll
    void readContract() throws Exception {
        contract = json.readTree(Files.readString(CONTRACT));
    }

    @BeforeEach
    void stubIdentity() {
        when(tokens.verify(anyString())).thenAnswer(call -> new VerifiedIdentity(call.getArgument(0), "x@example.test",
                true, Instant.now()));
        when(tokens.verify(eq("revoked"))).thenThrow(new TokenRevokedException("revoked"));
        when(accounts.resolveActor(anyString(), anyBoolean()))
                .thenAnswer(call -> Optional.ofNullable(ACTORS.get((String) call.getArgument(0))));
    }

    @Test
    void everyContractOperationIsMappedAndTheContractHas47Schemas() {
        Set<String> mapped = new HashSet<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                info.getMethodsCondition().getMethods().forEach(method -> mapped.add(method.name() + " " + normalize(pattern)));
            }
        }
        List<String> operations = operations().stream().map(op -> op.method + " " + normalize(op.path)).toList();

        assertThat(operations).hasSize(32);
        assertThat(mapped).containsAll(operations);
        assertThat(contract.get("components").get("schemas").size()).isEqualTo(47);
    }

    @Test
    void adminIsRefusedOnEveryManagementOperation() throws Exception {
        List<String> notRefused = new ArrayList<>();
        for (Operation op : operations()) {
            if (!op.path.startsWith("/api/v1/catalogue-management")) {
                continue;
            }
            int status = mvc.perform(request(op).header("Authorization", "Bearer admin")).andReturn().getResponse().getStatus();
            if (status != 403) {
                notRefused.add(op.method + " " + op.path + " -> " + status);
            }
        }
        assertThat(notRefused).isEmpty();
    }

    @Test
    void aCookieNeverReplacesTheBearerAndARevokedTokenIsRefused() throws Exception {
        mvc.perform(get("/api/v1/catalogue-management/products").cookie(new Cookie("SESSION", "staff")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/catalogue-management/products").header("Authorization", "Bearer revoked"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    @Test
    void responsesOfTheMainOperationsMatchTheirSchemas() throws Exception {
        long categoryId = fixture.activeCategory();
        long productId = fixture.draftProduct("9301");
        String body = json.writeValueAsString(Map.of("name", "Hợp đồng " + UUID.randomUUID(), "description", "Mô tả",
                "categoryId", Long.toString(categoryId), "variants", List.of(Map.of("size", 9, "proposedPrice",
                        "123000", "quantity", "0"))));

        assertMatches("ActionResult", call(MockMvcRequestBuilders.post("/api/v1/catalogue-management/products")
                .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body)));
        assertMatches("ManagementProductPage", call(get("/api/v1/catalogue-management/products")
                .param("categoryId", Long.toString(fixture.categoryOf(productId)))));
        assertMatches("ManagementProduct", call(get("/api/v1/catalogue-management/products/" + productId)));
        assertMatches("CategoryPage", call(get("/api/v1/catalogue-management/categories")));
        assertMatches("CategoryImpact", call(get("/api/v1/catalogue-management/categories/"
                + fixture.categoryOf(productId) + "/impact")));
        assertMatches("CategoryPage", json.readTree(mvc.perform(get("/api/v1/product-categories")).andReturn()
                .getResponse().getContentAsString()));
        assertMatches("Error", json.readTree(mvc.perform(get("/api/v1/products/999999999999")).andReturn()
                .getResponse().getContentAsString()));
    }

    // ---- helpers ----

    private record Operation(String method, String path, JsonNode node) {
    }

    private List<Operation> operations() {
        List<Operation> result = new ArrayList<>();
        contract.get("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (Set.of("get", "post", "put", "delete").contains(method.getKey())) {
                result.add(new Operation(method.getKey().toUpperCase(), path.getKey(), method.getValue()));
            }
        }));
        return result;
    }

    private static String normalize(String path) {
        return path.replaceAll("\\{[^}]+}", "{}");
    }

    private MockHttpServletRequestBuilder request(Operation op) {
        String path = op.path.replace("{productId}", "1").replace("{variantId}", "1").replace("{proposalId}", "1")
                .replace("{categoryId}", "1").replace("{batchId}", UUID.randomUUID().toString())
                .replace("{commandId}", UUID.randomUUID().toString()).replace("{mediaId}", UUID.randomUUID().toString());
        JsonNode content = op.node.path("requestBody").path("content");
        MockHttpServletRequestBuilder request;
        if (content.has("multipart/form-data")) {
            request = MockMvcRequestBuilders.multipart(path)
                    .file(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1}));
        } else {
            request = MockMvcRequestBuilders.request(HttpMethod.valueOf(op.method), path);
            if (content.has("application/json")) {
                request.contentType(MediaType.APPLICATION_JSON).content("{}");
            }
        }
        return request.header("Idempotency-Key", UUID.randomUUID()).param("expectedVersion", "0").param("action", "x");
    }

    private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
        String content = mvc.perform(request.header("Authorization", "Bearer staff")).andReturn().getResponse()
                .getContentAsString();
        return json.readTree(content);
    }

    private void assertMatches(String schemaName, JsonNode value) {
        List<String> problems = new ArrayList<>();
        check(contract.get("components").get("schemas").get(schemaName), value, schemaName, problems);
        assertThat(problems).as("response of %s: %s", schemaName, value).isEmpty();
    }

    /** Minimal OpenAPI 3.0 check: $ref, oneOf, object (required/additionalProperties), array, enum, type, pattern. */
    private void check(JsonNode schema, JsonNode value, String at, List<String> problems) {
        if (schema.has("$ref")) {
            String name = schema.get("$ref").asText().replace("#/components/schemas/", "");
            check(contract.get("components").get("schemas").get(name), value, at, problems);
            return;
        }
        if (value == null || value.isNull()) {
            if (!schema.path("nullable").asBoolean(false)) {
                problems.add(at + " is null");
            }
            return;
        }
        if (schema.has("oneOf")) {
            for (JsonNode option : schema.get("oneOf")) {
                List<String> optionProblems = new ArrayList<>();
                check(option, value, at, optionProblems);
                if (optionProblems.isEmpty()) {
                    return;
                }
            }
            problems.add(at + " matches no oneOf option");
            return;
        }
        if (schema.has("enum")) {
            boolean listed = false;
            for (JsonNode allowed : schema.get("enum")) {
                listed |= allowed.equals(value);
            }
            if (!listed) {
                problems.add(at + " = " + value + " is not in the enum");
            }
        }
        String type = schema.path("type").asText("");
        switch (type) {
            case "object" -> {
                if (!value.isObject()) {
                    problems.add(at + " is not an object");
                    return;
                }
                JsonNode properties = schema.path("properties");
                for (JsonNode required : schema.path("required")) {
                    if (!value.has(required.asText())) {
                        problems.add(at + "." + required.asText() + " is missing");
                    }
                }
                for (Iterator<String> names = value.fieldNames(); names.hasNext(); ) {
                    String name = names.next();
                    if (properties.has(name)) {
                        check(properties.get(name), value.get(name), at + "." + name, problems);
                    } else if (!schema.path("additionalProperties").asBoolean(true)) {
                        problems.add(at + "." + name + " is not in the contract");
                    }
                }
            }
            case "array" -> {
                if (!value.isArray()) {
                    problems.add(at + " is not an array");
                    return;
                }
                for (int i = 0; i < value.size(); i++) {
                    check(schema.get("items"), value.get(i), at + "[" + i + "]", problems);
                }
            }
            case "string" -> {
                if (!value.isTextual()) {
                    problems.add(at + " is not a string");
                } else if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").asText()).matcher(value.asText()).find()) {
                    problems.add(at + " = " + value.asText() + " does not match " + schema.get("pattern").asText());
                }
            }
            case "integer" -> {
                if (!value.isIntegralNumber()) {
                    problems.add(at + " is not an integer");
                }
            }
            case "boolean" -> {
                if (!value.isBoolean()) {
                    problems.add(at + " is not a boolean");
                }
            }
            default -> {
                // untyped schema: nothing more to check
            }
        }
    }
}
