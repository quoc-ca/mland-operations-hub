package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dao.PromotionProgramRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionWriteDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.PromotionSeedCataloguePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Sample promotion programmes for every developer database (2026-11-13), read from
 * {@code classpath:promotion-seed/promotion-seed.json}: product vouchers, automatic discounts and workshop vouchers
 * for the sample catalogue of 002 ({@code CatalogueSeedRunner}). Runs once in the background after start-up.
 *
 * <ul>
 *   <li>Each entry is the body of {@code POST /api/v1/promotion-programs}, parsed by the same
 *       {@link PromotionWriteDtos#parseCreate} and created by {@link PromotionCreationService} (same validation,
 *       target checks through 002/003, audit and command receipt). Only times (days from now) and targets (category
 *       names, product names, branch display names) are written in a portable form and resolved here.</li>
 *   <li>Constitution I (T116): no other module's table is read. Names become ids through
 *       {@link PromotionSeedCataloguePort}, implemented by 002; 006's own records go through its repositories.
 *       Lookups that no owner offers are not seeded: the creator is the system ({@code 0}, no foreign key on
 *       {@code created_by}) rather than a Manager account, and there is no sample workshop automatic discount (it
 *       would need the list of published 003 packages).</li>
 *   <li>Targets are fixed when a programme is created, so it waits until the sample products stop appearing (the
 *       catalogue seed runs at the same time): the published product count of the target categories must stay the
 *       same for three checks in a row, at most {@value #WAIT_MINUTES} minutes.</li>
 *   <li>Idempotent: a voucher whose code exists, or an automatic discount whose current name exists, is skipped.</li>
 *   <li>Off with {@code app.promotion.seed.enabled=false}, or without a {@link PromotionSeedCataloguePort} bean; never
 *       runs in tests (JUnit on the class path).</li>
 * </ul>
 */
@Component
public class PromotionSeedRunner {

    private static final String RESOURCE = "promotion-seed/promotion-seed.json";
    private static final long WAIT_MINUTES = 15;
    private static final Duration POLL = Duration.ofSeconds(15);
    /** The system as creator: no Manager lookup is offered to 006 (T116). */
    static final String SYSTEM_CREATOR = "0";
    private static final Logger log = LoggerFactory.getLogger(PromotionSeedRunner.class);

    private final boolean enabled;
    private final ObjectMapper json;
    private final ObjectProvider<PromotionSeedCataloguePort> catalogue;
    private final PromotionProgramRepository programs;
    private final PromotionCreationService creation;

    public PromotionSeedRunner(@Value("${app.promotion.seed.enabled:true}") boolean enabled, ObjectMapper json,
                               ObjectProvider<PromotionSeedCataloguePort> catalogue,
                               PromotionProgramRepository programs, PromotionCreationService creation) {
        this.enabled = enabled;
        this.json = json;
        this.catalogue = catalogue;
        this.programs = programs;
        this.creation = creation;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled || ClassUtils.isPresent("org.junit.jupiter.api.Test", getClass().getClassLoader())) {
            return;
        }
        PromotionSeedCataloguePort port = catalogue.getIfAvailable();
        if (port == null) {
            log.warn("Promotion seed skipped: no sample catalogue provider (002).");
            return;
        }
        Thread worker = new Thread(() -> runSafely(port), "promotion-seed");
        worker.setDaemon(true);
        worker.start();
    }

    private void runSafely(PromotionSeedCataloguePort port) {
        try {
            int created = run(port);
            if (created > 0) {
                log.info("Promotion seed: {} sample programme(s) created.", created);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | IOException e) {
            log.warn("Promotion seed stopped: {}", e.toString());
        }
    }

    int run(PromotionSeedCataloguePort port) throws IOException, InterruptedException {
        JsonNode seed;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            seed = json.readTree(in);
        }
        List<JsonNode> pending = new ArrayList<>();
        Set<String> categories = new LinkedHashSet<>();
        for (JsonNode program : seed.path("programs")) {
            if (!exists(program)) {
                pending.add(program);
                program.path("seedTargets").path("categories").forEach(name -> categories.add(name.asText()));
            }
        }
        if (pending.isEmpty()) {
            return 0;
        }
        waitForCatalogue(port, categories);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        int created = 0;
        for (JsonNode program : pending) {
            String label = program.path("name").asText();
            try {
                ObjectNode body = body(port, program, now);
                if (body == null) {
                    log.warn("Promotion seed: '{}' skipped, its products or branches do not exist.", label);
                    continue;
                }
                creation.create(SYSTEM_CREATOR, key(program), PromotionWriteDtos.parseCreate(body));
                created++;
            } catch (LoyaltyException e) {
                log.warn("Promotion seed: '{}' not created ({}).", label, e.getMessage());
            }
        }
        return created;
    }

    /** The API body: times from now, targets resolved to ids; null when a named target resolves to nothing. */
    static ObjectNode body(PromotionSeedCataloguePort port, JsonNode program, Instant now) {
        ObjectNode body = program.deepCopy();
        body.remove(List.of("startOffsetDays", "durationDays", "seedTargets"));
        Instant start = now.plus(program.path("startOffsetDays").asLong(0), ChronoUnit.DAYS);
        body.put("startAt", start.toString());
        body.put("endAt", start.plus(program.path("durationDays").asLong(30), ChronoUnit.DAYS).toString());

        JsonNode wanted = program.path("seedTargets");
        ObjectNode targets = body.putObject("targets");
        Set<Long> branchIds = new LinkedHashSet<>();
        for (JsonNode name : wanted.path("branchNames")) {
            branchIds.addAll(port.activeBranchIdsNamed(name.asText()));
        }
        if (wanted.path("branchNames").size() > 0 && branchIds.isEmpty()) {
            return null;
        }
        if ("PRODUCT".equals(program.path("scope").asText())) {
            Set<Long> productIds = new LinkedHashSet<>();
            for (JsonNode category : wanted.path("categories")) {
                productIds.addAll(port.productIdsInCategory(category.asText()));
            }
            for (JsonNode name : wanted.path("products")) {
                productIds.addAll(port.productIdsNamed(name.asText()));
            }
            if (productIds.isEmpty()) {
                return null;
            }
            ArrayNode ids = targets.putArray("productIds");
            productIds.forEach(id -> ids.add(Long.toString(id)));
        }
        if (!branchIds.isEmpty()) {
            ArrayNode ids = targets.putArray("branchIds");
            branchIds.forEach(id -> ids.add(Long.toString(id)));
        }
        return body;
    }

    private boolean exists(JsonNode program) {
        if ("VOUCHER".equals(program.path("kind").asText())) {
            return programs.existsByCodeKey(program.path("code").asText().trim().toUpperCase(Locale.ROOT));
        }
        return programs.existsAutoDiscountNamed(program.path("name").asText());
    }

    /** Waits until the published product count of the categories is the same three checks in a row (or time is up). */
    private void waitForCatalogue(PromotionSeedCataloguePort port, Set<String> categories) throws InterruptedException {
        if (categories.isEmpty()) {
            return;
        }
        Instant deadline = Instant.now().plus(Duration.ofMinutes(WAIT_MINUTES));
        long last = -1;
        int stable = 0;
        while (Instant.now().isBefore(deadline)) {
            long count = 0;
            for (String category : categories) {
                count += port.publishedProductCount(category);
            }
            stable = count > 0 && count == last ? stable + 1 : 0;
            if (stable >= 2) {
                return;
            }
            last = count;
            Thread.sleep(POLL.toMillis());
        }
    }

    static UUID key(JsonNode program) {
        String id = program.hasNonNull("code") ? program.path("code").asText() : program.path("name").asText();
        return UUID.nameUUIDFromBytes(("promotion-seed:" + id).getBytes(StandardCharsets.UTF_8));
    }
}
