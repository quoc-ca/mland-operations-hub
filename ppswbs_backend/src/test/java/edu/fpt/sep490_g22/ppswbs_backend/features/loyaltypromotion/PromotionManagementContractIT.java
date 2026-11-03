package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.MemberProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionRevisionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * T041: updatePromotionProgram, pausePromotionProgram and resumePromotionProgram through the real chains
 * (FR-006, FR-009, FR-010, US2.2-US2.8). Edits create a new immutable revision for new transactions only; code,
 * scope, kind and targets never change; limits never drop below what is used plus held; pause and resume keep the
 * same programme, code, counters and window.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class PromotionManagementContractIT {

    private static final String PROGRAMS = "/api/v1/promotion-programs";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private TransactionTemplate transaction;
    @Autowired private AccountRepository accounts;
    @Autowired private ProductCategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private MemberProgramRepository memberPrograms;
    @MockBean private TokenVerificationService tokens;

    private String manager;
    private long productId;

    @BeforeEach
    void setUp() {
        manager = signIn(AccountRole.MANAGER);
        transaction.executeWithoutResult(status -> {
            ProductCategory category = categories.saveAndFlush(ProductCategory.create("Maint IT " + UUID.randomUUID(), null));
            productId = products.saveAndFlush(Product.draft("Nhẫn bạc", "Mô tả", category.getId())).getId();
        });
    }

    private String signIn(AccountRole role) {
        String uid = "loyalty-maint-it-" + UUID.randomUUID();
        accounts.save(Account.builder().firebaseUid(uid).loginEmail(uid + "@example.test").role(role)
                .status(AccountStatus.ACTIVE).build());
        String token = "token-" + uid;
        when(tokens.verify(token)).thenReturn(new VerifiedIdentity(uid, uid + "@example.test", true, Instant.now()));
        return "Bearer " + token;
    }

    private MvcResult send(MockHttpServletRequestBuilder request, String bearer, UUID key, String body) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(body);
        }
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        if (key != null) {
            request.header("Idempotency-Key", key.toString());
        }
        return mvc.perform(request).andReturn();
    }

    private JsonNode ok(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        return json.readTree(result.getResponse().getContentAsString());
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(json.readTree(result.getResponse().getContentAsString()).path("code").asText()).isEqualTo(code);
    }

    private JsonNode createVoucher(long total, long perMember) throws Exception {
        String code = "MT" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String body = """
                {"kind":"VOUCHER","scope":"PRODUCT","name":"Voucher","code":"%s",
                 "startAt":"2026-11-01T00:00:00Z","endAt":"2026-12-01T00:00:00Z",
                 "discount":{"kind":"PERCENT","percentage":"10","maxDiscountAmount":"50000"},
                 "minimumEligibleAmount":"0","allowPoints":true,"targets":{"productIds":["%d"]},
                 "totalLimit":"%d","perMemberLimit":"%d"}""".formatted(code, productId, total, perMember);
        return ok(send(post(PROGRAMS), manager, UUID.randomUUID(), body), 201);
    }

    private JsonNode createAuto() throws Exception {
        String body = """
                {"kind":"AUTO_DISCOUNT","scope":"PRODUCT","name":"Auto",
                 "startAt":"2026-11-01T00:00:00Z","endAt":"2026-12-01T00:00:00Z",
                 "discount":{"kind":"FIXED","amount":"20000","maxDiscountAmount":"20000"},
                 "minimumEligibleAmount":"0","targets":{"productIds":["%d"]}}""".formatted(productId);
        return ok(send(post(PROGRAMS), manager, UUID.randomUUID(), body), 201);
    }

    private MvcResult update(JsonNode program, String fields) throws Exception {
        return send(patch(PROGRAMS + "/" + program.path("id").asText()), manager, UUID.randomUUID(),
                "{\"expectedVersion\":\"" + program.path("version").asText() + "\"" + fields + "}");
    }

    private MvcResult pause(JsonNode program) throws Exception {
        return send(post(PROGRAMS + "/" + program.path("id").asText() + "/pauses"), manager, UUID.randomUUID(),
                "{\"expectedVersion\":\"" + program.path("version").asText() + "\"}");
    }

    private MvcResult resume(JsonNode program) throws Exception {
        return send(post(PROGRAMS + "/" + program.path("id").asText() + "/resumptions"), manager, UUID.randomUUID(),
                "{\"expectedVersion\":\"" + program.path("version").asText() + "\"}");
    }

    // ----- edits -----

    @Test
    void anEditCreatesANewRevisionAndKeepsTheOldTermsUnchanged() throws Exception {
        JsonNode created = createVoucher(100, 2);
        long programId = created.path("id").asLong();
        JsonNode edited = ok(update(created, ",\"name\":\"Voucher mới\",\"discount\":{\"kind\":\"FIXED\","
                + "\"amount\":\"30000\",\"maxDiscountAmount\":\"30000\"},\"endAt\":\"2026-12-15T00:00:00Z\""), 200);

        assertThat(edited.path("revision").asText()).isEqualTo("2");
        assertThat(edited.path("name").asText()).isEqualTo("Voucher mới");
        assertThat(edited.path("discount").path("kind").asText()).isEqualTo("FIXED");
        assertThat(edited.path("endAt").asText()).isEqualTo("2026-12-15T00:00:00Z");
        assertThat(edited.path("startAt").asText()).as("unsent fields are kept").isEqualTo("2026-11-01T00:00:00Z");
        assertThat(edited.path("code").asText()).isEqualTo(created.path("code").asText());
        assertThat(edited.path("targets")).isEqualTo(created.path("targets"));
        assertThat(edited.path("version").asLong()).isGreaterThan(created.path("version").asLong());

        PromotionRevision first = revisions.findByProgramIdOrderByRevisionNoAsc(programId).get(0);
        assertThat(first.getName()).as("revision 1 is immutable").isEqualTo("Voucher");
        assertThat(first.getPercent()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void codeScopeKindAndTargetsCannotChange() throws Exception {
        JsonNode created = createVoucher(100, 2);
        for (String field : new String[] {",\"code\":\"OTHER1\"", ",\"scope\":\"WORKSHOP\"", ",\"kind\":\"AUTO_DISCOUNT\"",
                ",\"targets\":{\"productIds\":[\"" + productId + "\"]}", ",\"status\":\"PAUSED\""}) {
            assertError(update(created, field), 400, "INVALID_INPUT");
        }
        assertError(send(patch(PROGRAMS + "/" + created.path("id").asText()), manager, UUID.randomUUID(),
                "{\"expectedVersion\":\"" + created.path("version").asText() + "\"}"), 400, "INVALID_INPUT");
    }

    @Test
    void invalidEditsAreRejectedAndNothingChanges() throws Exception {
        JsonNode created = createVoucher(100, 2);
        assertError(update(created, ",\"endAt\":\"2026-10-01T00:00:00Z\""), 400, "INVALID_INPUT");
        assertError(update(created, ",\"name\":\"  \""), 400, "INVALID_INPUT");
        assertError(update(created, ",\"discount\":{\"kind\":\"PERCENT\",\"percentage\":\"0\",\"maxDiscountAmount\":\"1\"}"),
                400, "INVALID_INPUT");
        assertError(update(created, ",\"totalLimit\":\"0\""), 400, "INVALID_INPUT");
        JsonNode now = ok(send(get(PROGRAMS + "/" + created.path("id").asText()), manager, null, null), 200);
        assertThat(now).isEqualTo(created);
    }

    @Test
    void automaticDiscountsKeepPointsAndHaveNoQuota() throws Exception {
        JsonNode auto = createAuto();
        assertError(update(auto, ",\"allowPoints\":false"), 400, "INVALID_INPUT");
        assertError(update(auto, ",\"totalLimit\":\"5\""), 400, "INVALID_INPUT");
        assertThat(ok(update(auto, ",\"allowPoints\":true,\"name\":\"Auto 2\""), 200).path("allowPoints").asBoolean())
                .isTrue();
    }

    @Test
    void aStaleVersionIsRejected() throws Exception {
        JsonNode created = createVoucher(100, 2);
        ok(update(created, ",\"name\":\"First edit\""), 200);
        assertError(update(created, ",\"name\":\"Second edit\""), 409, "STALE_VERSION");
    }

    // ----- limits (US2.3, US2.6) -----

    @Test
    void theTotalLimitCannotDropBelowUsedPlusHeld() throws Exception {
        JsonNode created = createVoucher(20, 20);
        long programId = created.path("id").asLong();
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.findById(programId).orElseThrow();
            for (int i = 0; i < 8; i++) {
                program.hold();
            }
            for (int i = 0; i < 6; i++) {
                program.consume();
            }
        });
        JsonNode current = ok(send(get(PROGRAMS + "/" + programId), manager, null, null), 200);
        assertError(update(current, ",\"totalLimit\":\"7\""), 409, "QUOTA_LIMIT_CONFLICT");
        JsonNode accepted = ok(update(current, ",\"totalLimit\":\"8\""), 200);
        assertThat(accepted.path("totalLimit").asText()).isEqualTo("8");
        PromotionProgram program = programs.findById(programId).orElseThrow();
        assertThat(program.getHeldCount()).as("holds are never removed to force a save").isEqualTo(2);
        assertThat(program.getConsumedCount()).isEqualTo(6);
    }

    @Test
    void thePerMemberLimitCannotDropBelowAnyMembersUsedPlusHeld() throws Exception {
        JsonNode created = createVoucher(100, 3);
        long programId = created.path("id").asLong();
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.findById(programId).orElseThrow();
            MemberProgram counters = MemberProgram.create(programId, LoyaltyTestSupport.MEMBER_ID);
            program.hold();
            counters.hold(3);
            program.hold();
            counters.hold(3);
            counters.consume();
            program.consume();
            memberPrograms.saveAndFlush(counters);
        });
        JsonNode current = ok(send(get(PROGRAMS + "/" + programId), manager, null, null), 200);
        assertError(update(current, ",\"perMemberLimit\":\"1\""), 409, "QUOTA_LIMIT_CONFLICT");
        assertThat(ok(update(current, ",\"perMemberLimit\":\"2\""), 200).path("perMemberLimit").asText()).isEqualTo("2");
    }

    // ----- configuration version -----

    @Test
    void salesBetweenLoadAndSaveDoNotMakeAnEditStale() throws Exception {
        JsonNode loaded = createVoucher(20, 5);
        long programId = loaded.path("id").asLong();
        // Checkout holds and settles uses while the Manager is editing: counters change, configuration does not.
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.findById(programId).orElseThrow();
            program.hold();
            program.hold();
            program.consume();
        });
        JsonNode edited = ok(update(loaded, ",\"name\":\"Edited during sales\""), 200);
        assertThat(edited.path("version").asLong()).as("one step per configuration change")
                .isEqualTo(loaded.path("version").asLong() + 1);
        JsonNode paused = ok(pause(edited), 200);
        assertThat(paused.path("version").asLong()).isEqualTo(edited.path("version").asLong() + 1);
        assertError(update(loaded, ",\"name\":\"Late edit\""), 409, "STALE_VERSION");
    }

    @Test
    void aNewProgrammeStartsAtConfigurationVersionZero() throws Exception {
        assertThat(createVoucher(10, 2).path("version").asText()).isEqualTo("0");
        assertThat(createAuto().path("version").asText()).isEqualTo("0");
    }

    // ----- pause and resume (US2.8) -----

    @Test
    void pauseAndResumeKeepTheSameProgrammeCodeCountersAndWindow() throws Exception {
        JsonNode created = createVoucher(10, 2);
        long programId = created.path("id").asLong();
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.findById(programId).orElseThrow();
            program.hold();
            program.consume();
        });
        JsonNode current = ok(send(get(PROGRAMS + "/" + programId), manager, null, null), 200);
        JsonNode paused = ok(pause(current), 200);
        assertThat(paused.path("status").asText()).isEqualTo("PAUSED");
        assertError(pause(paused), 409, "BENEFIT_STATE_CONFLICT");

        JsonNode resumed = ok(resume(paused), 200);
        assertThat(resumed.path("status").asText()).isEqualTo("ENABLED");
        assertThat(resumed.path("id")).isEqualTo(created.path("id"));
        assertThat(resumed.path("code")).isEqualTo(created.path("code"));
        assertThat(resumed.path("revision")).as("no new terms").isEqualTo(created.path("revision"));
        assertThat(resumed.path("startAt")).isEqualTo(created.path("startAt"));
        assertThat(resumed.path("endAt")).as("not extended").isEqualTo(created.path("endAt"));
        assertThat(programs.findById(programId).orElseThrow().getConsumedCount()).as("not reset").isEqualTo(1);
        assertError(resume(resumed), 409, "BENEFIT_STATE_CONFLICT");
        assertError(pause(current), 409, "STALE_VERSION");
    }

    @Test
    void aRepeatedCommandReplaysAndAChangedOneConflicts() throws Exception {
        JsonNode created = createVoucher(10, 2);
        UUID key = UUID.randomUUID();
        String path = PROGRAMS + "/" + created.path("id").asText() + "/pauses";
        String body = "{\"expectedVersion\":\"" + created.path("version").asText() + "\"}";
        JsonNode first = ok(send(post(path), manager, key, body), 200);
        JsonNode replay = ok(send(post(path), manager, key, body), 200);
        assertThat(replay.path("status").asText()).isEqualTo("PAUSED");
        assertThat(replay.path("version")).as("no second transition").isEqualTo(first.path("version"));
        assertError(send(post(path), manager, key, "{\"expectedVersion\":\"999\"}"), 409, "IDEMPOTENCY_CONFLICT");
        assertError(send(post(path), manager, null, body), 400, "INVALID_INPUT");
    }

    // ----- authority and lookup -----

    @Test
    void onlyAManagerMaintainsProgrammes() throws Exception {
        JsonNode created = createVoucher(10, 2);
        String id = created.path("id").asText();
        String body = "{\"expectedVersion\":\"" + created.path("version").asText() + "\",\"name\":\"x\"}";
        for (AccountRole role : new AccountRole[] {AccountRole.MEMBER, AccountRole.STAFF, AccountRole.ADMIN}) {
            String bearer = signIn(role);
            assertError(send(patch(PROGRAMS + "/" + id), bearer, UUID.randomUUID(), body), 403, "ACCESS_DENIED");
            assertError(send(post(PROGRAMS + "/" + id + "/pauses"), bearer, UUID.randomUUID(), body), 403, "ACCESS_DENIED");
            assertError(send(get(PROGRAMS + "/" + id + "/usage"), bearer, null, null), 403, "ACCESS_DENIED");
        }
        assertThat(send(patch(PROGRAMS + "/" + id), null, UUID.randomUUID(), body).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void anUnknownProgrammeIsNotFound() throws Exception {
        assertError(send(patch(PROGRAMS + "/9223372036854775807"), manager, UUID.randomUUID(),
                "{\"expectedVersion\":\"0\",\"name\":\"x\"}"), 404, "PROMOTION_NOT_FOUND");
        assertError(send(post(PROGRAMS + "/9223372036854775807/resumptions"), manager, UUID.randomUUID(),
                "{\"expectedVersion\":\"0\"}"), 404, "PROMOTION_NOT_FOUND");
        assertError(send(get(PROGRAMS + "/9223372036854775807/usage"), manager, null, null), 404, "PROMOTION_NOT_FOUND");
    }
}
