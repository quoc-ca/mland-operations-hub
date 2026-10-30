package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP transport + real transactional OTA services + isolated migrated MySQL. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import({OtaChannelManagementService.class, OtaChannelCatalogService.class, OtaChannelMappingService.class,
        OtaPriceTermsService.class, OtaSalesControlService.class, OtaReconciliationService.class,
        OtaSyncDeliveryService.class, OtaAuditCorrelationService.class, OtaAuthorizationService.class})
class OtaChannelControllerIT {
    private static final OtaMigrationTestSupport DB = OtaMigrationTestSupport.fromEnvironment().orElse(null);
    private static final String SCHEMA = initialize();
    private static final Instant FROM = Instant.parse("2035-01-01T00:00:00Z");
    @Autowired OtaChannelManagementService management;
    @Autowired OtaSalesControlService sales;
    @Autowired OtaAuthorizationService authorization;
    @Autowired OtaReconciliationService reconciliation;
    @Autowired OtaSyncDeliveryService delivery;
    @Autowired OtaPriceTermsService prices;
    @Autowired OtaChannelConnectionRepository connections;
    @Autowired OtaChannelMappingRepository mappings;
    @Autowired OtaChannelPriceSnapshotRepository snapshots;
    @Autowired OtaOutboundChangeRepository outbound;
    @Autowired OtaControlOutboxRepository controls;
    @Autowired OtaReadinessAssessmentRepository assessments;
    @Autowired OtaReconciliationDecisionRepository decisions;
    @Autowired OtaReconciliationWorkItemRepository items;
    @Autowired jakarta.persistence.EntityManager em;
    @MockBean AccountFacade accounts;
    @MockBean WorkshopOperationsFacade operations;
    @MockBean WorkshopOtaResyncFacade resync;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private MockMvc mvc;
    private OtaChannelConnection connection;
    private UUID session;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (DB == null) return;
        registry.add("spring.datasource.url", () -> DB.jdbcUrl(SCHEMA));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> System.getenv("WORKSHOP_OTA_TEST_JDBC_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("WORKSHOP_OTA_TEST_JDBC_PASSWORD"));
    }

    static String initialize() {
        if (DB == null) return null;
        try {
            String schema = DB.createDatabase();
            DB.flyway(schema, null).migrate();
            return schema;
        } catch (Exception exception) {
            try { DB.close(); } catch (Exception cleanup) { exception.addSuppressed(cleanup); }
            throw new IllegalStateException(exception);
        }
    }

    @AfterAll static void cleanup() throws Exception { if (DB != null) DB.close(); }

    /** Runs last: commits only fixtures in this class's disposable schema. */
    @Test @Order(Integer.MAX_VALUE)
    void recoveryCommitsAndReplaysAcrossIndependentTransactions() throws Exception {
        String reference = create().path("mappingId").asText();
        var mapping = mappings.findByPublicReference(reference).orElseThrow();
        mapping.startPreparationSync();
        mapping.suspendAfterSyncFailure();
        mappings.flush();
        var item = items.saveAndFlush(OtaReconciliationWorkItem.builder().mapping(mapping)
                .locationReference("42").category("SOURCE_VERSION_GAP").status("OPEN").build());
        Long itemId = item.getId();
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();

        var manager = new AccountSummary(91L, ActorRole.MANAGER, ActorStatus.ACTIVE, null, null, null);
        var admin = new AccountSummary(92L, ActorRole.ADMIN, ActorStatus.ACTIVE, null, null, null);
        var requested = reconciliation.resolve(itemId, "RESYNC_REQUESTED", "recover test gap", "durable-resync", manager);
        assertThat(reconciliation.resolve(itemId, "RESYNC_REQUESTED", "recover test gap", "durable-resync", manager))
                .isEqualTo(requested);
        assertThat(outbound.count()).isEqualTo(3);
        assertThat(decisions.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reconciliation.resolve(itemId, "ACCEPTED",
                "confirm recovery", "durable-accept", admin)).isInstanceOf(
                edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaMappingLifecycleException.class);
        assertThat(items.findById(itemId).orElseThrow().getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(decisions.count()).isEqualTo(1);
        for (var change : outbound.findAll()) {
            delivery.recordDeliveryResult(change.getId(), new OtaDeliveryResult(1, FROM, FROM.plusSeconds(1),
                    OtaDeliveryOutcome.DELIVERED, UUID.randomUUID().toString(), null));
        }
        var accepted = reconciliation.resolve(itemId, "ACCEPTED", "confirm recovery", "durable-accept", admin);
        assertThat(accepted.status()).isEqualTo("RESOLVED");
        assertThat(reconciliation.resolve(itemId, "ACCEPTED", "confirm recovery", "durable-accept", admin))
                .isEqualTo(accepted);
        assertThat(mappings.findByPublicReference(reference).orElseThrow().getStatus()).isEqualTo(OtaMappingStatus.SALES_CLOSED);
        assertThat(decisions.count()).isEqualTo(2);
        assertThat(decisions.findAll()).anySatisfy(decision -> {
            assertThat(decision.getActorAccountReference()).isEqualTo("91");
            assertThat(decision.getActionScope()).isEqualTo("RECONCILIATION");
            assertThat(decision.getLocationReference()).isEqualTo("42");
            assertThat(decision.getProviderCode()).isEqualTo("KLOOK");
        }).anySatisfy(decision -> {
            assertThat(decision.getActorAccountReference()).isEqualTo("92");
            assertThat(decision.getActionScope()).isEqualTo("RECOVERY");
            assertThat(decision.getOutcome()).isEqualTo("RESOLVED");
        });
    }

    @BeforeEach
    void setup() {
        Assumptions.assumeTrue(DB != null, "Requires isolated MySQL test configuration");
        ReflectionTestUtils.setField(authorization, "allowTestTokens", true);
        mvc = MockMvcBuilders.standaloneSetup(new OtaChannelController(authorization, management, sales),
                        new OtaReconciliationController(authorization, reconciliation))
                .setControllerAdvice(new OtaExceptionHandler()).addFilters(new CorrelationIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        for (ActorRole role : ActorRole.values()) {
            when(accounts.resolveAccount(role.name())).thenReturn(Optional.of(
                    new AccountSummary((long) role.ordinal() + 1, role, ActorStatus.ACTIVE, null, null, null)));
        }
        session = UUID.randomUUID();
        connection = connections.saveAndFlush(OtaChannelConnection.builder().providerCode(OtaProvider.KLOOK)
                .environment(OtaEnvironment.TEST).status("ACTIVE").credentialReference("private-credential-reference").build());
        when(operations.getPublishedPackageSelection(any())).thenReturn(new PublishedPackageSelectionSnapshot(
                "123", 500000L, 1, 10, 1, true, 0, "Test package"));
        when(operations.getSaleEligibility(any())).thenReturn(new SaleEligibilitySnapshot(session, true, "PUBLISHED", 42L,
                FROM.plusSeconds(3600), FROM.plusSeconds(7200), 10, LocalDate.parse("2035-01-01")));
        when(resync.resync(session.toString())).thenReturn(new WorkshopSessionOtaSnapshot(session.toString(), "42",
                FROM.plusSeconds(3600), FROM.plusSeconds(7200), 10, "PUBLISHED", 1, "test-change-1", FROM));
    }

    @Test void emptyCreateAndExactReplayExposeOnlyPublicIdentifiers() throws Exception {
        mvc.perform(auth(get("/api/v1/ota-channels"), ActorRole.MANAGER)).andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[]}"));
        JsonNode first = create();
        assertThat(first.path("version").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(UUID.fromString(first.path("mappingId").asText())).isNotNull();
        assertThat(first.path("channelId").asText()).isEqualTo(connection.getPublicReference());
        assertThat(first.path("commercialStatus").asText()).isEqualTo("PENDING_COMMERCIAL_CONFIRMATION");
        assertThat(create()).isEqualTo(first);
        assertThat(mappings.count()).isEqualTo(1);
        mvc.perform(auth(get("/api/v1/ota-channels"), ActorRole.ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].state").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].credentialReference").doesNotExist())
                .andExpect(jsonPath("$.items[0].id").doesNotExist());
        verify(accounts, never()).getLocationScope(any());
    }

    @Test void updateUsesVersionAndRefusesInFlightIdentityChanges() throws Exception {
        JsonNode created = create();
        String id = created.path("mappingId").asText();
        var body = mappingBody(); body.put("version", created.path("version").asLong()); body.put("providerOptionReference", "changed");
        JsonNode updated = call(auth(put(path(id)), ActorRole.MANAGER), body, 200);
        assertThat(updated.path("version").asLong()).isGreaterThan(created.path("version").asLong());
        call(auth(put(path(id)), ActorRole.MANAGER), body, 409);
        OtaChannelMapping mapping = mappings.findByPublicReference(id).orElseThrow();
        outbound.saveAndFlush(OtaOutboundChange.builder().mapping(mapping).sourceVersion(1L).changeKind(OtaChangeKind.SCHEDULE)
                .sourceChangeReference("queued-source").canonicalSnapshot(json.createObjectNode()).acceptedAt(FROM).status("QUEUED").build());
        body.put("version", updated.path("version").asLong()); body.put("providerOptionReference", "blocked-change");
        call(auth(put(path(id)), ActorRole.MANAGER), body, 409);
        assertThat(mapping.getExternalOptionId()).isEqualTo("changed");
    }

    @Test void priceIsExactImmutableAndExpiredReplacementNeverFallsBack() throws Exception {
        String id = create().path("mappingId").asText();
        Map<String,Object> original = priceBody("500000.0000", FROM, null);
        JsonNode first = call(auth(post(path(id)+"/price-terms"), ActorRole.MANAGER), original, 201);
        assertThat(first.path("retailAmount").path("amount").asText()).isEqualTo("500000");
        assertThat(call(auth(post(path(id)+"/price-terms"), ActorRole.MANAGER), original, 201)).isEqualTo(first);
        call(auth(post(path(id)+"/price-terms"), ActorRole.MANAGER), priceBody("500001", FROM, null), 409);
        call(auth(post(path(id)+"/price-terms"), ActorRole.MANAGER), priceBody("500000.1", FROM.plusSeconds(1), null), 400);
        call(auth(post(path(id)+"/price-terms"), ActorRole.MANAGER), priceBody("600000", FROM.plusSeconds(10), FROM.plusSeconds(20)), 201);
        Long mappingId = mappings.findByPublicReference(id).orElseThrow().getId();
        assertThat(prices.resolveAt(mappingId, FROM.plusSeconds(9)).getRetailAmountMinor()).isEqualTo(500000);
        assertThat(prices.resolveAt(mappingId, FROM.plusSeconds(10)).getRetailAmountMinor()).isEqualTo(600000);
        assertThat(prices.resolveAt(mappingId, FROM.plusSeconds(20))).isNull();
        assertThat(snapshots.count()).isEqualTo(2);
    }

    @Test void preparationEnableAndSalesCloseAreAuthorizedDurableAndReplaySafe() throws Exception {
        String id = create().path("mappingId").asText();
        call(auth(post(path(id)+"/price-terms"), ActorRole.MANAGER), priceBody("500000", FROM, null), 201);
        Map<String,Object> sync = Map.of("mode", "PREPARATION", "reason", "prepare test mapping");
        call(auth(post(path(id)+"/sync").header("Idempotency-Key", "prepare-001"), ActorRole.ADMIN), sync, 403);
        var first = call(auth(post(path(id)+"/sync").header("Idempotency-Key", "prepare-001"), ActorRole.MANAGER), sync, 202);
        assertThat(call(auth(post(path(id)+"/sync").header("Idempotency-Key", "prepare-001"), ActorRole.MANAGER), sync, 202)).isEqualTo(first);
        assertThat(outbound.count()).isEqualTo(3);
        for (var change : outbound.findAll()) {
            delivery.recordDeliveryResult(change.getId(), new OtaDeliveryResult(1, FROM, FROM.plusSeconds(1),
                    OtaDeliveryOutcome.DELIVERED, UUID.randomUUID().toString(), null));
        }
        assertThat(mappings.findByPublicReference(id).orElseThrow().getStatus()).isEqualTo(OtaMappingStatus.SALES_CLOSED);
        call(auth(post(path(id)+"/booking-enable"), ActorRole.MANAGER), readiness(true), 403);
        JsonNode enabled = call(auth(post(path(id)+"/booking-enable"), ActorRole.ADMIN), readiness(true), 200);
        assertThat(enabled.path("decision").asText()).isEqualTo("APPROVED");
        assertThat(enabled.path("recordedAt").isTextual()).isTrue();
        Map<String,Object> reason = Map.of("reason", "close test sale");
        JsonNode closed = call(auth(post(path(id)+"/sales-close").header("Idempotency-Key", "close-001"), ActorRole.MANAGER), reason, 202);
        assertThat(call(auth(post(path(id)+"/sales-close").header("Idempotency-Key", "close-001"), ActorRole.MANAGER), reason, 202)).isEqualTo(closed);
        assertThat(controls.count()).isEqualTo(1);
        assertThat(mappings.findByPublicReference(id).orElseThrow().getStatus()).isEqualTo(OtaMappingStatus.SALES_CLOSED);
        mvc.perform(auth(get("/api/v1/ota-channels"), ActorRole.MANAGER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].salesControlStatus").value("QUEUED"));
        call(auth(post(path(id)+"/sales-close").header("Idempotency-Key", "close-001"), ActorRole.MANAGER), Map.of("reason", "different"), 409);
    }

    @Test void incompleteReadinessIsRetainedAndMappingStaysClosed() throws Exception {
        String id = create().path("mappingId").asText();
        var mapping = mappings.findByPublicReference(id).orElseThrow();
        mapping.startPreparationSync(); mapping.completePreparationSync(); mappings.flush();
        call(auth(post(path(id)+"/price-terms"), ActorRole.MANAGER), priceBody("500000", FROM, null), 201);
        JsonNode result = call(auth(post(path(id)+"/booking-enable"), ActorRole.ADMIN), readiness(false), 200);
        assertThat(result.path("decision").asText()).isEqualTo("REJECTED");
        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.SALES_CLOSED);
        assertThat(assessments.count()).isEqualTo(1);
    }

    @ParameterizedTest @EnumSource(value=ActorRole.class, names={"MEMBER","STAFF"})
    void nonOperatorRolesCannotReadOrCreate(ActorRole role) throws Exception {
        mvc.perform(auth(get("/api/v1/ota-channels"), role)).andExpect(status().isForbidden());
        call(auth(post("/api/v1/ota-channels/"+connection.getPublicReference()+"/mappings"), role), mappingBody(), 403);
        assertThat(mappings.count()).isZero();
    }

    @Test void revokedManagerAndMissingTokenAreRejected() throws Exception {
        mvc.perform(get("/api/v1/ota-channels")).andExpect(status().isUnauthorized());
        when(accounts.resolveAccount("MANAGER")).thenReturn(Optional.of(new AccountSummary(1L, ActorRole.MANAGER,
                ActorStatus.SUSPENDED, null, null, null)));
        mvc.perform(auth(get("/api/v1/ota-channels"), ActorRole.MANAGER)).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings={"unknown", "credentialReference", "version"})
    void upsertRejectsExtraFieldsWithoutPersisting(String field) throws Exception {
        var body = mappingBody(); body.put(field, "unexpected");
        call(auth(post("/api/v1/ota-channels/"+connection.getPublicReference()+"/mappings"), ActorRole.MANAGER), body, 400);
        assertThat(mappings.count()).isZero();
    }

    @Test void malformedBodyMissingHeadersAndUnknownUuidUseSafeErrors() throws Exception {
        mvc.perform(auth(post("/api/v1/ota-channels/"+connection.getPublicReference()+"/mappings"), ActorRole.MANAGER)
                .contentType(MediaType.APPLICATION_JSON).content("{bad-json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        call(auth(post(path(UUID.randomUUID().toString())+"/sync"), ActorRole.MANAGER), Map.of("mode","PREPARATION","reason","test"), 400);
        call(auth(post(path(UUID.randomUUID().toString())+"/sales-close").header("Idempotency-Key","unknown-001"), ActorRole.MANAGER), Map.of("reason","test"), 404);
        var body=mappingBody(); body.put("version", 1);
        call(auth(put(path("1-1-1-1-1")), ActorRole.MANAGER), body, 400);
    }

    @Test void adminCannotPerformManagerOnlyBusinessWrites() throws Exception {
        String id = create().path("mappingId").asText();
        var update = mappingBody(); update.put("version", 1);
        call(auth(put(path(id)), ActorRole.ADMIN), update, 403);
        call(auth(post(path(id)+"/price-terms"), ActorRole.ADMIN), priceBody("500000",FROM,null), 403);
        call(auth(post(path(id)+"/sales-close").header("Idempotency-Key","admin-close-1"), ActorRole.ADMIN), Map.of("reason","test"), 403);
        assertThat(snapshots.count()).isZero();
        assertThat(controls.count()).isZero();
    }

    @Test void mappingUpdateAndCreationCannotBypassLifecycleOrSelectionRules() throws Exception {
        String id=create().path("mappingId").asText();
        var mapping=mappings.findByPublicReference(id).orElseThrow();
        mapping.startPreparationSync(); mappings.flush();
        var update=mappingBody(); update.put("version",mapping.getVersion());
        call(auth(put(path(id)),ActorRole.MANAGER),update,409);
        call(auth(post("/api/v1/ota-channels/"+connection.getPublicReference()+"/mappings"),ActorRole.MANAGER),mappingBody(),409);
        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.PREPARATION_SYNC);
    }

    @Test void strictJsonTypesAndRequiredVersionAreEnforced() throws Exception {
        var body=mappingBody(); body.put("providerProductReference",123);
        call(auth(post("/api/v1/ota-channels/"+connection.getPublicReference()+"/mappings"),ActorRole.MANAGER),body,400);
        String id=create().path("mappingId").asText();
        call(auth(put(path(id)),ActorRole.MANAGER),mappingBody(),400);
        body=mappingBody(); body.put("version","1");
        call(auth(put(path(id)),ActorRole.MANAGER),body,400);
        var price=priceBody("500000",FROM,null); price.put("retailAmount",Map.of("amount",500000,"currency","VND"));
        call(auth(post(path(id)+"/price-terms"),ActorRole.MANAGER),price,400);
        price=priceBody("500000",FROM,null); price.put("effectiveFrom",12345678);
        call(auth(post(path(id)+"/price-terms"),ActorRole.MANAGER),price,400);
        assertThat(snapshots.count()).isZero();
    }

    @Test void readinessRejectsDuplicateChecksBeforeWritingAnAssessment() throws Exception {
        String id=create().path("mappingId").asText();
        var mapping=mappings.findByPublicReference(id).orElseThrow();
        mapping.startPreparationSync(); mapping.completePreparationSync(); mappings.flush();
        call(auth(post(path(id)+"/price-terms"),ActorRole.MANAGER),priceBody("500000",FROM,null),201);
        var check=Map.of("check","CONTRACT_ONBOARDING","outcome","VERIFIED","evidenceReference","test");
        call(auth(post(path(id)+"/booking-enable"),ActorRole.ADMIN),Map.of("readinessRecordReference","test",
                "reason","test", "checks",Collections.nCopies(12,check)),400);
        assertThat(assessments.count()).isZero();
        assertThat(mapping.getStatus()).isEqualTo(OtaMappingStatus.SALES_CLOSED);
    }

    private JsonNode create() throws Exception {
        return call(auth(post("/api/v1/ota-channels/"+connection.getPublicReference()+"/mappings"), ActorRole.MANAGER), mappingBody(), 201);
    }
    private Map<String,Object> mappingBody() {
        return new LinkedHashMap<>(Map.of("providerProductReference","product", "providerOptionReference","option",
                "workshopOfferReference","123", "sessionReference",session.toString()));
    }
    private static Map<String,Object> priceBody(String amount, Instant from, Instant to) {
        var body = new LinkedHashMap<String,Object>(Map.of("retailAmount",Map.of("amount",amount,"currency","VND"),
                "targetNetAmount",Map.of("amount","400000","currency","VND"), "effectiveFrom",from.toString(),
                "providerConfirmationEvidenceReference","test-confirmation", "reason","confirmed test terms"));
        if(to!=null) body.put("effectiveTo",to.toString());
        return body;
    }
    private static Map<String,Object> readiness(boolean complete) {
        var checks = Arrays.stream(OtaReadinessCheck.values()).map(check -> Map.of("check",check.name(),
                "outcome",(!complete && check==OtaReadinessCheck.CONTRACT_ONBOARDING)?"MISSING":"VERIFIED",
                "evidenceReference","test-evidence/"+check.name())).toList();
        return Map.of("readinessRecordReference","test-record","checks",checks,"reason","reviewed test evidence");
    }
    private static String path(String id) { return "/api/v1/ota-channel-mappings/"+id; }
    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, ActorRole role) {
        return request.header("Authorization","Bearer test-uid:"+role.name());
    }
    private JsonNode call(MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        String response = mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().is(expected)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("private-credential-reference");
        return json.readTree(response);
    }
}
