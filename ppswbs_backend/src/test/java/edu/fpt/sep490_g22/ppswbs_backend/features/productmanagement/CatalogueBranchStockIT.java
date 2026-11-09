package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductVariantRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.VariantBranchStockRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductVariant;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.VariantBranchStock;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ManagementProductQueryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductBranchStockService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.VariantQuantityService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * US10 / FR-047–FR-050 on MySQL 8.4 (Session 2026-11-08): quantity per size × branch, the size total kept equal to the
 * sum of its branch rows, adding a branch, the old per-size command, an FE-08 sale without a branch, and the Staff
 * branch scope read from the 001 facade (a test double here: the assignment table belongs to 001).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueBranchStockIT {

    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private CreateProductService createProducts;
    @Autowired private ProductBranchStockService branchStock;
    @Autowired private VariantQuantityService quantities;
    @Autowired private ManagementProductQueryService query;
    @Autowired private ProductVariantRepository variants;
    @Autowired private VariantBranchStockRepository stocks;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ProductImportService imports;
    @Autowired private TransactionTemplate transaction;
    @MockitoBean private AccountFacade accounts;

    private long branchA;
    private long branchB;

    @BeforeEach
    void setUp() {
        branchA = fixture.testBranch();
        jdbc.update("INSERT IGNORE INTO workshop_locations (code, display_name, address_snapshot, active) VALUES (?, ?, ?, 1)",
                "PM-TEST-BRANCH-B", "Chi nhánh kiểm thử B", "Địa chỉ B");
        jdbc.update("UPDATE workshop_locations SET active = 1 WHERE code = ?", "PM-TEST-BRANCH-B");
        branchB = jdbc.queryForObject("SELECT id FROM workshop_locations WHERE code = ?", Long.class, "PM-TEST-BRANCH-B");
    }

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAtTwoBranchesKeepsTheSizeTotalAsTheSumAndShowsBranches() {
        long productId = create(List.of(branch(branchA, "3", "0"), branch(branchB, "2", "5")));

        List<ProductVariant> sizes = sizes(productId);
        assertThat(sizes).extracting(ProductVariant::getRemainingUnsold)
                .containsExactly(BigInteger.valueOf(5), BigInteger.valueOf(5));
        assertThat(rows(sizes.getFirst().getId())).extracting(VariantBranchStock::getRemainingUnsold)
                .containsExactly(BigInteger.valueOf(3), BigInteger.valueOf(2));

        ManagementProductDtos.ManagementProduct dto = query.get(Long.toString(productId));
        assertThat(dto.branches()).extracting(ManagementProductDtos.Branch::locationId)
                .containsExactly(Long.toString(branchA), Long.toString(branchB));
        assertThat(dto.variants().getFirst().branchStocks()).hasSize(2);
        assertThat(query.list(null, null, null, 0, Long.toString(branchB)).items())
                .extracting(ManagementProductDtos.ManagementProduct::id).contains(Long.toString(productId));
    }

    @Test
    void aManualCreateWithoutBranchIsRefusedAndSavesNothing() {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM catalogue_products", Long.class);
        assertThatThrownBy(() -> createProducts.create(CatalogueTestSupport.STAFF, UUID.randomUUID(), command(List.of())))
                .isInstanceOf(CatalogueException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalogue_products", Long.class)).isEqualTo(before);
    }

    @Test
    void branchQuantityChangesTheRowAndTheTotal() {
        long productId = create(List.of(branch(branchA, "3", "0"), branch(branchB, "2", "5")));
        ProductVariant size5 = sizes(productId).getFirst();
        VariantBranchStock atB = rows(size5.getId()).get(1);

        branchStock.setQuantity(CatalogueTestSupport.STAFF, UUID.randomUUID(), size5.getId().toString(),
                Long.toString(branchB), atB.getVersion(), "7");

        assertThat(rows(size5.getId())).extracting(VariantBranchStock::getRemainingUnsold)
                .containsExactly(BigInteger.valueOf(3), BigInteger.valueOf(7));
        assertThat(sizes(productId).getFirst().getRemainingUnsold()).isEqualTo(BigInteger.valueOf(10));
    }

    @Test
    void addingABranchAddsRowsOnceAndTheOldCommandNeedsABranchWhenThereAreSeveral() {
        long productId = create(List.of(branch(branchA, "3", "1")));
        long version = query.get(Long.toString(productId)).version();
        ProductVariant size5 = sizes(productId).getFirst();

        // One branch: the old per-size command changes that branch.
        quantities.setQuantity(CatalogueTestSupport.STAFF, UUID.randomUUID(), size5.getId().toString(),
                size5.getVersion(), "4");
        assertThat(rows(size5.getId())).extracting(VariantBranchStock::getRemainingUnsold).containsExactly(BigInteger.valueOf(4));

        branchStock.addBranch(CatalogueTestSupport.STAFF, UUID.randomUUID(), Long.toString(productId), version,
                branch(branchB, "2", "0"));
        assertThat(sizes(productId)).extracting(ProductVariant::getRemainingUnsold)
                .containsExactly(BigInteger.valueOf(6), BigInteger.valueOf(1));
        assertThatThrownBy(() -> branchStock.addBranch(CatalogueTestSupport.STAFF, UUID.randomUUID(),
                Long.toString(productId), version, branch(branchB, "1", "1")))
                .satisfies(error -> assertThat(((CatalogueException) error).code()).isEqualTo(CatalogueErrorCode.BRANCH_ALREADY_ADDED));

        ProductVariant fresh = sizes(productId).getFirst();
        assertThatThrownBy(() -> quantities.setQuantity(CatalogueTestSupport.STAFF, UUID.randomUUID(),
                fresh.getId().toString(), fresh.getVersion(), "9"))
                .satisfies(error -> assertThat(((CatalogueException) error).code()).isEqualTo(CatalogueErrorCode.BRANCH_REQUIRED));
    }

    @Test
    void stopSellingAtOneBranchKeepsTheOtherBranch() {
        long productId = create(List.of(branch(branchA, "3", "0"), branch(branchB, "2", "5")));

        branchStock.setSaleEnabled(CatalogueTestSupport.STAFF, UUID.randomUUID(), Long.toString(productId),
                Long.toString(branchB), false);

        ManagementProductDtos.ManagementProduct dto = query.get(Long.toString(productId));
        assertThat(dto.branches()).extracting(ManagementProductDtos.Branch::saleEnabled).containsExactly(true, false);
    }

    @Test
    void aSaleWithoutBranchTakesFromTheBranchesInIdOrderSoTheSumStaysTheTotal() {
        long productId = create(List.of(branch(branchA, "3", "0"), branch(branchB, "2", "5")));
        long size5 = sizes(productId).getFirst().getId();

        transaction.executeWithoutResult(status -> branchStock.deductSaleWithoutBranch(size5, BigInteger.valueOf(4)));

        List<VariantBranchStock> rows = rows(size5);
        BigInteger first = branchA < branchB ? BigInteger.ZERO : BigInteger.ONE;
        assertThat(rows.stream().map(VariantBranchStock::getRemainingUnsold).reduce(BigInteger.ZERO, BigInteger::add))
                .isEqualTo(BigInteger.ONE);
        assertThat(rows.getFirst().getRemainingUnsold()).isEqualTo(first);
    }

    @Test
    void aStaffMemberActsOnlyAtAssignedBranches() {
        actAsStaff(42L);
        when(accounts.getLocationScope(any())).thenReturn(List.of(branchA));

        long productId = create(List.of(branch(branchA, "1", "1")));
        assertThatThrownBy(() -> create(List.of(branch(branchB, "1", "1"))))
                .satisfies(error -> assertThat(((CatalogueException) error).code()).isEqualTo(CatalogueErrorCode.BRANCH_OUT_OF_SCOPE));
        long version = query.get(Long.toString(productId)).version();
        assertThatThrownBy(() -> branchStock.addBranch("42", UUID.randomUUID(), Long.toString(productId), version,
                branch(branchB, "1", "1")))
                .satisfies(error -> assertThat(((CatalogueException) error).code()).isEqualTo(CatalogueErrorCode.BRANCH_OUT_OF_SCOPE));

        when(accounts.getLocationScope(any())).thenReturn(List.of());
        assertThatThrownBy(() -> create(List.of(branch(branchA, "1", "1"))))
                .satisfies(error -> assertThat(((CatalogueException) error).code()).isEqualTo(CatalogueErrorCode.BRANCH_SCOPE_EMPTY));
    }

    @Test
    void anExcelImportAtTwoBranchesCreatesTheBranchRowsOnConfirmation() throws Exception {
        String category = jdbc.queryForObject("SELECT name FROM catalogue_categories WHERE id = ?", String.class,
                fixture.activeCategory());
        String nameA = jdbc.queryForObject("SELECT display_name FROM workshop_locations WHERE id = ?", String.class, branchA);
        String nameB = jdbc.queryForObject("SELECT display_name FROM workshop_locations WHERE id = ?", String.class, branchB);
        String group = "IMP-" + UUID.randomUUID().toString().substring(0, 8);
        byte[] file;
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var out = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Sản phẩm");
            String[] header = {"Mã nhóm", "Tên sản phẩm", "Mô tả", "Danh mục", "Size", "Giá đề xuất (VND)",
                    "Số lượng " + nameA, "Số lượng " + nameB};
            Object[] values = {group, "Nhẫn import chi nhánh", "Mô tả", category, "5", "150000", "3", "2"};
            var head = sheet.createRow(0);
            var data = sheet.createRow(1);
            for (int i = 0; i < header.length; i++) {
                head.createCell(i).setCellValue(header[i]);
                data.createCell(i).setCellValue(values[i].toString());
            }
            workbook.write(out);
            file = out.toByteArray();
        }

        String batchId = imports.validate(CatalogueTestSupport.STAFF, UUID.randomUUID(), "chi-nhanh.xlsx", file,
                List.of(Long.toString(branchA), Long.toString(branchB))).resourceId();
        var batch = imports.get(CatalogueTestSupport.STAFF, batchId);
        assertThat(batch.errors()).isEmpty();
        imports.confirm(CatalogueTestSupport.STAFF, UUID.randomUUID(), batchId, batch.version());

        long productId = Long.parseLong(imports.get(CatalogueTestSupport.STAFF, batchId).created().getFirst().productId());
        ProductVariant size5 = sizes(productId).getFirst();
        assertThat(size5.getRemainingUnsold()).isEqualTo(BigInteger.valueOf(5));
        assertThat(rows(size5.getId())).extracting(VariantBranchStock::getRemainingUnsold)
                .containsExactly(BigInteger.valueOf(3), BigInteger.valueOf(2));
    }

    // ---- helpers ----

    private long create(List<ProductBranchStockService.BranchInput> branches) {
        return Long.parseLong(createProducts.create(CatalogueTestSupport.STAFF, UUID.randomUUID(), command(branches)).resourceId());
    }

    private CreateProductService.CreateProductCommand command(List<ProductBranchStockService.BranchInput> branches) {
        return new CreateProductService.CreateProductCommand("Nhẫn chi nhánh " + UUID.randomUUID(), "Mô tả",
                Long.toString(fixture.activeCategory()),
                List.of(new CreateProductService.VariantInput(5, "150000", "0"),
                        new CreateProductService.VariantInput(12, "180000", "0")),
                List.of(), branches);
    }

    private static ProductBranchStockService.BranchInput branch(long locationId, String size5, String size12) {
        return new ProductBranchStockService.BranchInput(Long.toString(locationId), List.of(
                new ProductBranchStockService.SizeQuantity(5, size5), new ProductBranchStockService.SizeQuantity(12, size12)));
    }

    private List<ProductVariant> sizes(long productId) {
        return transaction.execute(status -> variants.findByProductIdInOrderByProductIdAscRingSizeAsc(List.of(productId)));
    }

    private List<VariantBranchStock> rows(long variantId) {
        return transaction.execute(status -> stocks.findByVariantIdInOrderByVariantIdAscLocationIdAsc(List.of(variantId)))
                .stream().sorted(Comparator.comparing(VariantBranchStock::getLocationId)).toList();
    }

    private static void actAsStaff(long accountId) {
        CurrentAccountActor actor = new CurrentAccountActor(accountId, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
    }
}
