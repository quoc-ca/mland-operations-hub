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
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Scope warnings on getPromotionProgram (spec clarification 2026-10-26): products in a product programme's scope that
 * became hidden/unpublished or soft-deleted after creation are listed for the Manager; the programme keeps running.
 * Uses the real 002 adapter and products on MySQL. Write responses carry no target check.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(LoyaltyTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class PromotionTargetWarningIT {

    private static final String PROGRAMS = "/api/v1/promotion-programs";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private AccountRepository accounts;
    @Autowired private CatalogueFlowFixture catalogue;
    @Autowired private DeleteProductService deletes;
    @MockBean private TokenVerificationService tokens;

    private String manager;

    @BeforeEach
    void signIn() {
        String uid = "loyalty-warn-it-" + UUID.randomUUID();
        accounts.save(Account.builder().firebaseUid(uid).loginEmail(uid + "@example.test").role(AccountRole.MANAGER)
                .status(AccountStatus.ACTIVE).build());
        when(tokens.verify("token-" + uid)).thenReturn(new VerifiedIdentity(uid, uid + "@example.test", true, Instant.now()));
        manager = "Bearer token-" + uid;
    }

    @Test
    void hiddenAndDeletedProductsInTheScopeAreWarnedWhileThePublicOneIsNot() throws Exception {
        long published = catalogue.publishedProduct("warn-staff", "warn-manager");
        long draft = catalogue.draftProduct("warn-staff");
        long laterDeleted = catalogue.draftProduct("warn-staff");
        String body = """
                {"kind":"AUTO_DISCOUNT","scope":"PRODUCT","name":"Auto",
                 "startAt":"2026-11-01T00:00:00Z","endAt":"2026-12-01T00:00:00Z",
                 "discount":{"kind":"FIXED","amount":"20000","maxDiscountAmount":"20000"},
                 "minimumEligibleAmount":"0","targets":{"productIds":["%d","%d","%d"]}}"""
                .formatted(published, draft, laterDeleted);
        MvcResult created = mvc.perform(post(PROGRAMS).header(HttpHeaders.AUTHORIZATION, manager)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(created.getResponse().getStatus()).as(created.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode program = json.readTree(created.getResponse().getContentAsString());
        assertThat(program.has("targetCheck")).as("write responses carry no target check").isFalse();

        deletes.delete("warn-staff", UUID.randomUUID(), Long.toString(laterDeleted), catalogue.productVersion(laterDeleted));

        MvcResult read = mvc.perform(get(PROGRAMS + "/" + program.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, manager)).andReturn();
        assertThat(read.getResponse().getStatus()).isEqualTo(200);
        JsonNode detail = json.readTree(read.getResponse().getContentAsString());
        assertThat(detail.path("targetCheck").asText()).isEqualTo("CHECKED");
        assertThat(detail.path("status").asText()).as("the programme keeps running").isEqualTo("ENABLED");
        List<String> warnings = new ArrayList<>();
        detail.path("targetWarnings").forEach(item ->
                warnings.add(item.path("dimension").asText() + ":" + item.path("targetKey").asText() + ":" + item.path("state").asText()));
        assertThat(warnings).containsExactlyInAnyOrder("PRODUCT:" + draft + ":NOT_PUBLIC", "PRODUCT:" + laterDeleted + ":DELETED");
    }
}
