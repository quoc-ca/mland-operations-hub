package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T024: createPromotionProgram, listPromotionPrograms and getPromotionProgram through the real security chain,
 * the real identity gate of 001 (only the Firebase token check is replaced) and the real product target provider
 * of 002 on the MySQL 8.4 test database (FR-001-FR-007, US1.1-US1.8). Workshop targets are checked by the real
 * provider of 003.2 ({@code workshopoperations}); unknown targets are refused, never accepted unchecked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class PromotionCreateContractIT {

    private static final String PROGRAMS = "/api/v1/promotion-programs";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private TransactionTemplate transaction;
    @Autowired private AccountRepository accounts;
    @Autowired private ProductCategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @MockBean private TokenVerificationService tokens;

    private String manager;
    private long productId;
    private long variantId;
    private long otherVariantId;
    private long deletedProductId;

    @BeforeEach
    void setUp() {
        manager = signIn(AccountRole.MANAGER, AccountStatus.ACTIVE);
        String suffix = UUID.randomUUID().toString();
        transaction.executeWithoutResult(status -> {
            ProductCategory category = categories.saveAndFlush(ProductCategory.create("Loyalty IT " + suffix, null));
            Product product = products.saveAndFlush(Product.draft("Nhẫn bạc", "Mô tả", category.getId()));
            productId = product.getId();
            variantId = variants.saveAndFlush(ProductVariant.create(productId, 12, BigInteger.ONE)).getId();
            Product other = products.saveAndFlush(Product.draft("Nhẫn vàng", "Mô tả", category.getId()));
            otherVariantId = variants.saveAndFlush(ProductVariant.create(other.getId(), 13, BigInteger.ONE)).getId();
            Product deleted = products.saveAndFlush(Product.draft("Nhẫn cũ", "Mô tả", category.getId()));
            deleted.softDelete("test-staff", Instant.now());
            deletedProductId = deleted.getId();
        });
    }

    /** A real account row plus a verified token for it; only the Firebase signature check is replaced. */
    private String signIn(AccountRole role, AccountStatus status) {
        String uid = "loyalty-it-" + UUID.randomUUID();
        accounts.save(Account.builder().firebaseUid(uid).loginEmail(uid + "@example.test").role(role).status(status)
                .build());
        String token = "token-" + uid;
        when(tokens.verify(token)).thenReturn(new VerifiedIdentity(uid, uid + "@example.test", true, Instant.now()));
        return "Bearer " + token;
    }

    private static String code() {
        return "IT" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private String productVoucher(String code, String extra) {
        return """
                {"kind":"VOUCHER","scope":"PRODUCT","name":"Giảm 10%%","code":"%s",
                 "startAt":"2026-11-01T00:00:00Z","endAt":"2026-12-01T00:00:00Z",
                 "discount":{"kind":"PERCENT","percentage":"10","maxDiscountAmount":"50000"},
                 "minimumEligibleAmount":"500000","allowPoints":true,
                 "targets":{"productIds":["%d"]},"totalLimit":"100","perMemberLimit":"2"%s}"""
                .formatted(code, productId, extra);
    }

    private String autoDiscount(String extra) {
        return """
                {"kind":"AUTO_DISCOUNT","scope":"PRODUCT","name":"Giảm tự động",
                 "startAt":"2026-11-01T00:00:00Z","endAt":"2026-12-01T00:00:00Z",
                 "discount":{"kind":"FIXED","amount":"20000","maxDiscountAmount":"20000"},
                 "minimumEligibleAmount":"0","targets":{"productIds":["%d"]}%s}""".formatted(productId, extra);
    }

    private MvcResult create(String bearer, UUID key, String body) throws Exception {
        var request = post(PROGRAMS).contentType(MediaType.APPLICATION_JSON).content(body);
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        if (key != null) {
            request.header("Idempotency-Key", key.toString());
        }
        return mvc.perform(request).andReturn();
    }

    private MvcResult perform(RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(body(result).path("code").asText()).isEqualTo(code);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    // ----- create and read back -----

    @Test
    void managerCreatesAProductVoucherAndReadsItBack() throws Exception {
        String code = code();
        MvcResult created = create(manager, UUID.randomUUID(), productVoucher("  " + code.toLowerCase() + " ", ""));
        assertThat(created.getResponse().getStatus()).as(created.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(created.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        JsonNode program = body(created);
        assertThat(program.path("kind").asText()).isEqualTo("VOUCHER");
        assertThat(program.path("scope").asText()).isEqualTo("PRODUCT");
        assertThat(program.path("code").asText()).isEqualTo(code.toLowerCase());
        assertThat(program.path("status").asText()).isEqualTo("ENABLED");
        assertThat(program.path("revision").asText()).isEqualTo("1");
        assertThat(program.path("discount").path("percentage").asText()).isEqualTo("10");
        assertThat(program.path("discount").path("maxDiscountAmount").asText()).isEqualTo("50000");
        assertThat(program.path("minimumEligibleAmount").asText()).isEqualTo("500000");
        assertThat(program.path("totalLimit").asText()).isEqualTo("100");
        assertThat(program.path("perMemberLimit").asText()).isEqualTo("2");
        assertThat(program.path("targets").path("productIds").get(0).asText()).isEqualTo(Long.toString(productId));
        assertThat(program.path("startAt").asText()).isEqualTo("2026-11-01T00:00:00Z");

        MvcResult read = perform(get(PROGRAMS + "/" + program.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, manager));
        assertThat(read.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(read)).isEqualTo(program);
    }

    @Test
    void automaticDiscountAlwaysAllowsPointsAndHasNoCodeOrQuota() throws Exception {
        MvcResult created = create(manager, UUID.randomUUID(), autoDiscount(""));
        assertThat(created.getResponse().getStatus()).as(created.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode program = body(created);
        assertThat(program.path("allowPoints").asBoolean()).isTrue();
        assertThat(program.path("code").isNull()).isTrue();
        assertThat(program.path("totalLimit").isNull()).isTrue();
        assertThat(program.path("perMemberLimit").isNull()).isTrue();

        assertError(create(manager, UUID.randomUUID(), autoDiscount(",\"allowPoints\":false")), 400, "INVALID_INPUT");
        assertError(create(manager, UUID.randomUUID(), autoDiscount(",\"code\":\"AUTO1\"")), 400, "INVALID_INPUT");
        assertError(create(manager, UUID.randomUUID(), autoDiscount(",\"totalLimit\":\"5\"")), 400, "INVALID_INPUT");
        assertError(create(manager, UUID.randomUUID(),
                autoDiscount("").replace("\"scope\":\"PRODUCT\"", "\"scope\":\"WORKSHOP\"")), 400, "INVALID_INPUT");
    }

    @Test
    void codeIsUniqueAfterTrimAndIgnoringCaseAcrossHistory() throws Exception {
        String code = code();
        assertThat(create(manager, UUID.randomUUID(), productVoucher(code, "")).getResponse().getStatus()).isEqualTo(201);
        assertError(create(manager, UUID.randomUUID(), productVoucher(" " + code.toLowerCase() + "  ", "")), 409,
                "CODE_EXISTS");
    }

    @Test
    void workshopTargetsAreCheckedByTheWorkshopProvider() throws Exception {
        String unknownSession = UUID.randomUUID().toString();
        String body = """
                {"kind":"VOUCHER","scope":"WORKSHOP","name":"Workshop","code":"%s",
                 "startAt":"2026-11-01T00:00:00Z","endAt":"2026-12-01T00:00:00Z",
                 "discount":{"kind":"FIXED","amount":"100000","maxDiscountAmount":"100000"},
                 "minimumEligibleAmount":"0","allowPoints":true,
                 "targets":{"packageIds":["999999999999"],"sessionIds":["%s"]},
                 "totalLimit":"10","perMemberLimit":"1"}""".formatted(code(), unknownSession);
        MvcResult result = create(manager, UUID.randomUUID(), body);
        assertError(result, 400, "INVALID_INPUT");
        assertThat(result.getResponse().getContentAsString())
                .contains("targets.packageIds[999999999999]", "targets.sessionIds[" + unknownSession + "]");
    }

    // ----- validation -----

    @Test
    void invalidValuesAreRejectedWithTheirField() throws Exception {
        String[][] cases = {
                {"\"endAt\":\"2026-12-01T00:00:00Z\"", "\"endAt\":\"2026-11-01T00:00:00Z\""},
                {"\"percentage\":\"10\"", "\"percentage\":\"0\""},
                {"\"percentage\":\"10\"", "\"percentage\":\"100.000001\""},
                {"\"maxDiscountAmount\":\"50000\"", "\"maxDiscountAmount\":\"0\""},
                {"\"minimumEligibleAmount\":\"500000\"", "\"minimumEligibleAmount\":\"-1\""},
                {"\"totalLimit\":\"100\"", "\"totalLimit\":\"0\""},
                {"\"perMemberLimit\":\"2\"", "\"perMemberLimit\":\"1.5\""},
                {"\"name\":\"Giảm 10%\"", "\"name\":\"   \""},
                {"\"name\":\"Giảm 10%\"", "\"name\":\"" + "x".repeat(201) + "\""},
                {"\"percentage\":\"10\"", "\"percentage\":\"10\",\"amount\":\"5\""},
                {"\"startAt\":\"2026-11-01T00:00:00Z\"", "\"startAt\":\"01/11/2026\""},
                {"\"allowPoints\":true", "\"allowPoints\":\"yes\""},
        };
        for (String[] replace : cases) {
            String body = productVoucher(code(), "").replace(replace[0], replace[1]);
            assertError(create(manager, UUID.randomUUID(), body), 400, "INVALID_INPUT");
        }
        assertError(create(manager, UUID.randomUUID(), productVoucher(code(), ",\"status\":\"PAUSED\"")), 400,
                "INVALID_INPUT");
        assertError(create(manager, UUID.randomUUID(), productVoucher("TET\\n10", "")), 400, "INVALID_INPUT");
        assertError(create(manager, UUID.randomUUID(), productVoucher(code(), "").replace(",\"allowPoints\":true", "")),
                400, "INVALID_INPUT");
        assertError(create(manager, UUID.randomUUID(), "not json"), 400, "INVALID_INPUT");
    }

    @Test
    void targetsMustExistBelongToTheirProductAndNotBeDeleted() throws Exception {
        String missing = productVoucher(code(), "").replace("\"productIds\":[\"" + productId + "\"]",
                "\"productIds\":[\"999999999999\"]");
        assertError(create(manager, UUID.randomUUID(), missing), 400, "INVALID_INPUT");
        String deleted = productVoucher(code(), "").replace("\"productIds\":[\"" + productId + "\"]",
                "\"productIds\":[\"" + deletedProductId + "\"]");
        assertError(create(manager, UUID.randomUUID(), deleted), 400, "INVALID_INPUT");
        String foreignVariant = productVoucher(code(), "").replace("\"productIds\":[\"" + productId + "\"]",
                "\"productIds\":[\"" + productId + "\"],\"variantIds\":[\"" + otherVariantId + "\"]");
        assertError(create(manager, UUID.randomUUID(), foreignVariant), 400, "INVALID_INPUT");
        String ownVariant = productVoucher(code(), "").replace("\"productIds\":[\"" + productId + "\"]",
                "\"productIds\":[\"" + productId + "\"],\"variantIds\":[\"" + variantId + "\"]");
        assertThat(create(manager, UUID.randomUUID(), ownVariant).getResponse().getStatus())
                .as("an unpublished product and its size stay valid targets").isEqualTo(201);
        String empty = productVoucher(code(), "").replace("\"productIds\":[\"" + productId + "\"]", "");
        assertError(create(manager, UUID.randomUUID(), empty), 400, "INVALID_INPUT");
    }

    // ----- authority -----

    @Test
    void onlyAnActiveManagerMayCreate() throws Exception {
        assertThat(create(null, UUID.randomUUID(), productVoucher(code(), "")).getResponse().getStatus()).isEqualTo(401);
        for (AccountRole role : new AccountRole[] {AccountRole.MEMBER, AccountRole.STAFF, AccountRole.ADMIN}) {
            assertError(create(signIn(role, AccountStatus.ACTIVE), UUID.randomUUID(), productVoucher(code(), "")), 403,
                    "ACCESS_DENIED");
        }
        MvcResult suspended = create(signIn(AccountRole.MANAGER, AccountStatus.SUSPENDED), UUID.randomUUID(),
                productVoucher(code(), ""));
        assertThat(suspended.getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    void readsAreManagerOnlyToo() throws Exception {
        String member = signIn(AccountRole.MEMBER, AccountStatus.ACTIVE);
        assertError(perform(get(PROGRAMS).header(HttpHeaders.AUTHORIZATION, member)), 403, "ACCESS_DENIED");
        assertError(perform(get(PROGRAMS + "/1").header(HttpHeaders.AUTHORIZATION, member)), 403, "ACCESS_DENIED");
    }

    // ----- idempotency -----

    @Test
    void theSameKeyReplaysAndAChangedRequestConflicts() throws Exception {
        UUID key = UUID.randomUUID();
        String code = code();
        MvcResult first = create(manager, key, productVoucher(code, ""));
        MvcResult replay = create(manager, key, productVoucher(code, ""));
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(replay.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(replay).path("id")).isEqualTo(body(first).path("id"));
        assertError(create(manager, key, productVoucher(code, "").replace("\"totalLimit\":\"100\"",
                "\"totalLimit\":\"99\"")), 409, "IDEMPOTENCY_CONFLICT");
        assertError(create(manager, null, productVoucher(code(), "")), 400, "INVALID_INPUT");
    }

    // ----- list and detail -----

    @Test
    void listFiltersAndPagesNewestFirstWithAStableCursor() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(create(manager, UUID.randomUUID(), autoDiscount("")).getResponse().getStatus()).isEqualTo(201);
        }
        MvcResult firstPage = perform(get(PROGRAMS).param("scope", "PRODUCT").param("limit", "2")
                .header(HttpHeaders.AUTHORIZATION, manager));
        assertThat(firstPage.getResponse().getStatus()).isEqualTo(200);
        JsonNode page = body(firstPage);
        assertThat(page.path("items")).hasSize(2);
        String cursor = page.path("nextCursor").asText();
        assertThat(cursor).isNotBlank();
        JsonNode next = body(perform(get(PROGRAMS).param("scope", "PRODUCT").param("limit", "2").param("cursor", cursor)
                .header(HttpHeaders.AUTHORIZATION, manager)));
        long firstLast = page.path("items").get(1).path("id").asLong();
        assertThat(next.path("items").get(0).path("id").asLong()).isLessThan(firstLast);
        for (JsonNode item : next.path("items")) {
            assertThat(item.path("scope").asText()).isEqualTo("PRODUCT");
        }
        assertError(perform(get(PROGRAMS).param("limit", "101").header(HttpHeaders.AUTHORIZATION, manager)), 400,
                "INVALID_INPUT");
        assertError(perform(get(PROGRAMS).param("cursor", "not-a-cursor").header(HttpHeaders.AUTHORIZATION, manager)),
                400, "INVALID_INPUT");
        assertError(perform(get(PROGRAMS).param("status", "EXPIRED").header(HttpHeaders.AUTHORIZATION, manager)), 400,
                "INVALID_INPUT");
    }

    @Test
    void anUnknownProgramIsNotFound() throws Exception {
        assertError(perform(get(PROGRAMS + "/9223372036854775807").header(HttpHeaders.AUTHORIZATION, manager)), 404,
                "PROMOTION_NOT_FOUND");
        assertError(perform(get(PROGRAMS + "/abc").header(HttpHeaders.AUTHORIZATION, manager)), 400, "INVALID_INPUT");
    }
}
