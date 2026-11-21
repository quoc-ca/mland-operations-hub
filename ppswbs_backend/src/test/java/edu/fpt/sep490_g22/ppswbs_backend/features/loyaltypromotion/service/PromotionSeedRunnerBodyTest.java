package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.PromotionSeedCataloguePort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** T116: the seed resolves names only through the 002 port; nothing found means the programme is skipped. */
class PromotionSeedRunnerBodyTest {

    private static final Instant NOW = Instant.parse("2026-11-21T00:00:00Z");

    private static final PromotionSeedCataloguePort PORT = new PromotionSeedCataloguePort() {
        @Override public List<Long> productIdsInCategory(String name) {
            return Map.of("Nhẫn đá quý", List.of(5L, 6L)).getOrDefault(name, List.of());
        }
        @Override public List<Long> productIdsNamed(String name) {
            return Map.of("Nhẫn bạc", List.of(6L, 9L)).getOrDefault(name, List.of());
        }
        @Override public long publishedProductCount(String name) {
            return 0;
        }
        @Override public List<Long> activeBranchIdsNamed(String name) {
            return Map.of("Mland Hà Nội – Hoàn Kiếm", List.of(1L)).getOrDefault(name, List.of());
        }
    };

    private static JsonNode program(String seedTargets) throws Exception {
        return new ObjectMapper().readTree("""
                {"kind":"VOUCHER","scope":"PRODUCT","code":"MLX","startOffsetDays":1,"durationDays":10,
                 "seedTargets":%s}""".formatted(seedTargets));
    }

    @Test
    void namesBecomeIdsThroughThePortWithoutDuplicates() throws Exception {
        ObjectNode body = PromotionSeedRunner.body(PORT, program(
                "{\"categories\":[\"Nhẫn đá quý\"],\"products\":[\"Nhẫn bạc\"],\"branchNames\":[\"Mland Hà Nội – Hoàn Kiếm\"]}"),
                NOW);
        assertThat(body.path("targets").path("productIds").toString()).isEqualTo("[\"5\",\"6\",\"9\"]");
        assertThat(body.path("targets").path("branchIds").toString()).isEqualTo("[\"1\"]");
        assertThat(body.has("seedTargets")).isFalse();
        assertThat(body.path("startAt").asText()).isEqualTo("2026-11-22T00:00:00Z");
        assertThat(body.path("endAt").asText()).isEqualTo("2026-12-02T00:00:00Z");
    }

    @Test
    void anUnknownBranchOrNoProductSkipsTheProgramme() throws Exception {
        assertThat(PromotionSeedRunner.body(PORT, program(
                "{\"categories\":[\"Nhẫn đá quý\"],\"branchNames\":[\"Chi nhánh khác\"]}"), NOW)).isNull();
        assertThat(PromotionSeedRunner.body(PORT, program("{\"categories\":[\"Không có\"]}"), NOW)).isNull();
    }

    @Test
    void theCreatorIsTheSystem() {
        assertThat(PromotionSeedRunner.SYSTEM_CREATOR).isEqualTo("0");
    }
}
