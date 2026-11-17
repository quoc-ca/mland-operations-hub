package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogueSeedRunnerTests {

    @Test
    void sizeRangesExpandAndCommandKeysAreStablePerStepAndProduct() {
        assertThat(CatalogueSeedRunner.sizes("6-9")).containsExactly(6, 7, 8, 9);
        assertThat(CatalogueSeedRunner.sizes("12")).containsExactly(12);
        assertThat(CatalogueSeedRunner.key("create", "Nhẫn A")).isEqualTo(CatalogueSeedRunner.key("create", "Nhẫn A"));
        assertThat(CatalogueSeedRunner.key("create", "Nhẫn A")).isNotEqualTo(CatalogueSeedRunner.key("submit", "Nhẫn A"));
    }

    @Test
    void branchesAreMatchedByTheDisplayNameOfAnActiveBranchOnly() {
        List<SellingLocation> active = List.of(
                new SellingLocation(1, "Mland Hà Nội – Hoàn Kiếm", "83 Mã Mây"),
                new SellingLocation(7, "Mland Hà Nội – Tây Hồ", "3B Đặng Thái Thân"));
        Map<String, String> names = new LinkedHashMap<>();
        names.put("HN-HOANKIEM", " Mland Hà Nội – Hoàn Kiếm ");
        names.put("HN-TAYHO", "Mland Hà Nội – Tây Hồ");
        names.put("DN-HAICHAU", "Mland Đà Nẵng – Hải Châu");
        names.put("NO-NAME", null);

        assertThat(CatalogueSeedRunner.branchIds(names, active))
                .containsExactly(Map.entry("HN-HOANKIEM", 1L), Map.entry("HN-TAYHO", 7L));
        assertThat(CatalogueSeedRunner.branchIds(null, active)).isEmpty();
        assertThat(CatalogueSeedRunner.branchIds(names, List.of())).isEmpty();
    }

    @Test
    void theSeedFileIsValidForTheCatalogueRules() throws Exception {
        CatalogueSeedRunner.Seed seed;
        try (InputStream in = new ClassPathResource("catalogue-seed/catalogue-seed.json").getInputStream()) {
            seed = new ObjectMapper().readValue(in, CatalogueSeedRunner.Seed.class);
        }
        Set<String> categories = new HashSet<>();
        seed.categories().forEach(category -> categories.add(category.name()));
        Set<String> names = new HashSet<>();
        for (CatalogueSeedRunner.SeedProduct product : seed.products()) {
            assertThat(names.add(product.name())).as("unique name %s", product.name()).isTrue();
            assertThat(categories).contains(product.category());
            assertThat(product.name().length()).isBetween(1, 200);
            assertThat(product.description().length()).isBetween(1, 5000);
            List<Integer> sizes = CatalogueSeedRunner.sizes(product.sizes());
            assertThat(sizes).isNotEmpty().allSatisfy(size -> assertThat(size).isBetween(5, 29));
            assertThat(sizes.size()).isLessThanOrEqualTo(25);
            assertThat(Long.parseLong(product.price())).isPositive();
            assertThat(product.quantities()).isNotEmpty().allSatisfy((code, quantity) -> assertThat(quantity).isNotNegative());
            assertThat(seed.branches()).as("every branch code of %s is named", product.name())
                    .containsKeys(product.quantities().keySet().toArray(String[]::new));
            assertThat(product.images()).isNotEmpty().hasSizeLessThanOrEqualTo(10).allSatisfy(image -> {
                assertThat(image.alt().length()).isBetween(1, 200);
                assertThat(new ClassPathResource("catalogue-seed/images/" + image.file()).contentLength())
                        .isBetween(1L, 5_000_000L);
            });
        }
    }
}
