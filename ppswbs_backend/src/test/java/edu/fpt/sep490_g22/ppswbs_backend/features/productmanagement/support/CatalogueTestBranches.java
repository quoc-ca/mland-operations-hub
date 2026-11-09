package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductBranchStockService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * US10 test helper: a manual create needs a branch (FR-048). The branch is an active row of the canonical list
 * ({@code workshop_locations}, owner 003.2) on the MySQL test database, created once with a fixed code.
 */
public final class CatalogueTestBranches {

    public static final String CODE = "PM-TEST-BRANCH";

    private CatalogueTestBranches() {
    }

    /** Id of the active test branch, created if missing. */
    public static long ensure(JdbcTemplate jdbc) {
        jdbc.update("INSERT IGNORE INTO workshop_locations (code, display_name, address_snapshot, active) VALUES (?, ?, ?, 1)",
                CODE, "Chi nhánh kiểm thử 002", "Địa chỉ kiểm thử");
        jdbc.update("UPDATE workshop_locations SET active = 1 WHERE code = ?", CODE);
        Long id = jdbc.queryForObject("SELECT id FROM workshop_locations WHERE code = ?", Long.class, CODE);
        return id == null ? 0 : id;
    }

    /** The same command sold at the given branch, with each size's quantity from {@code variants[].quantity}. */
    public static CreateProductService.CreateProductCommand atBranch(CreateProductService.CreateProductCommand command,
                                                                    long branchId) {
        List<ProductBranchStockService.SizeQuantity> quantities = command.variants().stream()
                .map(variant -> new ProductBranchStockService.SizeQuantity(variant.size(), variant.quantity()))
                .toList();
        return new CreateProductService.CreateProductCommand(command.name(), command.description(), command.categoryId(),
                command.variants(), command.images(),
                List.of(new ProductBranchStockService.BranchInput(Long.toString(branchId), quantities)));
    }
}
