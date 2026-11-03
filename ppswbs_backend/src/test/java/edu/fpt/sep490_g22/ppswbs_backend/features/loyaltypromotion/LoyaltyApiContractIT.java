package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.AcceptedBenefitRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyRegistryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyWalletRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PointLedgerRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * T071 on MySQL: the running API against {@code contracts/loyalty-promotion.openapi.json}, through the real 001
 * identity chain (only Firebase token verification is stubbed).
 *
 * <ul>
 *   <li>Exactly the 15 contract operations are mapped by loyaltypromotion controllers; there is no public
 *       reserve/settle/earn/release/restore route (those stay Java-only on {@code LoyaltyFacade}).</li>
 *   <li>Role matrix on every operation: public reads need no bearer; programme and policy operations are
 *       MANAGER-only; wallet and saved vouchers are MEMBER-only; ADMIN and STAFF have no FE-06 capability;
 *       no bearer gives 401 on every private operation. Every loyalty response is {@code no-store}.</li>
 *   <li>Every mutation needs a UUID {@code Idempotency-Key}; JSON bodies are strict (unknown field, number instead
 *       of decimal string); paging limits 1–100 and opaque cursors.</li>
 *   <li>The JSON of all 15 operations, and of the error envelope, satisfies its schema: required fields, no
 *       extra field, decimal-string patterns, enums, UTC timestamps.</li>
 * </ul>
 * Business rules are covered by the per-story suites; this class does not re-implement them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoyaltyApiContractIT {

    private static final Path CONTRACT = Path.of("..", "ppswbs-spec", "features", "006-loyalty-promotion", "contracts",
            "loyalty-promotion.openapi.json");
    private static final String UNKNOWN_ID = "9223372036854775807";
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private TransactionTemplate transaction;
    @Autowired private CatalogueFlowFixture catalogue;
    @Autowired private AccountRepository accounts;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private PromotionTargetRepository targets;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private AcceptedBenefitRepository benefits;
    @Autowired private PointLedgerRepository ledger;
    @Autowired private LoyaltyRegistryRepository registry;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;
    @MockBean private TokenVerificationService tokens;

    private JsonNode contract;
    private Map<AccountRole, String> bearers;
    private long memberId;

    @BeforeAll
    void readContract() throws Exception {
        contract = json.readTree(Files.readString(CONTRACT));
    }

    @BeforeEach
    void signInEveryRole() {
        bearers = new LinkedHashMap<>();
        for (AccountRole role : AccountRole.values()) {
            String uid = "loyalty-contract-it-" + UUID.randomUUID();
            Account account = accounts.save(Account.builder().firebaseUid(uid).loginEmail(uid + "@example.test")
                    .role(role).status(AccountStatus.ACTIVE).build());
            String token = "token-" + uid;
            when(tokens.verify(token)).thenReturn(new VerifiedIdentity(uid, uid + "@example.test", true, Instant.now()));
            bearers.put(role, "Bearer " + token);
            if (role == AccountRole.MEMBER) {
                memberId = account.getId();
            }
        }
    }

    // ---- mapping ----

    @Test
    void exactlyTheFifteenContractOperationsAreMapped() {
        Set<String> loyaltyRoutes = new HashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getPackageName().contains(".loyaltypromotion.")) {
                continue;
            }
            for (String pattern : entry.getKey().getPatternValues()) {
                entry.getKey().getMethodsCondition().getMethods()
                        .forEach(method -> loyaltyRoutes.add(method.name() + " " + normalize(pattern)));
            }
        }
        Set<String> operations = new HashSet<>();
        operations().forEach(op -> operations.add(op.method + " " + normalize(op.path)));

        assertThat(operations).hasSize(15);
        assertThat(loyaltyRoutes).as("no extra route such as a public reserve/earn/restore").isEqualTo(operations);
        assertThat(contract.get("components").get("schemas").size()).isEqualTo(34);
    }

    // ---- authority ----

    @Test
    void everyOperationFollowsTheRoleMatrix() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Operation op : operations()) {
            Set<AccountRole> allowed = allowedRoles(op);
            int anonymous = status(authorityProbe(op), null);
            if (op.isPublic() ? (anonymous == 401 || anonymous == 403) : anonymous != 401) {
                violations.add(op.id + " without bearer -> " + anonymous);
            }
            for (AccountRole role : AccountRole.values()) {
                MvcResult result = perform(authorityProbe(op), bearers.get(role));
                int status = result.getResponse().getStatus();
                boolean refused = status == 401 || status == 403;
                if (allowed.contains(role) == refused) {
                    violations.add(op.id + " as " + role + " -> " + status + " " + result.getResponse().getContentAsString());
                }
                if (!refused || status == 403) {
                    String cache = result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL);
                    if (cache == null || !cache.contains("no-store")) {
                        violations.add(op.id + " as " + role + " is cacheable: " + cache);
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    // ---- mutation keys and strict bodies ----

    @Test
    void everyMutationNeedsAUuidIdempotencyKey() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Operation op : operations()) {
            if (op.method.equals("GET")) {
                continue;
            }
            String bearer = bearers.get(allowedRoles(op).iterator().next());
            for (String key : new String[] {null, "not-a-uuid"}) {
                MockHttpServletRequestBuilder request = baseRequest(op, UNKNOWN_ID);
                if (key != null) {
                    request.header("Idempotency-Key", key);
                }
                MvcResult result = perform(request, bearer);
                if (result.getResponse().getStatus() != 400 || !code(result).equals("INVALID_INPUT")) {
                    violations.add(op.id + " key=" + key + " -> " + result.getResponse().getStatus() + " "
                            + result.getResponse().getContentAsString());
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void jsonBodiesAreStrict() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Operation op : operations()) {
            if (!op.hasJsonBody()) {
                continue;
            }
            String bearer = bearers.get(AccountRole.MANAGER);
            String valid = validBody(op);
            List<String> bad = new ArrayList<>(List.of(valid.replaceFirst("\\{", "{\"unexpected\":\"x\","), "[]", "{"));
            if (valid.contains("\"expectedVersion\"")) {
                bad.add(valid.replace("\"expectedVersion\":\"999999999\"", "\"expectedVersion\":999999999"));
            } else {
                bad.add(valid.replace("\"totalLimit\":\"10\"", "\"totalLimit\":10"));
            }
            for (String body : bad) {
                MvcResult result = perform(baseRequest(op, UNKNOWN_ID).header("Idempotency-Key", UUID.randomUUID())
                        .content(body), bearer);
                if (result.getResponse().getStatus() != 400 || !code(result).equals("INVALID_INPUT")) {
                    violations.add(op.id + " body=" + body + " -> " + result.getResponse().getStatus());
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    // ---- paging ----

    @Test
    void pagedOperationsAcceptOneToOneHundredAndOpaqueCursorsOnly() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Operation op : operations()) {
            if (!op.hasParameter("limit")) {
                continue;
            }
            String bearer = op.isPublic() ? null : bearers.get(allowedRoles(op).iterator().next());
            Map<String, Integer> expected = new LinkedHashMap<>();
            expected.put("limit=0", 400);
            expected.put("limit=101", 400);
            expected.put("limit=x", 400);
            expected.put("limit=1", 200);
            expected.put("limit=100", 200);
            expected.put("cursor=%21%21", 400);
            for (Map.Entry<String, Integer> query : expected.entrySet()) {
                MvcResult result = perform(MockMvcRequestBuilders.get(op.path + "?" + query.getKey()), bearer);
                if (result.getResponse().getStatus() != query.getValue()) {
                    violations.add(op.id + "?" + query.getKey() + " -> " + result.getResponse().getStatus());
                } else if (query.getValue() == 400 && !code(result).equals("INVALID_INPUT")) {
                    violations.add(op.id + "?" + query.getKey() + " code " + code(result));
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void aCursorContinuesWithoutOverlap() throws Exception {
        String manager = bearers.get(AccountRole.MANAGER);
        long productId = catalogue.draftProduct(LoyaltyTestSupport.STAFF);
        for (int i = 0; i < 3; i++) {
            createProgram(productId);
        }
        JsonNode first = body(perform(MockMvcRequestBuilders.get("/api/v1/promotion-programs?limit=2"), manager), 200);
        assertThat(first.path("items")).hasSize(2);
        assertThat(first.path("nextCursor").isTextual()).isTrue();
        JsonNode second = body(perform(MockMvcRequestBuilders.get("/api/v1/promotion-programs?limit=2&cursor="
                + first.path("nextCursor").asText()), manager), 200);
        Set<String> firstIds = new HashSet<>();
        first.path("items").forEach(item -> firstIds.add(item.path("id").asText()));
        second.path("items").forEach(item -> assertThat(firstIds).doesNotContain(item.path("id").asText()));
        assertThat(second.path("items").size()).isPositive();
    }

    // ---- response schemas ----

    @Test
    void responsesOfAllFifteenOperationsMatchTheirSchemas() throws Exception {
        String manager = bearers.get(AccountRole.MANAGER);
        String member = bearers.get(AccountRole.MEMBER);
        long publicProduct = catalogue.publishedProduct(LoyaltyTestSupport.STAFF, LoyaltyTestSupport.MANAGER);
        long activePublic = activeVoucher(publicProduct);
        walletWithHistory(memberId);

        JsonNode created = createProgram(publicProduct);
        assertMatches("Program", created);
        String programPath = "/api/v1/promotion-programs/" + created.path("id").asText();
        assertMatches("ProgramPage", ownItems(body(perform(MockMvcRequestBuilders.get("/api/v1/promotion-programs?limit=100"),
                manager), 200), created.path("id").asText()));
        assertMatches("Program", body(perform(MockMvcRequestBuilders.get(programPath), manager), 200));
        JsonNode updated = body(perform(withKey(MockMvcRequestBuilders.patch(programPath)).content(
                "{\"expectedVersion\":\"" + created.path("version").asText() + "\",\"name\":\"Đổi tên\"}"), manager), 200);
        assertMatches("Program", updated);
        JsonNode paused = body(perform(withKey(MockMvcRequestBuilders.post(programPath + "/pauses")).content(
                "{\"expectedVersion\":\"" + updated.path("version").asText() + "\"}"), manager), 200);
        assertMatches("Program", paused);
        assertMatches("Program", body(perform(withKey(MockMvcRequestBuilders.post(programPath + "/resumptions")).content(
                "{\"expectedVersion\":\"" + paused.path("version").asText() + "\"}"), manager), 200));
        assertMatches("UsageSummary", body(perform(MockMvcRequestBuilders.get(programPath + "/usage"), manager), 200));

        assertMatches("PromotionPage", ownItems(body(perform(MockMvcRequestBuilders.get("/api/v1/promotions?limit=100"),
                null), 200), Long.toString(activePublic)));
        assertMatches("PublicPromotion", body(perform(MockMvcRequestBuilders.get("/api/v1/promotions/" + activePublic),
                null), 200));
        assertMatches("SavedVoucher", body(perform(withKey(MockMvcRequestBuilders.put(
                "/api/v1/members/me/saved-vouchers/" + activePublic)), member), 200));
        JsonNode savedPage = body(perform(MockMvcRequestBuilders.get("/api/v1/members/me/saved-vouchers"), member), 200);
        assertThat(savedPage.path("items")).isNotEmpty();
        assertMatches("SavedVoucherPage", savedPage);
        assertMatches("Account", body(perform(MockMvcRequestBuilders.get("/api/v1/members/me/loyalty-accounts/current"),
                member), 200));
        JsonNode history = body(perform(MockMvcRequestBuilders.get("/api/v1/members/me/loyalty-transactions"), member), 200);
        assertThat(history.path("items")).isNotEmpty();
        assertMatches("TransactionPage", history);

        JsonNode policy = body(perform(MockMvcRequestBuilders.get("/api/v1/loyalty-policies/current"), manager), 200);
        assertMatches("Policy", policy);
        JsonNode changed = body(perform(withKey(MockMvcRequestBuilders.put("/api/v1/loyalty-policies/current"))
                .content(policyBody(policy.path("version").asText(), "11000")), manager), 200);
        try {
            assertMatches("Policy", changed);
        } finally {
            // Test cleanup only (as in LoyaltyMySqlConcurrencyIT): point the registry back at the policy that was
            // current, so suites that check the seeded policy still see it.
            long originalId = policy.path("id").asLong();
            transaction.executeWithoutResult(status ->
                    registry.findById(LoyaltyRegistry.SINGLETON_ID).orElseThrow().pointTo(originalId));
        }
    }

    @Test
    void errorsUseTheContractEnvelope() throws Exception {
        String manager = bearers.get(AccountRole.MANAGER);
        List<MvcResult> errors = List.of(
                perform(MockMvcRequestBuilders.get("/api/v1/promotion-programs?limit=0"), manager),
                perform(MockMvcRequestBuilders.get("/api/v1/promotion-programs"), bearers.get(AccountRole.ADMIN)),
                perform(MockMvcRequestBuilders.get("/api/v1/promotion-programs/" + UNKNOWN_ID), manager),
                perform(MockMvcRequestBuilders.get("/api/v1/promotions/" + UNKNOWN_ID), null),
                perform(withKey(MockMvcRequestBuilders.patch("/api/v1/promotion-programs/" + createProgram(
                        catalogue.draftProduct(LoyaltyTestSupport.STAFF)).path("id").asText()))
                        .content("{\"expectedVersion\":\"999999999\",\"name\":\"x\"}"), manager));
        List<Integer> statuses = new ArrayList<>();
        for (MvcResult result : errors) {
            statuses.add(result.getResponse().getStatus());
            assertMatches("Error", json.readTree(result.getResponse().getContentAsString()));
        }
        assertThat(statuses).containsExactly(400, 403, 404, 404, 409);
    }

    // ---- fixtures ----

    private JsonNode createProgram(long productId) throws Exception {
        String code = "AC" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return body(perform(withKey(MockMvcRequestBuilders.post("/api/v1/promotion-programs"))
                .content(programBody(code, productId)), bearers.get(AccountRole.MANAGER)), 201);
    }

    /** A voucher active now on a published product, so it is publicly listed. */
    private long activeVoucher(long productId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String code = "AP" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return transaction.execute(status -> {
            PromotionProgram program = programs.saveAndFlush(
                    PromotionProgram.voucher(TargetScope.PRODUCT, code, code, 10, 2, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.VOUCHER, "Ưu đãi hợp đồng", now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS),
                    DiscountTerms.fixed(BigInteger.valueOf(20_000), BigInteger.valueOf(20_000), BigInteger.ZERO), true,
                    now, 1L));
            targets.save(PromotionTarget.create(revision.getId(), TargetScope.PRODUCT, TargetDimension.PRODUCT,
                    Long.toString(productId)));
            program.pointToRevision(revision.getId());
            return program.getId();
        });
    }

    /** Wallet with balance 200 and 50 held, and one EARN history row. */
    private void walletWithHistory(long member) {
        transaction.executeWithoutResult(status -> {
            long policyId = registry.findById(LoyaltyRegistry.SINGLETON_ID).orElseThrow().getCurrentPolicyId();
            LoyaltyWallet wallet = LoyaltyWallet.open(member);
            wallet.earn(BigInteger.valueOf(200));
            wallet.hold(BigInteger.valueOf(50));
            wallets.saveAndFlush(wallet);
            AcceptedBenefit benefit = benefits.saveAndFlush(AcceptedBenefit.accept(new AcceptedBenefit.Snapshot(
                    SourceKind.RETAIL, "contract-it-" + UUID.randomUUID(), member, Instant.now(),
                    Instant.now().plusSeconds(600), 1L, "digest", policyId, null, null, BigInteger.valueOf(100_000),
                    BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(100_000), null, false), true));
            ledger.saveAndFlush(PointLedger.entry(LedgerEntryKind.EARN, member, benefit.getId(), BigInteger.TEN,
                    BigInteger.valueOf(200), BigInteger.valueOf(50), Instant.parse("2026-10-27T00:00:00Z"), null));
        });
    }

    private static String programBody(String code, long productId) {
        return """
                {"kind":"VOUCHER","scope":"PRODUCT","name":"Hợp đồng","code":"%s",
                 "startAt":"2026-11-01T00:00:00Z","endAt":"2026-12-01T00:00:00Z",
                 "discount":{"kind":"PERCENT","percentage":"10","maxDiscountAmount":"50000"},
                 "minimumEligibleAmount":"0","allowPoints":true,"targets":{"productIds":["%d"]},
                 "totalLimit":"10","perMemberLimit":"2"}""".formatted(code, productId);
    }

    private static String policyBody(String version, String earnUnitAmount) {
        return """
                {"expectedVersion":"%s","earnUnitAmount":"%s","redeemPointValue":"100",
                 "minRedeemPoints":"10","maxRedeemPercent":"10"}""".formatted(version, earnUnitAmount);
    }

    // ---- operations from the contract ----

    private record Operation(String id, String method, String path, JsonNode node) {

        boolean isPublic() {
            return node.has("security") && node.get("security").isEmpty();
        }

        boolean hasJsonBody() {
            return node.path("requestBody").path("content").has("application/json");
        }

        boolean hasParameter(String name) {
            for (JsonNode parameter : node.path("parameters")) {
                if (parameter.path("name").asText().equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    private List<Operation> operations() {
        List<Operation> result = new ArrayList<>();
        contract.get("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (HTTP_METHODS.contains(method.getKey())) {
                result.add(new Operation(method.getValue().path("operationId").asText(),
                        method.getKey().toUpperCase(), path.getKey(), method.getValue()));
            }
        }));
        return result;
    }

    private static Set<AccountRole> allowedRoles(Operation op) {
        if (op.isPublic()) {
            return Set.of(AccountRole.values());
        }
        return op.path.startsWith("/api/v1/members/me") ? Set.of(AccountRole.MEMBER) : Set.of(AccountRole.MANAGER);
    }

    private static String normalize(String path) {
        return path.replaceAll("\\{[^}]+}", "{}");
    }

    private MockHttpServletRequestBuilder baseRequest(Operation op, String id) {
        String path = op.path.replaceAll("\\{[^}]+}", id);
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(HttpMethod.valueOf(op.method), path);
        if (op.hasJsonBody()) {
            request.contentType(MediaType.APPLICATION_JSON).content(validBody(op));
        }
        return request;
    }

    /** A well-formed request on an unknown resource: allowed roles get past authority, refused ones get 401/403. */
    private MockHttpServletRequestBuilder authorityProbe(Operation op) {
        MockHttpServletRequestBuilder request = baseRequest(op, UNKNOWN_ID);
        if (!op.method.equals("GET")) {
            request.header("Idempotency-Key", UUID.randomUUID());
        }
        return request;
    }

    /** Valid for its schema; the stale expectedVersion keeps a Manager's probe from changing anything. */
    private static String validBody(Operation op) {
        return switch (op.id) {
            case "createPromotionProgram" -> programBody("PROBE" + UUID.randomUUID().toString().substring(0, 8)
                    .toUpperCase(), Long.parseLong(UNKNOWN_ID));
            case "updatePromotionProgram" -> "{\"expectedVersion\":\"999999999\",\"name\":\"x\"}";
            case "pausePromotionProgram", "resumePromotionProgram" -> "{\"expectedVersion\":\"999999999\"}";
            case "updateCurrentLoyaltyPolicy" -> policyBody("999999999", "10000");
            default -> throw new IllegalStateException("No body for " + op.id);
        };
    }

    // ---- HTTP helpers ----

    private static MockHttpServletRequestBuilder withKey(MockHttpServletRequestBuilder request) {
        return request.header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        return mvc.perform(request).andReturn();
    }

    private int status(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        return perform(request, bearer).getResponse().getStatus();
    }

    private JsonNode body(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        return json.readTree(result.getResponse().getContentAsString());
    }

    /**
     * The page with only this test's item kept (asserted present): the shared test database also holds rows other
     * suites seed directly through repositories, which are not API-created data.
     */
    private static JsonNode ownItems(JsonNode page, String id) {
        com.fasterxml.jackson.databind.node.ObjectNode copy = page.deepCopy();
        com.fasterxml.jackson.databind.node.ArrayNode items = copy.putArray("items");
        page.path("items").forEach(item -> {
            if (item.path("id").asText().equals(id)) {
                items.add(item);
            }
        });
        assertThat(items).as("item %s is listed", id).hasSize(1);
        return copy;
    }

    private String code(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).path("code").asText();
    }

    // ---- minimal OpenAPI 3.0 schema check ----

    private void assertMatches(String schemaName, JsonNode value) {
        List<String> problems = new ArrayList<>();
        check(contract.get("components").get("schemas").get(schemaName), value, schemaName, problems);
        assertThat(problems).as("%s: %s", schemaName, value).isEmpty();
    }

    /**
     * $ref, oneOf/anyOf, nullable, enum, object (required, additionalProperties false or schema, minProperties),
     * array (items, minItems, uniqueItems), string (pattern, min/maxLength, date-time with Z), integer, boolean.
     */
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
        for (String combinator : new String[] {"oneOf", "anyOf"}) {
            if (schema.has(combinator)) {
                int matching = 0;
                for (JsonNode option : schema.get(combinator)) {
                    List<String> optionProblems = new ArrayList<>();
                    check(option, value, at, optionProblems);
                    matching += optionProblems.isEmpty() ? 1 : 0;
                }
                if (matching == 0 || (combinator.equals("oneOf") && matching > 1)) {
                    problems.add(at + " matches " + matching + " " + combinator + " options");
                }
            }
        }
        if (!schema.has("type") && (schema.has("required") || schema.has("properties")) && value.isObject()) {
            // An untyped subschema such as {"required":["productIds"]} still constrains an object.
            checkObject(schema, value, at, problems);
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
        switch (schema.path("type").asText("")) {
            case "object" -> checkObject(schema, value, at, problems);
            case "array" -> {
                if (!value.isArray()) {
                    problems.add(at + " is not an array");
                    return;
                }
                if (value.size() < schema.path("minItems").asInt(0)) {
                    problems.add(at + " has fewer than " + schema.path("minItems").asInt() + " items");
                }
                if (schema.path("uniqueItems").asBoolean(false) && new HashSet<>(toList(value)).size() != value.size()) {
                    problems.add(at + " has duplicate items");
                }
                for (int i = 0; i < value.size(); i++) {
                    check(schema.get("items"), value.get(i), at + "[" + i + "]", problems);
                }
            }
            case "string" -> checkString(schema, value, at, problems);
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

    private void checkObject(JsonNode schema, JsonNode value, String at, List<String> problems) {
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
        if (value.size() < schema.path("minProperties").asInt(0)) {
            problems.add(at + " has too few properties");
        }
        JsonNode additional = schema.path("additionalProperties");
        for (Iterator<String> names = value.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (properties.has(name)) {
                check(properties.get(name), value.get(name), at + "." + name, problems);
            } else if (additional.isObject()) {
                check(additional, value.get(name), at + "." + name, problems);
            } else if (!additional.asBoolean(true)) {
                problems.add(at + "." + name + " is not in the contract");
            }
        }
    }

    private static void checkString(JsonNode schema, JsonNode value, String at, List<String> problems) {
        if (!value.isTextual()) {
            problems.add(at + " is not a string");
            return;
        }
        String text = value.asText();
        int length = text.codePointCount(0, text.length());
        if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").asText()).matcher(text).find()) {
            problems.add(at + " = " + text + " does not match " + schema.get("pattern").asText());
        }
        if (schema.has("minLength") && length < schema.get("minLength").asInt()) {
            problems.add(at + " is shorter than " + schema.get("minLength").asInt());
        }
        if (schema.has("maxLength") && length > schema.get("maxLength").asInt()) {
            problems.add(at + " is longer than " + schema.get("maxLength").asInt());
        }
        if (schema.path("format").asText().equals("date-time")) {
            try {
                Instant.parse(text);
                if (!text.endsWith("Z")) {
                    problems.add(at + " = " + text + " is not UTC");
                }
            } catch (RuntimeException e) {
                problems.add(at + " = " + text + " is not an ISO-8601 instant");
            }
        }
    }

    private static List<JsonNode> toList(JsonNode array) {
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        return items;
    }
}
