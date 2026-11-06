package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueCommandReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueAuditEvent;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueCommandReceipt;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.Product;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductPublicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T111 on MySQL (FR-027, SC-007, data-model {@code catalogue_audit_events}): a rejected business attempt is audited
 * as REJECTED in its own transaction after the command rolled back; nothing of the rejected change is kept.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueRejectionAuditIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private ProductPublicationService publication;
    @Autowired private ProductRepository products;
    @Autowired private CatalogueAuditRepository audits;
    @Autowired private CatalogueCommandReceiptRepository receipts;
    @Autowired private TransactionTemplate transaction;

    @Test
    void aRefusedPublicationLeavesTheProductUnchangedAndIsAuditedAsRejected() {
        long productId = fixture.draftProduct(STAFF);
        long version = fixture.productVersion(productId);
        UUID key = UUID.randomUUID();

        CatalogueErrorCode[] code = new CatalogueErrorCode[1];
        assertThatThrownBy(() -> publication.setPublication(STAFF, key, Long.toString(productId), version, true))
                .isInstanceOfSatisfying(CatalogueException.class, error -> code[0] = error.code());

        Product product = transaction.execute(status -> products.findById(productId).orElseThrow());
        assertThat(product.isPublished()).isFalse();
        assertThat(product.getVersion()).isEqualTo(version);
        Optional<CatalogueCommandReceipt> receipt = transaction.execute(status ->
                receipts.findByActorIdAndActionAndCommandId(STAFF, ProductPublicationService.PUBLISH_ACTION, key));
        assertThat(receipt).isEmpty();

        List<CatalogueAuditEvent> rejected = transaction.execute(status -> audits
                .findByResourceTypeAndResourceIdOrderByOccurredAtAscIdAsc(
                        CatalogueCommandExecutor.REJECTED_RESOURCE_TYPE, key.toString()));
        assertThat(rejected).singleElement().satisfies(event -> {
            assertThat(event.getOutcome()).isEqualTo(CatalogueAuditEvent.Outcome.REJECTED);
            assertThat(event.getActorId()).isEqualTo(STAFF);
            assertThat(event.getAction()).isEqualTo(ProductPublicationService.PUBLISH_ACTION);
            assertThat(event.getSafeChanges()).containsEntry("code", code[0].name());
            assertThat(event.getCorrelationId()).isNotBlank();
        });
        List<CatalogueAuditEvent> productEvents = transaction.execute(status -> audits
                .findByResourceTypeAndResourceIdOrderByOccurredAtAscIdAsc("product", Long.toString(productId)));
        assertThat(productEvents).extracting(CatalogueAuditEvent::getAction)
                .doesNotContain(ProductPublicationService.PUBLISH_ACTION);
    }

    @Test
    void aStaleVersionIsAuditedAsRejected() {
        long productId = fixture.draftProduct(STAFF);
        long staleVersion = fixture.productVersion(productId) + 7;
        UUID key = UUID.randomUUID();

        assertThatThrownBy(() -> publication.setPublication(STAFF, key, Long.toString(productId), staleVersion, true))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.STALE_VERSION));

        List<CatalogueAuditEvent> rejected = transaction.execute(status -> audits
                .findByResourceTypeAndResourceIdOrderByOccurredAtAscIdAsc(
                        CatalogueCommandExecutor.REJECTED_RESOURCE_TYPE, key.toString()));
        assertThat(rejected)
                .singleElement()
                .satisfies(event -> assertThat(event.getSafeChanges()).containsEntry("code", "STALE_VERSION"));
    }
}
