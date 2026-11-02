package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueAuditRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.CatalogueCommandReceiptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueMediaCleanupService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.DeleteProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ProductImportPayloadCleaner;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * T084 retention part on MySQL (data-model: receipts and business audit kept at least 5 years, FR-027/028): no
 * catalogue code deletes command receipts or audit events, and running every catalogue cleanup job far beyond five
 * years later removes neither the receipts/audit of a product nor the images it still references, even after the
 * product was soft-deleted. The storage provider is a stand-in; nothing is deleted from it here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueRetentionIT {

    private static final String CATALOGUE = "edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement..";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement");

    @MockitoBean private MediaStoragePort storage;
    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private DeleteProductService deletion;
    @Autowired private CatalogueMediaCleanupService mediaCleanup;
    @Autowired private ProductImportPayloadCleaner importCleanup;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void noCatalogueCodeDeletesReceiptsOrAuditEvents() {
        noClasses().that().resideInAPackage(CATALOGUE)
                .should().callMethodWhere(DescribedPredicate.<JavaMethodCall>describe(
                        "a delete method of the receipt or audit repository",
                        call -> call.getTarget().getName().startsWith("delete")
                                && (call.getTargetOwner().isAssignableTo(CatalogueCommandReceiptRepository.class)
                                || call.getTargetOwner().isAssignableTo(CatalogueAuditRepository.class))))
                .because("command receipts and business audit are kept at least five years (data-model, G-04)")
                .check(CLASSES);
        noClasses().that().resideInAPackage(CATALOGUE)
                .should().callMethod(EntityManager.class, "remove", Object.class)
                .because("catalogue history is soft-deleted, never removed through the entity manager")
                .check(CLASSES);
    }

    @Test
    void cleanupJobsSixYearsLaterKeepReceiptsAuditAndReferencedImages() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        deletion.delete(MANAGER, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId));
        UUID image = fixture.images(productId).get(0).getMediaObjectId();
        long receiptsBefore = receiptsOf(productId);
        long auditBefore = auditOf(productId);
        assertThat(receiptsBefore).isPositive();
        assertThat(auditBefore).isPositive();

        Instant sixYearsLater = Instant.now().plus(Duration.ofDays(6 * 366));
        // Only this product's image: a full run would also clean other tests' unreferenced uploads in the shared DB.
        assertThat(mediaCleanup.clean(image, sixYearsLater)).isEqualTo(CatalogueMediaCleanupService.Result.SKIPPED);
        importCleanup.cleanExpired(sixYearsLater);

        assertThat(receiptsOf(productId)).isEqualTo(receiptsBefore);
        assertThat(auditOf(productId)).isEqualTo(auditBefore);
        assertThat(fixture.media(image).getState()).as("a deleted product keeps its images as history")
                .isEqualTo(CatalogueMediaObject.State.READY);
    }

    private long receiptsOf(long productId) {
        return jdbc.queryForObject("select count(*) from catalogue_command_receipts where resource_id = ?",
                Long.class, Long.toString(productId));
    }

    private long auditOf(long productId) {
        return jdbc.queryForObject("select count(*) from catalogue_audit_events where resource_type = 'product' "
                + "and resource_id = ?", Long.class, Long.toString(productId));
    }
}
