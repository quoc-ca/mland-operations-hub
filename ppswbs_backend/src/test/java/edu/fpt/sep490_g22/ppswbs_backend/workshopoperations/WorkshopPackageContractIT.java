package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.CustomerSessionAvailabilityResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageLifecycleTargetState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageLifecycleTransitionRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.WorkshopPackageState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackage;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WorkshopPackageContractIT {

    private static Map<String, Object> openApi;
    private static Validator validator;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadOpenApiContract() throws Exception {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        File contract = List.of(
                        new File("../ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/contracts/workshop-operations.openapi.yaml"),
                        new File("ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/contracts/workshop-operations.openapi.yaml"),
                        new File("contracts/workshop-operations.openapi.yaml"))
                .stream().filter(File::isFile).findFirst()
                .orElseThrow(() -> new IllegalStateException("Workshop Operations OpenAPI contract not found"));
        try (InputStream input = new FileInputStream(contract)) {
            openApi = new Yaml().load(input);
        }
        assertThat(openApi).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void packageRoutesMatchOpenApiSecurityAndResponseContracts() {
        Map<String, Object> paths = (Map<String, Object>) openApi.get("paths");
        assertThat(paths).containsKeys("/workshop-packages", "/workshop-package-management/packages", "/workshop-packages/{packageId}",
                "/workshop-packages/{packageId}/lifecycle-transitions", "/workshop-packages/{packageId}/sessions");

        Map<String, Object> collection = (Map<String, Object>) paths.get("/workshop-packages");
        assertOperationResponse(collection, "get", "200", "#/components/responses/PackageList");
        assertOperationResponse(collection, "post", "201", "#/components/responses/Package");
        assertPublicOperation(collection, "get");
        assertAuthenticatedOperation(collection, "post");
        assertIdempotencyKey(collection, "post");

        Map<String, Object> management = (Map<String, Object>) paths.get("/workshop-package-management/packages");
        assertOperationResponse(management, "get", "200", "#/components/responses/PackageManagementList");
        assertAuthenticatedOperation(management, "get");
        Map<String, Object> managementGet = (Map<String, Object>) management.get("get");
        assertThat(((List<Map<String, Object>>) managementGet.get("parameters")))
                .anySatisfy(parameter -> assertThat(parameter).containsEntry("name", "state").containsEntry("in", "query"));

        Map<String, Object> item = (Map<String, Object>) paths.get("/workshop-packages/{packageId}");
        assertOperationResponse(item, "get", "200", "#/components/responses/Package");
        assertOperationResponse(item, "patch", "200", "#/components/responses/Package");
        assertPublicOperation(item, "get");
        assertAuthenticatedOperation(item, "patch");
        assertIdempotencyKey(item, "patch");

        Map<String, Object> lifecycle = (Map<String, Object>) paths.get("/workshop-packages/{packageId}/lifecycle-transitions");
        assertOperationResponse(lifecycle, "post", "200", "#/components/responses/Package");
        assertAuthenticatedOperation(lifecycle, "post");
        assertIdempotencyKey(lifecycle, "post");

        Map<String, Object> publicAddOns = (Map<String, Object>) paths.get("/workshop-packages/{packageId}/locations/{locationId}/add-ons");
        assertOperationResponse(publicAddOns, "get", "200", "#/components/responses/AddOnList");
        assertOperationResponse(publicAddOns, "post", "201", "#/components/responses/AddOn");
        assertPublicOperation(publicAddOns, "get");
        assertAuthenticatedOperation(publicAddOns, "post");
        assertIdempotencyKey(publicAddOns, "post");

        Map<String, Object> managementAddOns = (Map<String, Object>) paths.get("/workshop-package-management/packages/{packageId}/locations/{locationId}/add-ons");
        assertOperationResponse(managementAddOns, "get", "200", "#/components/responses/AddOnList");
        assertAuthenticatedOperation(managementAddOns, "get");
        assertThat(managementAddOns).doesNotContainKey("post");
    }

    @Test
    @SuppressWarnings("unchecked")
    void packageWriteAndResponseSchemasMatchDtoFieldsAndWholeVndRules() {
        Map<String, Object> schemas = schemas();
        Map<String, Object> writeSchema = (Map<String, Object>) schemas.get("PackageWrite");
        Map<String, Object> properties = (Map<String, Object>) writeSchema.get("properties");
        Set<String> expected = Set.of("name", "description", "publicPrice", "minParticipants", "maxParticipants",
                "requiredSessionCount", "sameLocationRequired", "minimumSameDaySlotGap",
                "requiredFoundryWorkingDaysBetweenSessions");
        assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat((List<String>) writeSchema.get("required")).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(((Map<String, Object>) properties.get("publicPrice"))).containsEntry("type", "integer")
                .containsEntry("format", "int64").containsEntry("minimum", 0);
        assertThat(((Map<String, Object>) properties.get("minimumSameDaySlotGap"))).containsEntry("minimum", 0);
        assertThat(((Map<String, Object>) properties.get("requiredFoundryWorkingDaysBetweenSessions"))).containsEntry("minimum", 0);

        PackageRequest valid = validPackageRequest();
        assertThat(validator.validate(valid)).isEmpty();

        // 1. DTO accepts 0 (one-session default) and 2 (Wax Casting)
        assertThat(validator.validate(valid.toBuilder().requiredFoundryWorkingDaysBetweenSessions(0).build())).isEmpty();
        assertThat(validator.validate(valid.toBuilder().requiredFoundryWorkingDaysBetweenSessions(2).build())).isEmpty();

        // 2. DTO rejects negative values
        assertThat(validator.validate(valid.toBuilder().requiredFoundryWorkingDaysBetweenSessions(-1).build())).isNotEmpty();

        assertThat(validator.validate(PackageRequest.builder().build())).isNotEmpty();
        assertThat(validator.validate(PackageRequest.builder().name("A").description("B").publicPrice(10L)
                .minParticipants(5).maxParticipants(4).requiredSessionCount(1).sameLocationRequired(true)
                .minimumSameDaySlotGap(0).requiredFoundryWorkingDaysBetweenSessions(0).build())).isNotEmpty();
        assertThat(validator.validate(PackageRequest.builder().name("A").description("B").publicPrice(-1L)
                .minParticipants(1).maxParticipants(1).requiredSessionCount(1).sameLocationRequired(true)
                .minimumSameDaySlotGap(-1).requiredFoundryWorkingDaysBetweenSessions(0).build())).isNotEmpty();

        Map<String, Object> responseSchema = (Map<String, Object>) schemas.get("Package");
        List<Map<String, Object>> allOf = (List<Map<String, Object>>) responseSchema.get("allOf");
        assertThat(allOf).anySatisfy(part -> assertThat(part.get("$ref")).isEqualTo("#/components/schemas/PackageWrite"));
        Map<String, Object> responseProperties = (Map<String, Object>) ((Map<String, Object>) allOf.get(1).get("properties"));
        assertThat(responseProperties).containsKeys("packageId", "state");

        PackageResponse response = PackageResponse.builder().packageId(1L).name("Package")
                .description("Description").publicPrice(100L).minParticipants(1).maxParticipants(4)
                .requiredSessionCount(1).sameLocationRequired(true).minimumSameDaySlotGap(0)
                .requiredFoundryWorkingDaysBetweenSessions(0)
                .state(WorkshopPackageState.PUBLISHED).build();
        assertThat(response.getState()).isEqualTo(WorkshopPackageState.PUBLISHED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lifecycleTransitionIsLimitedToPublishAndRetire() {
        Map<String, Object> schemas = schemas();
        Map<String, Object> lifecycleSchema = (Map<String, Object>) schemas.get("PackageLifecycleTransitionRequest");
        Map<String, Object> properties = (Map<String, Object>) lifecycleSchema.get("properties");
        Map<String, Object> targetState = (Map<String, Object>) properties.get("targetState");
        assertThat((List<String>) lifecycleSchema.get("required")).containsExactly("targetState");
        assertThat((List<String>) targetState.get("enum")).containsExactly("PUBLISHED", "RETIRED");
        assertThat(validator.validate(new PackageLifecycleTransitionRequest(PackageLifecycleTargetState.PUBLISHED))).isEmpty();
        assertThat(validator.validate(new PackageLifecycleTransitionRequest(PackageLifecycleTargetState.RETIRED))).isEmpty();
        assertThat(validator.validate(new PackageLifecycleTransitionRequest(null))).isNotEmpty();
    }

    @SuppressWarnings("unchecked")
    private static void assertIdempotencyKey(Map<String, Object> path, String method) {
        Map<String, Object> operation = (Map<String, Object>) path.get(method);
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        assertThat(parameters).anySatisfy(parameter -> assertThat(parameter)
                .containsEntry("$ref", "#/components/parameters/IdempotencyKey"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicCustomerSessionDiscoveryUsesOpaqueLocationScopedSafeProjection() {
        Map<String, Object> paths = (Map<String, Object>) openApi.get("paths");
        Map<String, Object> discovery = (Map<String, Object>) paths.get("/workshop-packages/{packageId}/sessions");
        Map<String, Object> get = (Map<String, Object>) discovery.get("get");
        assertOperationResponse(discovery, "get", "200", "#/components/responses/CustomerSessionAvailabilityList");
        assertPublicOperation(discovery, "get");
        Map<String, Object> parameters = (Map<String, Object>) ((List<Map<String, Object>>) get.get("parameters"))
                .stream().filter(p -> "locationId".equals(p.get("name"))).findFirst().orElseThrow();
        assertThat(parameters.get("in")).isEqualTo("query");
        assertThat(parameters.get("required")).isEqualTo(true);

        Map<String, Object> schema = (Map<String, Object>) schemas().get("CustomerSessionAvailability");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat((List<String>) schema.get("required")).containsExactlyInAnyOrder("sessionReference", "locationId",
                "scheduledStart", "scheduledEnd", "saleState", "customerSelectable", "remainingCapacity");
        assertThat((Map<String, Object>) properties.get("sessionReference")).containsEntry("type", "string")
                .containsEntry("format", "uuid");
        assertThat((Map<String, Object>) properties.get("locationId")).containsEntry("type", "integer")
                .containsEntry("format", "int64");

        CustomerSessionAvailabilityResponse projection = new CustomerSessionAvailabilityResponse(UUID.randomUUID(), 7L,
                Instant.parse("2026-11-01T02:00:00Z"), Instant.parse("2026-11-01T03:00:00Z"),
                "OPEN", true, 12);
        assertThat(validator.validate(projection)).isEmpty();
        assertThat(validator.validate(new CustomerSessionAvailabilityResponse(null, 7L, Instant.now(), Instant.now(),
                "OPEN", true, -1))).isNotEmpty();
    }

    @Test
    void dtoSurfaceDoesNotIntroduceBookingTicketStaffOrAuditData() {
        Set<String> packageFields = Arrays.stream(PackageRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).collect(java.util.stream.Collectors.toSet());
        Set<String> projectionFields = Arrays.stream(CustomerSessionAvailabilityResponse.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).collect(java.util.stream.Collectors.toSet());
        assertThat(packageFields).doesNotContain("bookingReference", "ticketCode", "staffAccountId", "auditMetadata");
        assertThat(projectionFields).doesNotContain("bookingReference", "ticketCode", "staffAccountId", "auditMetadata",
                "branchId", "personnelId");
    }

    @Test
    void packageSnapshotRetainsFoundryWorkingDays() {
        // One-session package retains 0 foundry working days
        WorkshopPackage oneSession = WorkshopPackage.builder()
                .id(10L).name("Basic Ring").description("One session").publicPrice(500_000L)
                .minParticipants(1).maxParticipants(10).requiredSessionCount(1).sameLocationRequired(true)
                .minimumSameDaySlotGap(0).requiredFoundryWorkingDaysBetweenSessions(0)
                .state(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState.PUBLISHED).build();
        PublishedPackageSelectionSnapshot oneSessionSnapshot = new PublishedPackageSelectionSnapshot(
                oneSession.getId().toString(), oneSession.getPublicPrice(), oneSession.getMinParticipants(),
                oneSession.getMaxParticipants(), oneSession.getRequiredSessionCount(),
                oneSession.isSameLocationRequired(), oneSession.getMinimumSameDaySlotGap(),
                oneSession.getRequiredFoundryWorkingDaysBetweenSessions(), oneSession.getName());
        assertThat(oneSessionSnapshot.requiredFoundryWorkingDaysBetweenSessions()).isEqualTo(0);

        // Wax Casting package retains 2 foundry working days
        WorkshopPackage waxCasting = WorkshopPackage.builder()
                .id(20L).name("Wax Casting").description("Foundry calendar package").publicPrice(1_200_000L)
                .minParticipants(1).maxParticipants(6).requiredSessionCount(2).sameLocationRequired(true)
                .minimumSameDaySlotGap(0).requiredFoundryWorkingDaysBetweenSessions(2)
                .state(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopPackageState.PUBLISHED).build();
        PublishedPackageSelectionSnapshot waxCastingSnapshot = new PublishedPackageSelectionSnapshot(
                waxCasting.getId().toString(), waxCasting.getPublicPrice(), waxCasting.getMinParticipants(),
                waxCasting.getMaxParticipants(), waxCasting.getRequiredSessionCount(),
                waxCasting.isSameLocationRequired(), waxCasting.getMinimumSameDaySlotGap(),
                waxCasting.getRequiredFoundryWorkingDaysBetweenSessions(), waxCasting.getName());
        assertThat(waxCastingSnapshot.requiredFoundryWorkingDaysBetweenSessions()).isEqualTo(2);
    }

    private static PackageRequest validPackageRequest() {
        return PackageRequest.builder().name("Basic Ring").description("One session")
                .publicPrice(500_000L).minParticipants(1).maxParticipants(10).requiredSessionCount(1)
                .sameLocationRequired(true).minimumSameDaySlotGap(0)
                .requiredFoundryWorkingDaysBetweenSessions(0).build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemas() {
        Map<String, Object> components = (Map<String, Object>) openApi.get("components");
        return (Map<String, Object>) components.get("schemas");
    }

    @SuppressWarnings("unchecked")
    private static void assertOperationResponse(Map<String, Object> path, String method, String status, String responseRef) {
        Map<String, Object> operation = (Map<String, Object>) path.get(method);
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        assertThat(responses).containsKey(status);
        assertThat(((Map<String, Object>) responses.get(status)).get("$ref")).isEqualTo(responseRef);
    }

    @SuppressWarnings("unchecked")
    private static void assertPublicOperation(Map<String, Object> path, String method) {
        Map<String, Object> operation = (Map<String, Object>) path.get(method);
        assertThat((List<Object>) operation.get("security")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static void assertAuthenticatedOperation(Map<String, Object> path, String method) {
        Map<String, Object> operation = (Map<String, Object>) path.get(method);
        assertThat(operation).doesNotContainKey("security");
    }
}
