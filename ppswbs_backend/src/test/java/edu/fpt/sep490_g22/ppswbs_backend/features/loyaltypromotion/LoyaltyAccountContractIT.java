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
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.AcceptedBenefit;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LedgerEntryKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyRegistry;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyWallet;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.PointLedger;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.SourceKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.support.LoyaltyTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
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

/**
 * T036: getOwnLoyaltyAccount and listOwnLoyaltyTransactions (FR-023, FR-031, FR-040, US6.4, US6.8). A Member sees
 * only their own wallet and history; the member id always comes from the verified actor, never from the request.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
class LoyaltyAccountContractIT {

    private static final String ACCOUNT = "/api/v1/members/me/loyalty-accounts/current";
    private static final String HISTORY = "/api/v1/members/me/loyalty-transactions";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private TransactionTemplate transaction;
    @Autowired private AccountRepository accounts;
    @Autowired private LoyaltyWalletRepository wallets;
    @Autowired private AcceptedBenefitRepository benefits;
    @Autowired private PointLedgerRepository ledger;
    @Autowired private LoyaltyRegistryRepository registry;
    @MockBean private TokenVerificationService tokens;

    private record Actor(long accountId, String bearer) {
    }

    private Actor signIn(AccountRole role) {
        String uid = "loyalty-account-it-" + UUID.randomUUID();
        Account account = accounts.save(Account.builder().firebaseUid(uid).loginEmail(uid + "@example.test").role(role)
                .status(AccountStatus.ACTIVE).build());
        String token = "token-" + uid;
        when(tokens.verify(token)).thenReturn(new VerifiedIdentity(uid, uid + "@example.test", true, Instant.now()));
        return new Actor(account.getId(), "Bearer " + token);
    }

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    /** Wallet with balance 200 and 50 held, plus {@code entries} history rows on separate benefits. */
    private void walletWithHistory(long memberId, int entries) {
        transaction.executeWithoutResult(status -> {
            long policyId = registry.findById(LoyaltyRegistry.SINGLETON_ID).orElseThrow().getCurrentPolicyId();
            LoyaltyWallet wallet = LoyaltyWallet.open(memberId);
            wallet.earn(n(200));
            wallet.hold(n(50));
            wallets.saveAndFlush(wallet);
            for (int i = 0; i < entries; i++) {
                AcceptedBenefit benefit = benefits.saveAndFlush(AcceptedBenefit.accept(new AcceptedBenefit.Snapshot(
                        SourceKind.RETAIL, "acct-it-" + UUID.randomUUID(), memberId, Instant.now(),
                        Instant.now().plusSeconds(600), 1L, "digest", policyId, null, null, n(100_000),
                        BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, n(100_000), null, false), true));
                ledger.saveAndFlush(PointLedger.entry(LedgerEntryKind.EARN, memberId, benefit.getId(), n(10 + i),
                        n(200), n(50), Instant.parse("2026-10-27T00:00:00Z").plusSeconds(i), null));
            }
        });
    }

    private MvcResult perform(RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn();
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

    @Test
    void aMemberSeesBalanceHeldAvailableAndTheCurrentConversion() throws Exception {
        Actor member = signIn(AccountRole.MEMBER);
        walletWithHistory(member.accountId(), 0);
        JsonNode account = ok(perform(get(ACCOUNT).header(HttpHeaders.AUTHORIZATION, member.bearer())));
        assertThat(account.path("balancePoints").asText()).isEqualTo("200");
        assertThat(account.path("heldPoints").asText()).isEqualTo("50");
        assertThat(account.path("availablePoints").asText()).isEqualTo("150");
        assertThat(account.path("policy").path("redeemPointValue").asText()).isNotBlank();
        assertThat(account.path("policy").path("roundingMode").asText()).isEqualTo("FLOOR");
    }

    @Test
    void aMemberWithoutAWalletReadsZeroAndNoWalletIsCreated() throws Exception {
        Actor member = signIn(AccountRole.MEMBER);
        JsonNode account = ok(perform(get(ACCOUNT).header(HttpHeaders.AUTHORIZATION, member.bearer())));
        assertThat(account.path("balancePoints").asText()).isEqualTo("0");
        assertThat(account.path("heldPoints").asText()).isEqualTo("0");
        assertThat(account.path("availablePoints").asText()).isEqualTo("0");
        assertThat(wallets.findById(member.accountId())).isEmpty();
        JsonNode page = ok(perform(get(HISTORY).header(HttpHeaders.AUTHORIZATION, member.bearer())));
        assertThat(page.path("items")).isEmpty();
        assertThat(page.path("nextCursor").isNull()).isTrue();
    }

    @Test
    void historyIsOwnNewestFirstAndPagedWithAStableCursor() throws Exception {
        Actor member = signIn(AccountRole.MEMBER);
        Actor other = signIn(AccountRole.MEMBER);
        walletWithHistory(member.accountId(), 3);
        walletWithHistory(other.accountId(), 2);

        JsonNode first = ok(perform(get(HISTORY).param("limit", "2").header(HttpHeaders.AUTHORIZATION, member.bearer())));
        assertThat(first.path("items")).hasSize(2);
        JsonNode top = first.path("items").get(0);
        assertThat(top.path("kind").asText()).isEqualTo("EARN");
        assertThat(top.path("balanceChange").asText()).isEqualTo("12");
        assertThat(top.path("heldChange").asText()).isEqualTo("0");
        assertThat(top.path("sourceKind").asText()).isEqualTo("RETAIL");
        assertThat(top.path("sourceRef").asText()).startsWith("acct-it-");
        assertThat(top.path("originalDebitId").isNull()).isTrue();
        String cursor = first.path("nextCursor").asText();
        JsonNode second = ok(perform(get(HISTORY).param("limit", "2").param("cursor", cursor)
                .header(HttpHeaders.AUTHORIZATION, member.bearer())));
        assertThat(second.path("items")).hasSize(1);
        assertThat(second.path("items").get(0).path("balanceChange").asText()).isEqualTo("10");
        assertThat(second.path("nextCursor").isNull()).isTrue();

        JsonNode others = ok(perform(get(HISTORY).header(HttpHeaders.AUTHORIZATION, other.bearer())));
        assertThat(others.path("items")).as("never another Member's rows").hasSize(2);
    }

    @Test
    void theMemberIdCannotBeSuppliedByTheClient() throws Exception {
        Actor member = signIn(AccountRole.MEMBER);
        Actor other = signIn(AccountRole.MEMBER);
        walletWithHistory(other.accountId(), 1);
        JsonNode account = ok(perform(get(ACCOUNT).param("memberId", Long.toString(other.accountId()))
                .header(HttpHeaders.AUTHORIZATION, member.bearer())));
        assertThat(account.path("balancePoints").asText()).isEqualTo("0");
    }

    @Test
    void invalidPagingIsRejected() throws Exception {
        Actor member = signIn(AccountRole.MEMBER);
        assertError(perform(get(HISTORY).param("limit", "0").header(HttpHeaders.AUTHORIZATION, member.bearer())), 400,
                "INVALID_INPUT");
        assertError(perform(get(HISTORY).param("limit", "101").header(HttpHeaders.AUTHORIZATION, member.bearer())), 400,
                "INVALID_INPUT");
        assertError(perform(get(HISTORY).param("cursor", "%%%").header(HttpHeaders.AUTHORIZATION, member.bearer())), 400,
                "INVALID_INPUT");
    }

    @Test
    void onlyAnActiveMemberReadsOwnLoyalty() throws Exception {
        assertThat(perform(get(ACCOUNT)).getResponse().getStatus()).isEqualTo(401);
        assertThat(perform(get(HISTORY)).getResponse().getStatus()).isEqualTo(401);
        for (AccountRole role : new AccountRole[] {AccountRole.MANAGER, AccountRole.ADMIN, AccountRole.STAFF}) {
            Actor actor = signIn(role);
            assertError(perform(get(ACCOUNT).header(HttpHeaders.AUTHORIZATION, actor.bearer())), 403, "ACCESS_DENIED");
            assertError(perform(get(HISTORY).header(HttpHeaders.AUTHORIZATION, actor.bearer())), 403, "ACCESS_DENIED");
        }
    }
}
