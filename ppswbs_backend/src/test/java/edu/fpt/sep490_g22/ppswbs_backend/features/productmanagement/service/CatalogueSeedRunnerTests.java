package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
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
            assertThat(product.images()).isNotEmpty().hasSizeLessThanOrEqualTo(10).allSatisfy(image -> {
                assertThat(image.alt().length()).isBetween(1, 200);
                assertThat(new ClassPathResource("catalogue-seed/images/" + image.file()).contentLength())
                        .isBetween(1L, 5_000_000L);
            });
        }
    }
}
