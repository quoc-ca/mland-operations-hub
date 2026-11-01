package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshotChanged;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopOtaSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real producer, Modulith event registry, consumer and MySQL across context restart. */
class OtaInventorySyncIT {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan({"edu.fpt.sep490_g22.ppswbs_backend.features", "org.springframework.modulith.events.jpa.updating"})
    @EnableJpaRepositories(basePackages = {
            "edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence",
            "edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao"})
    @Import({WorkshopOtaSnapshotService.class, OtaOperationsSnapshotListener.class, OtaAuditCorrelationService.class,
            OtaReconciliationService.class})
    static class Config {
        @Bean AtomicBoolean failConsumer() { return new AtomicBoolean(false); }
        @Bean OtaSyncDeliveryService delivery(OtaChannelMappingRepository mappings,
                OtaOutboundChangeRepository changes, OtaDeliveryAttemptRepository attempts,
                OtaReconciliationWorkItemRepository items, OtaAuditCorrelationService audit, AtomicBoolean failConsumer) {
            return new FailingDelivery(mappings, changes, attempts, items, audit, failConsumer);
        }
    }

    static class FailingDelivery extends OtaSyncDeliveryService {
        private final AtomicBoolean fail;
        FailingDelivery(OtaChannelMappingRepository m, OtaOutboundChangeRepository c,
                OtaDeliveryAttemptRepository a, OtaReconciliationWorkItemRepository w,
                OtaAuditCorrelationService audit, AtomicBoolean fail) {
            super(m,c,a,w,audit); this.fail=fail;
        }
        @Override @Transactional
        public int acceptSnapshot(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshot snapshot) {
            int result=super.acceptSnapshot(snapshot);
            if (fail.compareAndSet(true,false)) throw new IllegalStateException("Injected consumer failure after outbound writes");
            return result;
        }
    }

    private ConfigurableApplicationContext start(OtaMigrationTestSupport db, String schema, boolean replay) {
        return new SpringApplicationBuilder(Config.class).web(WebApplicationType.NONE).run(
                "--spring.config.name=ota-inventory-isolated-test",
                "--spring.datasource.url="+db.jdbcUrl(schema),
                "--spring.datasource.username="+System.getenv("WORKSHOP_OTA_TEST_JDBC_USER"),
                "--spring.datasource.password="+System.getenv("WORKSHOP_OTA_TEST_JDBC_PASSWORD"),
                "--spring.jpa.hibernate.ddl-auto=none", "--spring.flyway.enabled=false",
                "--spring.modulith.events.republish-outstanding-events-on-restart="+replay,
                "--spring.main.banner-mode=off", "--logging.level.root=WARN");
    }

    @Test
    void failedConsumerRollsBackAndPersistentPublicationReplaysAfterRestart() throws Exception {
        var configured=OtaMigrationTestSupport.fromEnvironment();
        assumeTrue(configured.isPresent(), "Requires isolated MySQL test configuration");
        try (var db=configured.orElseThrow()) {
            String schema=db.createDatabase();
            db.flyway(schema,null).migrate();
            String sessionReference;
            Long mappingId;
            try (var app=start(db,schema,false)) {
                var sessions=app.getBean(WorkshopSessionRepository.class);
                var locations=app.getBean(WorkshopLocationRepository.class);
                var slots=app.getBean(WorkshopSlotRepository.class);
                var location=locations.saveAndFlush(WorkshopLocation.builder().code("REPLAY-TEST")
                        .displayName("Replay fixture").addressSnapshot("test").build());
                var slot=slots.saveAndFlush(WorkshopSlot.builder().location(location).label("Test")
                        .startTime(LocalTime.of(9,0)).endTime(LocalTime.of(10,0)).defaultCapacity(10).build());
                Instant start=Instant.parse("2035-01-01T02:00:00Z");
                var session=sessions.saveAndFlush(WorkshopSession.builder().location(location).slot(slot)
                        .workshopDate(LocalDate.of(2035,1,1)).scheduledStartAt(start).scheduledEndAt(start.plusSeconds(3600))
                        .checkInOpensAt(start.minusSeconds(1800)).capacity(10).state(SessionState.PUBLISHED).build());
                sessionReference=session.getReference();
                var connection=app.getBean(OtaChannelConnectionRepository.class).saveAndFlush(OtaChannelConnection.builder()
                        .providerCode(OtaProvider.KLOOK).environment(OtaEnvironment.TEST).status("ACTIVE")
                        .credentialReference("test-reference-only").build());
                var mapping=app.getBean(OtaChannelMappingRepository.class).saveAndFlush(OtaChannelMapping.builder()
                        .connection(connection).offerReference("1").sessionReference(sessionReference)
                        .locationReference(location.getId().toString()).externalProductId("product").externalOptionId("option")
                        .status(OtaMappingStatus.PREPARATION_SYNC).build());
                mappingId=mapping.getId();
                app.getBean(AtomicBoolean.class).set(true);
                app.getBean(WorkshopOtaSnapshotService.class).publishForObservableChange(session,"first",start);
                await().atMost(Duration.ofSeconds(10)).until(() -> !app.getBean(AtomicBoolean.class).get());
                assertThat(count(app,"event_publication WHERE completion_date IS NULL")).isEqualTo(1);
                assertThat(count(app,"workshop_session_ota_snapshots")).isEqualTo(1);
                assertThat(count(app,"ota_outbound_changes")).isZero();
                assertThat(sessions.findById(session.getId()).orElseThrow().getCapacity()).isEqualTo(10);
            }
            try (var app=start(db,schema,true)) {
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                    assertThat(count(app,"event_publication WHERE completion_date IS NULL")).isZero();
                    assertThat(count(app,"ota_outbound_changes")).isEqualTo(3);
                });
                var producer=app.getBean(WorkshopOtaSnapshotService.class);
                var first=producer.resync(sessionReference);
                assertThat(first.sourceVersion()).isEqualTo(1);
                var tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
                tx.executeWithoutResult(status -> app.publishEvent(new WorkshopSessionOtaSnapshotChanged(first)));
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                        assertThat(count(app,"event_publication WHERE completion_date IS NULL")).isZero());
                assertThat(count(app,"ota_outbound_changes")).isEqualTo(3);
                assertThat(app.getBean(OtaChannelMappingRepository.class).findById(mappingId).orElseThrow().getStatus())
                        .isEqualTo(OtaMappingStatus.PREPARATION_SYNC);
                assertThat(count(app,"capacity_holds")).isZero();
                assertThat(count(app,"ticket_session_entitlements")).isZero();
                // Producer transaction rollback must persist neither the snapshot nor its publication.
                long publications=count(app,"event_publication");
                tx.executeWithoutResult(status -> {
                    var session=app.getBean(WorkshopSessionRepository.class).findByReference(sessionReference).orElseThrow();
                    session.setCapacity(8);
                    producer.publishForObservableChange(session,"rolled-back",Instant.now());
                    status.setRollbackOnly();
                });
                assertThat(count(app,"event_publication")).isEqualTo(publications);
                assertThat(producer.resync(sessionReference)).isEqualTo(first);

                var changes=app.getBean(OtaOutboundChangeRepository.class);
                var mappings=app.getBean(OtaChannelMappingRepository.class);
                var connection=app.getBean(OtaChannelConnectionRepository.class).findAll().getFirst();
                var unrelated=mappings.saveAndFlush(OtaChannelMapping.builder().connection(connection)
                        .offerReference("2").sessionReference(UUID.randomUUID().toString()).locationReference(first.locationReference())
                        .externalProductId("other-product").externalOptionId("other-option")
                        .status(OtaMappingStatus.PREPARATION_SYNC).build());
                change(app,sessionReference,"schedule",s -> {
                    s.setScheduledStartAt(s.getScheduledStartAt().plusSeconds(1800));
                    s.setScheduledEndAt(s.getScheduledEndAt().plusSeconds(1800));
                });
                completed(app,0);
                change(app,sessionReference,"availability",s -> s.setCapacity(9));
                completed(app,0);
                assertThat(changes.findAllByMapping_IdAndSourceVersion(mappingId,2L)).hasSize(3).anySatisfy(c -> {
                    assertThat(c.getChangeKind()).isEqualTo(OtaChangeKind.SCHEDULE);
                    assertThat(c.getCanonicalSnapshot().path("scheduledStart").asText())
                            .isEqualTo(first.scheduledStart().plusSeconds(1800).toString());
                });
                assertThat(changes.findAllByMapping_IdAndSourceVersion(mappingId,3L)).hasSize(3).anySatisfy(c -> {
                    assertThat(c.getChangeKind()).isEqualTo(OtaChangeKind.AVAILABILITY);
                    assertThat(c.getCanonicalSnapshot().path("sellableAvailability").asInt()).isEqualTo(9);
                });
                change(app,sessionReference,"pause",s -> s.setState(SessionState.SALES_PAUSED));
                completed(app,0);
                assertThat(changes.findAllByMapping_IdAndSourceVersion(mappingId,4L)).hasSize(3)
                        .anySatisfy(c -> {
                            assertThat(c.getChangeKind()).isEqualTo(OtaChangeKind.SALE_STATE);
                            assertThat(c.getCanonicalSnapshot().path("saleState").asText()).isEqualTo("SALES_PAUSED");
                        });
                assertThat(mappings.findById(mappingId).orElseThrow().getStatus()).isEqualTo(OtaMappingStatus.PREPARATION_SYNC);
                change(app,sessionReference,"resume",s -> s.setState(SessionState.PUBLISHED));
                completed(app,0);
                assertThat(changes.count()).isEqualTo(15);
                tx.executeWithoutResult(status -> app.publishEvent(new WorkshopSessionOtaSnapshotChanged(first)));
                completed(app,0);
                assertThat(changes.count()).isEqualTo(15);

                // Lose version 6 at the consumer; version 7 must suspend only this session's mapping.
                app.getBean(AtomicBoolean.class).set(true);
                change(app,sessionReference,"missed-six",s -> s.setCapacity(8));
                await().atMost(Duration.ofSeconds(10)).until(() -> !app.getBean(AtomicBoolean.class).get());
                change(app,sessionReference,"gap-seven",s -> s.setCapacity(7));
                completed(app,1);
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                        assertThat(mappings.findById(mappingId).orElseThrow().getStatus()).isEqualTo(OtaMappingStatus.SYNC_SUSPENDED));
                assertThat(mappings.findById(unrelated.getId()).orElseThrow().getStatus()).isEqualTo(OtaMappingStatus.PREPARATION_SYNC);
                assertThat(changes.count()).isEqualTo(15);
                var authoritative=producer.resync(sessionReference);
                assertThat(authoritative.sourceVersion()).isEqualTo(7);
                assertThat(authoritative.sellableAvailability()).isEqualTo(7);
                var actor=new AccountSummary(99L,ActorRole.MANAGER,ActorStatus.ACTIVE,null,null,null);
                var reconciliation=app.getBean(OtaReconciliationService.class);
                Long itemId=app.getBean(OtaReconciliationWorkItemRepository.class).findAll().getFirst().getId();
                reconciliation.resolve(itemId,"RESYNC_REQUESTED","repair gap","gap-resync",actor);
                assertThat(producer.resync(sessionReference)).isEqualTo(authoritative);
                var operationalSession=app.getBean(WorkshopSessionRepository.class).findByReference(sessionReference).orElseThrow();
                assertThat(operationalSession.getCapacity()).isEqualTo(7);
                assertThat(operationalSession.getState()).isEqualTo(SessionState.PUBLISHED);
                assertThat(changes.findAllByMapping_IdAndSourceVersion(mappingId,7L)).hasSize(3);
                assertThatThrownBy(() -> reconciliation.resolve(itemId,"ACCEPTED","confirmed","gap-accept",actor))
                        .isInstanceOf(edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaMappingLifecycleException.class);
                for (var c : changes.findAllByMapping_IdAndSourceVersion(mappingId,7L)) {
                    app.getBean(OtaSyncDeliveryService.class).recordDeliveryResult(c.getId(),new OtaDeliveryResult(1,
                            Instant.now(),Instant.now(),OtaDeliveryOutcome.DELIVERED,UUID.randomUUID().toString(),null));
                }
                reconciliation.resolve(itemId,"ACCEPTED","confirmed","gap-accept",actor);
                assertThat(mappings.findById(mappingId).orElseThrow().getStatus()).isEqualTo(OtaMappingStatus.SALES_CLOSED);
                assertThat(producer.resync(sessionReference)).isEqualTo(authoritative);
                assertThat(count(app,"capacity_holds")).isZero();
                assertThat(count(app,"ticket_session_entitlements")).isZero();

                // Same version with another change identity is uncertain ordering, not a replacement.
                var conflicting=new edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopSessionOtaSnapshot(
                        authoritative.sessionReference(),authoritative.locationReference(),authoritative.scheduledStart(),
                        authoritative.scheduledEnd(),authoritative.sellableAvailability(),authoritative.saleState(),
                        authoritative.sourceVersion(),"conflicting-identity",authoritative.effectiveAt());
                tx.executeWithoutResult(status -> app.publishEvent(new WorkshopSessionOtaSnapshotChanged(conflicting)));
                completed(app,1);
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                        assertThat(mappings.findById(mappingId).orElseThrow().getStatus()).isEqualTo(OtaMappingStatus.SYNC_SUSPENDED));
                assertThat(changes.findAllByMapping_IdAndSourceVersion(mappingId,7L)).allSatisfy(c ->
                        assertThat(c.getSourceChangeReference()).isEqualTo("gap-seven"));
                assertThat(mappings.findById(unrelated.getId()).orElseThrow().getStatus()).isEqualTo(OtaMappingStatus.PREPARATION_SYNC);
            }
        }
    }

    private long count(ConfigurableApplicationContext app,String tableAndCondition) {
        return app.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM "+tableAndCondition,Long.class);
    }

    private void completed(ConfigurableApplicationContext app,long remaining) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(count(app,"event_publication WHERE completion_date IS NULL")).isEqualTo(remaining));
    }

    private void change(ConfigurableApplicationContext app,String sessionReference,String reference,
                        java.util.function.Consumer<WorkshopSession> mutation) {
        new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var session=app.getBean(WorkshopSessionRepository.class).findByReference(sessionReference).orElseThrow();
            mutation.accept(session);
            app.getBean(WorkshopOtaSnapshotService.class).publishForObservableChange(session,reference,Instant.now());
        });
    }
}
