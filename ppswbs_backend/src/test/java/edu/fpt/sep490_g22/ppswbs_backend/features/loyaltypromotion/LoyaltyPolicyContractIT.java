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
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.LoyaltyPolicyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyPolicy;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T031: getCurrentLoyaltyPolicy and updateCurrentLoyaltyPolicy through the real security chain and identity gate
 * (FR-024, FR-026, FR-027, FR-040, US8.1-US8.5). Each test ends with the initial policy 10000/100/10/10 as the
 * current one again (a new revision), so other tests on the shared database keep their expectations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyPolicyContractIT {

    private static final String POLICY = "/api/v1/loyalty-policies/current";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private AccountRepository accounts;
    @Autowired private LoyaltyPolicyRepository policies;
    @Autowired private AcceptedBenefitRepository benefits;
    @MockBean private TokenVerificationService tokens;

    private String manager;

    @BeforeEach
    void setUp() throws Exception {
        manager = signIn(AccountRole.MANAGER, AccountStatus.ACTIVE);
        restoreDefaults();
    }

    @AfterEach
    void tearDown() throws Exception {
        restoreDefaults();
    }

    private String signIn(AccountRole role, AccountStatus status) {
        String uid = "loyalty-policy-it-" + UUID.randomUUID();
        accounts.save(Account.builder().firebaseUid(uid).loginEmail(uid + "@example.test").role(role).status(status)
                .build());
        String token = "token-" + uid;
        when(tokens.verify(token)).thenReturn(new VerifiedIdentity(uid, uid + "@example.test", true, Instant.now()));
        return "Bearer " + token;
    }

    private JsonNode current() throws Exception {
        MvcResult result = mvc.perform(get(POLICY).header(HttpHeaders.AUTHORIZATION, manager)).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static String update(String version, String earn, String value, String min, String percent) {
        return """
                {"expectedVersion":"%s","earnUnitAmount":"%s","redeemPointValue":"%s",
                 "minRedeemPoints":"%s","maxRedeemPercent":"%s"}""".formatted(version, earn, value, min, percent);
    }

    private MvcResult send(String bearer, UUID key, String body) throws Exception {
        var request = put(POLICY).contentType(MediaType.APPLICATION_JSON).content(body);
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        if (key != null) {
            request.header("Idempotency-Key", key.toString());
        }
        return mvc.perform(request).andReturn();
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(json.readTree(result.getResponse().getContentAsString()).path("code").asText()).isEqualTo(code);
    }

    private void restoreDefaults() throws Exception {
        JsonNode now = current();
        if (!(now.path("earnUnitAmount").asText().equals("10000") && now.path("redeemPointValue").asText().equals("100")
                && now.path("minRedeemPoints").asText().equals("10") && now.path("maxRedeemPercent").asText().equals("10"))) {
            MvcResult reset = send(manager, UUID.randomUUID(),
                    update(now.path("version").asText(), "10000", "100", "10", "10"));
            assertThat(reset.getResponse().getStatus()).isEqualTo(200);
        }
    }

    @Test
    void theCurrentPolicyIsTheInitialConfiguration() throws Exception {
        JsonNode policy = current();
        assertThat(policy.path("earnUnitAmount").asText()).isEqualTo("10000");
        assertThat(policy.path("redeemPointValue").asText()).isEqualTo("100");
        assertThat(policy.path("minRedeemPoints").asText()).isEqualTo("10");
        assertThat(policy.path("maxRedeemPercent").asText()).isEqualTo("10");
        assertThat(policy.path("roundingMode").asText()).isEqualTo("FLOOR");
        assertThat(policy.path("id").asText()).matches("[1-9][0-9]*");
        assertThat(policy.path("version").asText()).matches("[0-9]+");
    }

    @Test
    void aManagerUpdateCreatesANewRevisionUsedOnlyByNewTransactions() throws Exception {
        JsonNode before = current();
        long oldPolicyId = before.path("id").asLong();
        // An accepted transaction recorded under the old policy.
        AcceptedBenefit accepted = benefits.saveAndFlush(AcceptedBenefit.accept(new AcceptedBenefit.Snapshot(
                SourceKind.RETAIL, "policy-it-" + UUID.randomUUID(), LoyaltyTestSupport.MEMBER_ID, Instant.now(),
                Instant.now().plusSeconds(600), 1L, "digest", oldPolicyId, null, null, BigInteger.valueOf(100_000),
                BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(100_000), null, false), true));

        MvcResult updated = send(manager, UUID.randomUUID(),
                update(before.path("version").asText(), "20000", "50", "20", "12.5"));
        assertThat(updated.getResponse().getStatus()).as(updated.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(updated.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        JsonNode after = json.readTree(updated.getResponse().getContentAsString());
        assertThat(after.path("id").asLong()).isNotEqualTo(oldPolicyId);
        assertThat(after.path("version").asLong()).isGreaterThan(before.path("version").asLong());
        assertThat(after.path("maxRedeemPercent").asText()).isEqualTo("12.5");
        assertThat(current()).isEqualTo(after);

        LoyaltyPolicy old = policies.findById(oldPolicyId).orElseThrow();
        assertThat(old.getEarnUnitAmount()).as("old revision is immutable").isEqualTo(BigInteger.valueOf(10_000));
        assertThat(old.getMaxRedeemPercent()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(benefits.findById(accepted.getId()).orElseThrow().getPolicyId())
                .as("an accepted transaction keeps its policy").isEqualTo(oldPolicyId);
    }

    @Test
    void invalidValuesAreRejectedAndTheValidPolicyStays() throws Exception {
        JsonNode before = current();
        String version = before.path("version").asText();
        String[][] invalid = {
                {"0", "100", "10", "10"}, {"10000", "0", "10", "10"}, {"10000", "100", "0", "10"},
                {"10000", "100", "10", "0"}, {"10000", "100", "10", "100.000001"}, {"10000", "100", "10", "0.0000001"},
                {"-1", "100", "10", "10"}, {"10000", "100", "1.5", "10"}, {"1e4", "100", "10", "10"},
        };
        for (String[] values : invalid) {
            assertError(send(manager, UUID.randomUUID(), update(version, values[0], values[1], values[2], values[3])), 400,
                    "INVALID_INPUT");
        }
        assertError(send(manager, UUID.randomUUID(), update(version, "10000", "100", "10", "10")
                .replace("}", ",\"roundingMode\":\"CEIL\"}")), 400, "INVALID_INPUT");
        assertError(send(manager, UUID.randomUUID(), "{\"expectedVersion\":\"" + version + "\"}"), 400, "INVALID_INPUT");
        assertError(send(manager, UUID.randomUUID(), update(version, "10000", "100", "10", "10")
                .replace("\"10000\"", "10000")), 400, "INVALID_INPUT");
        assertThat(current()).isEqualTo(before);
        // The edge values of the ranges are accepted.
        assertThat(send(manager, UUID.randomUUID(), update(version, "1", "1", "1", "100")).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void aStaleVersionIsRejectedAndTheLatestIsKept() throws Exception {
        JsonNode before = current();
        String stale = before.path("version").asText();
        assertThat(send(manager, UUID.randomUUID(), update(stale, "15000", "100", "10", "10")).getResponse().getStatus())
                .isEqualTo(200);
        JsonNode latest = current();
        assertError(send(manager, UUID.randomUUID(), update(stale, "30000", "100", "10", "10")), 409, "STALE_VERSION");
        assertThat(current()).isEqualTo(latest);
    }

    @Test
    void theSameKeyReplaysAndAChangedRequestConflicts() throws Exception {
        String version = current().path("version").asText();
        UUID key = UUID.randomUUID();
        MvcResult first = send(manager, key, update(version, "12000", "100", "10", "10"));
        MvcResult replay = send(manager, key, update(version, "12000", "100", "10", "10"));
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(replay.getResponse().getContentAsString()).path("id"))
                .isEqualTo(json.readTree(first.getResponse().getContentAsString()).path("id"));
        assertError(send(manager, key, update(version, "13000", "100", "10", "10")), 409, "IDEMPOTENCY_CONFLICT");
        assertError(send(manager, null, update(version, "13000", "100", "10", "10")), 400, "INVALID_INPUT");
    }

    @Test
    void onlyAnActiveManagerReadsOrChangesThePolicy() throws Exception {
        String version = current().path("version").asText();
        assertThat(send(null, UUID.randomUUID(), update(version, "10000", "100", "10", "10")).getResponse().getStatus())
                .isEqualTo(401);
        assertThat(mvc.perform(get(POLICY)).andReturn().getResponse().getStatus()).isEqualTo(401);
        for (AccountRole role : new AccountRole[] {AccountRole.ADMIN, AccountRole.STAFF, AccountRole.MEMBER}) {
            String bearer = signIn(role, AccountStatus.ACTIVE);
            assertError(send(bearer, UUID.randomUUID(), update(version, "20000", "100", "10", "10")), 403, "ACCESS_DENIED");
            assertError(mvc.perform(get(POLICY).header(HttpHeaders.AUTHORIZATION, bearer)).andReturn(), 403,
                    "ACCESS_DENIED");
        }
        assertThat(current().path("earnUnitAmount").asText()).isEqualTo("10000");
    }
}
