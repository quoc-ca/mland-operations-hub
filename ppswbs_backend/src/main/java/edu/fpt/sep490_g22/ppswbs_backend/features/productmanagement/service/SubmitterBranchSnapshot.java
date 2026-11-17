package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-061 (Session 2026-11-16): the submitter's branch scope, captured when a price proposal or a product review is
 * submitted and never rewritten afterwards.
 *
 * <ul>
 *   <li>Manager (or an internal call without an actor): {@code {"all": true}}.</li>
 *   <li>Staff: the branches assigned at submit time, read from the same {@link CatalogueBranchScope} the command
 *       checks, in id order, with the branch name of that moment (null when the branch is not in the active list or
 *       the branch source cannot be read; the id is kept).</li>
 *   <li>A system actor ({@code system:…}, e.g. seed data) has no scope: nothing is recorded.</li>
 * </ul>
 * A failure to read the assigned branches from the identity facade fails the submit like the scope check does.
 */
@Component
public class SubmitterBranchSnapshot {

    public static final String SYSTEM_ACTOR_PREFIX = "system:";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CatalogueBranchScope scope;
    private final ObjectProvider<SellingLocationPort> locations;

    public SubmitterBranchSnapshot(CatalogueBranchScope scope, ObjectProvider<SellingLocationPort> locations) {
        this.scope = scope;
        this.locations = locations;
    }

    public static boolean isSystemActor(String actorId) {
        return actorId != null && actorId.startsWith(SYSTEM_ACTOR_PREFIX);
    }

    /** JSON to store with the submit, or null for a system actor. */
    public String capture(String actorId) {
        if (isSystemActor(actorId)) {
            return null;
        }
        CatalogueBranchScope.Scope current = scope.current();
        Map<String, Object> value = new LinkedHashMap<>();
        if (current.all()) {
            value.put("all", true);
            return write(value);
        }
        Map<Long, String> names = activeNames();
        List<Map<String, Object>> branches = new ArrayList<>();
        current.locationIds().stream().sorted().forEach(locationId -> {
            Map<String, Object> branch = new LinkedHashMap<>();
            branch.put("locationId", locationId);
            branch.put("name", names.get(locationId));
            branches.add(branch);
        });
        value.put("all", false);
        value.put("branches", branches);
        return write(value);
    }

    /**
     * Display value: null while not submitted; SYSTEM for a system actor; NOT_RECORDED for rows submitted before the
     * snapshot existed (or unreadable JSON); otherwise ALL or LISTED.
     */
    public static ManagementProductDtos.SubmitterBranches read(String json, String submittedBy) {
        if (submittedBy == null) {
            return null;
        }
        if (isSystemActor(submittedBy)) {
            return new ManagementProductDtos.SubmitterBranches("SYSTEM", List.of());
        }
        if (json == null || json.isBlank()) {
            return notRecorded();
        }
        try {
            JsonNode node = JSON.readTree(json);
            if (node.path("all").asBoolean(false)) {
                return new ManagementProductDtos.SubmitterBranches("ALL", List.of());
            }
            List<ManagementProductDtos.SubmitterBranch> branches = new ArrayList<>();
            for (JsonNode branch : node.path("branches")) {
                if (!branch.path("locationId").canConvertToLong()) {
                    continue;
                }
                JsonNode name = branch.path("name");
                branches.add(new ManagementProductDtos.SubmitterBranch(
                        CatalogueValues.format(branch.path("locationId").asLong()),
                        name.isTextual() ? name.asText() : null));
            }
            return new ManagementProductDtos.SubmitterBranches("LISTED", List.copyOf(branches));
        } catch (JsonProcessingException e) {
            return notRecorded();
        }
    }

    private static ManagementProductDtos.SubmitterBranches notRecorded() {
        return new ManagementProductDtos.SubmitterBranches("NOT_RECORDED", List.of());
    }

    private Map<Long, String> activeNames() {
        Map<Long, String> names = new HashMap<>();
        try {
            SellingLocationPort port = locations.getIfAvailable();
            if (port != null) {
                for (SellingLocation location : port.activeSellingLocations()) {
                    names.put(location.id(), location.displayName());
                }
            }
        } catch (RuntimeException e) {
            // FR-061 (T170, decided 2026-11-17): the submission goes through without names; the ids are what was
            // assigned and the screens show "Chi nhánh #id", as FR-060 does for a name that cannot be read.
            return Map.of();
        }
        return names;
    }

    private static String write(Map<String, Object> value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Submitter branch snapshot is not serializable", e);
        }
    }
}
