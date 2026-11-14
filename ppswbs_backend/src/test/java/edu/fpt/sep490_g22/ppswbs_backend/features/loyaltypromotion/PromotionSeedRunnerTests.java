package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionWriteDtos;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Every entry of the sample promotion file passes the API's own create parser once its targets are ids. */
class PromotionSeedRunnerTests {

    @Test
    void everySeedProgrammeIsAValidCreateBodyWithAUniqueCodeOrName() throws Exception {
        JsonNode seed;
        try (InputStream in = new ClassPathResource("promotion-seed/promotion-seed.json").getInputStream()) {
            seed = new ObjectMapper().readTree(in);
        }
        Set<String> identities = new HashSet<>();
        int count = 0;
        for (JsonNode program : seed.path("programs")) {
            count++;
            String identity = program.hasNonNull("code") ? program.path("code").asText() : program.path("name").asText();
            assertThat(identities.add(identity)).as("unique %s", identity).isTrue();

            ObjectNode body = program.deepCopy();
            body.remove(List.of("startOffsetDays", "durationDays", "seedTargets"));
            Instant start = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            body.put("startAt", start.toString());
            body.put("endAt", start.plus(program.path("durationDays").asLong(), ChronoUnit.DAYS).toString());
            ObjectNode targets = body.putObject("targets");
            if ("PRODUCT".equals(program.path("scope").asText())) {
                targets.putArray("productIds").add("1");
            }
            if (program.path("seedTargets").has("branchCodes")) {
                targets.putArray("branchIds").add("1");
            }
            assertThatCode(() -> PromotionWriteDtos.parseCreate(body)).as(identity).doesNotThrowAnyException();
        }
        assertThat(count).isEqualTo(10);
    }
}
