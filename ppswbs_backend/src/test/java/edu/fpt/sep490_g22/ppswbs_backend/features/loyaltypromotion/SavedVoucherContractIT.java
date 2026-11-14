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
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionTargetRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.MemberProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionProgram;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionRevision;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PromotionTarget;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T066: listPublicPromotions, getPublicPromotion, listOwnSavedVouchers and saveOwnVoucher (FR-011-FR-013, US3.1-US3.6,
 * spec clarification 2026-10-26). Public cards show only active programmes and only products that are public now; a
 * product programme with no public product left is hidden from the public list but stays in the Member's saved list as
 * "no product available". Saving needs a Member, is idempotent and holds no quota.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class SavedVoucherContractIT {

    private static final String PUBLIC = "/api/v1/promotions";
    private static final String SAVED = "/api/v1/members/me/saved-vouchers";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private TransactionTemplate transaction;
    @Autowired private CatalogueFlowFixture catalogue;
    @Autowired private AccountRepository accounts;
    @Autowired private PromotionProgramRepository programs;
    @Autowired private PromotionRevisionRepository revisions;
    @Autowired private PromotionTargetRepository targets;
    @Autowired private MemberProgramRepository memberPrograms;
    @Autowired private ProductVariantRepository variants;
    @MockBean private TokenVerificationService tokens;

    private long publicProduct;
    private long draftProduct;

    @BeforeEach
    void setUp() {
        publicProduct = catalogue.publishedProduct(LoyaltyTestSupport.STAFF, LoyaltyTestSupport.MANAGER);
        draftProduct = catalogue.draftProduct(LoyaltyTestSupport.STAFF);
    }

    private String signIn(AccountRole role) {
        String uid = "loyalty-saved-it-" + UUID.randomUUID();
        accounts.save(Account.builder().firebaseUid(uid).loginEmail(uid + "@example.test").role(role)
                .status(AccountStatus.ACTIVE).build());
        String token = "token-" + uid;
        when(tokens.verify(token)).thenReturn(new VerifiedIdentity(uid, uid + "@example.test", true, Instant.now()));
        return "Bearer " + token;
    }

    /** A programme active now (or shifted by the given days), targeting the given products. */
    private long program(ProgramKind kind, TargetScope scope, long startOffsetDays, long endOffsetDays, long... productIds) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String code = "SV" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return transaction.execute(status -> {
            PromotionProgram program = programs.saveAndFlush(kind == ProgramKind.VOUCHER
                    ? PromotionProgram.voucher(scope, code, code, 10, 2, now, 1L)
                    : PromotionProgram.autoDiscount(TargetScope.PRODUCT, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1, kind,
                    "Ưu đãi", now.plus(startOffsetDays, ChronoUnit.DAYS), now.plus(endOffsetDays, ChronoUnit.DAYS),
                    DiscountTerms.fixed(BigInteger.valueOf(20_000), BigInteger.valueOf(20_000), BigInteger.ZERO), true,
                    now, 1L));
            if (scope == TargetScope.PRODUCT) {
                for (long productId : productIds) {
                    targets.save(PromotionTarget.create(revision.getId(), scope, TargetDimension.PRODUCT,
                            Long.toString(productId)));
                }
            } else {
                targets.save(PromotionTarget.create(revision.getId(), scope, TargetDimension.PACKAGE, "1"));
            }
            program.pointToRevision(revision.getId());
            return program.getId();
        });
    }

    /** An active product voucher with product AND size targets. */
    private long sizeVoucher(List<Long> productIds, List<Long> variantIds) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String code = "SV" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return transaction.execute(status -> {
            PromotionProgram program = programs.saveAndFlush(
                    PromotionProgram.voucher(TargetScope.PRODUCT, code, code, 10, 2, now, 1L));
            PromotionRevision revision = revisions.saveAndFlush(PromotionRevision.create(program.getId(), 1,
                    ProgramKind.VOUCHER, "Ưu đãi size", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS),
                    DiscountTerms.fixed(BigInteger.valueOf(20_000), BigInteger.valueOf(20_000), BigInteger.ZERO), true,
                    now, 1L));
            productIds.forEach(id -> targets.save(PromotionTarget.create(revision.getId(), TargetScope.PRODUCT,
                    TargetDimension.PRODUCT, Long.toString(id))));
            variantIds.forEach(id -> targets.save(PromotionTarget.create(revision.getId(), TargetScope.PRODUCT,
                    TargetDimension.VARIANT, Long.toString(id))));
            program.pointToRevision(revision.getId());
            return program.getId();
        });
    }

    private long firstSize(long productId) {
        return variants.findByProductIdInOrderByProductIdAscRingSizeAsc(java.util.Set.of(productId)).get(0).getId();
    }

    private long memberId(String bearer) {
        String uid = bearer.substring("Bearer token-".length());
        return accounts.findByFirebaseUid(uid).orElseThrow().getId();
    }

    /** The Member's row as if they had saved the voucher earlier, while it was public. */
    private void savedEarlier(long programId, String bearer) {
        long memberId = memberId(bearer);
        transaction.executeWithoutResult(status -> {
            MemberProgram row = memberPrograms.saveAndFlush(MemberProgram.create(programId, memberId));
            row.save(Instant.now().truncatedTo(ChronoUnit.MICROS));
        });
    }

    private MvcResult call(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        return mvc.perform(request).andReturn();
    }

    private MvcResult save(long programId, String bearer) throws Exception {
        return call(put(SAVED + "/" + programId).header("Idempotency-Key", UUID.randomUUID().toString()), bearer);
    }

    private JsonNode ok(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        return json.readTree(result.getResponse().getContentAsString());
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(json.readTree(result.getResponse().getContentAsString()).path("code").asText()).isEqualTo(code);
    }

    /** Every public card across pages (the shared test database also holds other tests' programmes). */
    private List<JsonNode> allPublic(String scope) throws Exception {
        List<JsonNode> cards = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder request = get(PUBLIC).param("limit", "100");
            if (scope != null) {
                request.param("scope", scope);
            }
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode page = ok(call(request, null));
            page.path("items").forEach(cards::add);
            cursor = page.path("nextCursor").isNull() ? null : page.path("nextCursor").asText();
        } while (cursor != null);
        return cards;
    }

    private static JsonNode find(List<JsonNode> cards, long programId) {
        return cards.stream().filter(card -> card.path("id").asLong() == programId).findFirst().orElse(null);
    }

    // ----- public cards -----

    @Test
    void aGuestSeesActiveProgrammesWithOnlyTheirPublicProducts() throws Exception {
        long mixed = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, publicProduct, draftProduct);
        List<JsonNode> cards = allPublic("PRODUCT");
        JsonNode card = find(cards, mixed);
        assertThat(card).isNotNull();
        assertThat(card.path("targets").path("productIds")).hasSize(1);
        assertThat(card.path("targets").path("productIds").get(0).asText()).isEqualTo(Long.toString(publicProduct));
        assertThat(card.path("allowPoints").asBoolean()).isTrue();
        assertThat(card.path("code").asText()).startsWith("SV");
        assertThat(card.path("totalLimit").asText()).isEqualTo("10");

        JsonNode detail = ok(call(get(PUBLIC + "/" + mixed), null));
        assertThat(detail).isEqualTo(card);
    }

    @Test
    void programmesWithoutAPublicProductOrNotActiveAreNotListed() throws Exception {
        long hidden = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, draftProduct);
        long future = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, 1, 2, publicProduct);
        long ended = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -2, -1, publicProduct);
        long paused = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, publicProduct);
        transaction.executeWithoutResult(status -> programs.findById(paused).orElseThrow().pause());
        List<JsonNode> cards = allPublic(null);
        for (long id : new long[] {hidden, future, ended, paused}) {
            assertThat(find(cards, id)).as("programme " + id).isNull();
            assertError(call(get(PUBLIC + "/" + id), null), 404, "PROMOTION_NOT_FOUND");
        }
    }

    @Test
    void theScopeFilterSeparatesProductAndWorkshopCards() throws Exception {
        long workshop = program(ProgramKind.VOUCHER, TargetScope.WORKSHOP, -1, 1);
        assertThat(find(allPublic("WORKSHOP"), workshop)).isNotNull();
        assertThat(find(allPublic("PRODUCT"), workshop)).isNull();
        assertError(call(get(PUBLIC).param("scope", "OTHER"), null), 400, "INVALID_INPUT");
        assertError(call(get(PUBLIC).param("limit", "0"), null), 400, "INVALID_INPUT");
    }

    // ----- saved vouchers -----

    @Test
    void aMemberSavesIdempotentlyWithoutHoldingAnyUse() throws Exception {
        long voucher = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, publicProduct);
        String member = signIn(AccountRole.MEMBER);
        JsonNode first = ok(save(voucher, member));
        JsonNode again = ok(save(voucher, member));
        assertThat(first.path("availability").asText()).isEqualTo("ACTIVE");
        assertThat(first.path("promotion").path("id").asLong()).isEqualTo(voucher);
        assertThat(again.path("savedAt")).as("saving twice adds nothing").isEqualTo(first.path("savedAt"));
        PromotionProgram program = programs.findById(voucher).orElseThrow();
        assertThat(program.getHeldCount()).isZero();
        assertThat(program.getConsumedCount()).isZero();

        JsonNode page = ok(call(get(SAVED), member));
        assertThat(page.path("items")).hasSize(1);
        assertThat(page.path("items").get(0).path("promotion").path("id").asLong()).isEqualTo(voucher);
    }

    @Test
    void savedVouchersShowTheirCurrentAvailability() throws Exception {
        String member = signIn(AccountRole.MEMBER);
        long paused = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, publicProduct);
        long noProduct = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, draftProduct);
        long scheduled = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, 1, 2, publicProduct);
        long ended = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -2, -1, publicProduct);
        ok(save(paused, member));
        // Saved while they were public; only a public voucher can be saved now (L1).
        for (long id : new long[] {noProduct, scheduled, ended}) {
            savedEarlier(id, member);
        }
        transaction.executeWithoutResult(status -> programs.findById(paused).orElseThrow().pause());
        JsonNode page = ok(call(get(SAVED).param("limit", "10"), member));
        assertThat(page.path("items")).hasSize(4);
        java.util.Map<Long, String> availability = new java.util.HashMap<>();
        page.path("items").forEach(item -> availability.put(item.path("promotion").path("id").asLong(),
                item.path("availability").asText()));
        assertThat(availability).containsEntry(paused, "PAUSED").containsEntry(noProduct, "NO_PUBLIC_TARGET")
                .containsEntry(scheduled, "SCHEDULED").containsEntry(ended, "EXPIRED");
    }

    @Test
    void savedListsArePrivateAndPaged() throws Exception {
        String member = signIn(AccountRole.MEMBER);
        String other = signIn(AccountRole.MEMBER);
        for (int i = 0; i < 3; i++) {
            ok(save(program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, publicProduct), member));
        }
        JsonNode first = ok(call(get(SAVED).param("limit", "2"), member));
        assertThat(first.path("items")).hasSize(2);
        JsonNode second = ok(call(get(SAVED).param("limit", "2").param("cursor", first.path("nextCursor").asText()),
                member));
        assertThat(second.path("items")).hasSize(1);
        assertThat(second.path("nextCursor").isNull()).isTrue();
        assertThat(ok(call(get(SAVED), other)).path("items")).as("never another Member's vouchers").isEmpty();
    }

    @Test
    void onlyAMemberSavesAndOnlyVouchersCanBeSaved() throws Exception {
        long voucher = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, publicProduct);
        long auto = program(ProgramKind.AUTO_DISCOUNT, TargetScope.PRODUCT, -1, 1, publicProduct);
        assertThat(save(voucher, null).getResponse().getStatus()).as("a Guest is asked to sign in").isEqualTo(401);
        assertThat(call(get(SAVED), null).getResponse().getStatus()).isEqualTo(401);
        for (AccountRole role : new AccountRole[] {AccountRole.MANAGER, AccountRole.STAFF, AccountRole.ADMIN}) {
            assertError(save(voucher, signIn(role)), 403, "ACCESS_DENIED");
        }
        String member = signIn(AccountRole.MEMBER);
        assertError(save(auto, member), 404, "PROMOTION_NOT_FOUND");
        assertError(save(9_223_372_036_854_775_807L, member), 404, "PROMOTION_NOT_FOUND");
        assertError(call(put(SAVED + "/" + voucher), member), 400, "INVALID_INPUT");
    }

    // ----- review 2026-11-10 -----

    @Test
    void sizeTargetsThatLeftTheCatalogueLeaveNoPublicTarget() throws Exception {
        // M2: products [public, draft] AND only a size of the draft product. No line can match, so the card must not
        // show the public product alone (it would read as "every size of it").
        long voucher = sizeVoucher(List.of(publicProduct, draftProduct), List.of(firstSize(draftProduct)));
        assertThat(find(allPublic("PRODUCT"), voucher)).isNull();
        assertError(call(get(PUBLIC + "/" + voucher), null), 404, "PROMOTION_NOT_FOUND");
        String member = signIn(AccountRole.MEMBER);
        savedEarlier(voucher, member);
        JsonNode item = ok(call(get(SAVED), member)).path("items").get(0);
        assertThat(item.path("availability").asText()).isEqualTo("NO_PUBLIC_TARGET");

        // A public size keeps the card, listing only that size.
        long kept = sizeVoucher(List.of(publicProduct, draftProduct),
                List.of(firstSize(publicProduct), firstSize(draftProduct)));
        JsonNode card = find(allPublic("PRODUCT"), kept);
        assertThat(card).isNotNull();
        assertThat(card.path("targets").path("variantIds")).hasSize(1);
        assertThat(card.path("targets").path("variantIds").get(0).asText())
                .isEqualTo(Long.toString(firstSize(publicProduct)));
    }

    @Test
    void onlyAPublicVoucherCanBeSavedAndAnEarlierSaveStaysSaveable() throws Exception {
        // L1: knowing an ID of a paused, scheduled, ended or hidden voucher never reveals its code.
        String member = signIn(AccountRole.MEMBER);
        long paused = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, publicProduct);
        transaction.executeWithoutResult(status -> programs.findById(paused).orElseThrow().pause());
        long scheduled = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, 1, 2, publicProduct);
        long ended = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -2, -1, publicProduct);
        long hidden = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, draftProduct);
        for (long id : new long[] {paused, scheduled, ended, hidden}) {
            assertError(save(id, member), 404, "PROMOTION_NOT_FOUND");
        }
        assertThat(ok(call(get(SAVED), member)).path("items")).isEmpty();

        long voucher = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, publicProduct);
        ok(save(voucher, member));
        transaction.executeWithoutResult(status -> programs.findById(voucher).orElseThrow().pause());
        JsonNode again = ok(save(voucher, member));
        assertThat(again.path("availability").asText()).isEqualTo("PAUSED");
    }

    @Test
    void savedVouchersSayWhenNoUseIsLeft() throws Exception {
        // L2 / US3.5: all uses taken in total, or this Member's own limit reached (held uses count until released).
        String member = signIn(AccountRole.MEMBER);
        long full = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, publicProduct);
        long mine = program(ProgramKind.VOUCHER, TargetScope.PRODUCT, -1, 1, publicProduct);
        ok(save(full, member));
        ok(save(mine, member));
        long memberId = memberId(member);
        transaction.executeWithoutResult(status -> {
            PromotionProgram program = programs.findById(full).orElseThrow();
            for (int i = 0; i < 10; i++) {
                program.hold();
            }
            PromotionProgram limited = programs.findById(mine).orElseThrow();
            limited.hold();
            limited.hold();
            MemberProgram row = memberPrograms.findByProgramIdAndMemberId(mine, memberId).orElseThrow();
            row.hold(2);
            row.hold(2);
        });
        java.util.Map<Long, String> availability = new java.util.HashMap<>();
        ok(call(get(SAVED), member)).path("items").forEach(item -> availability.put(
                item.path("promotion").path("id").asLong(), item.path("availability").asText()));
        assertThat(availability).containsEntry(full, "NO_USES_LEFT").containsEntry(mine, "MEMBER_LIMIT_REACHED");
    }
}
