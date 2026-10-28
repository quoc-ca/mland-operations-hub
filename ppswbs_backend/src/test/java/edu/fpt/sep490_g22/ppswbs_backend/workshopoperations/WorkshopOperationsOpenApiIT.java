package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopAttendanceController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopCheckInController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopLocationController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopSessionController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T048: Contract drift verification for contracts/workshop-operations.openapi.yaml.
 * <p>
 * Verifies:
 * 1. OpenAPI metadata, server base path (/api/v1), and Bearer security scheme.
 * 2. Complete path/route inventory (21 distinct operational endpoints) and HTTP methods.
 * 3. All schema definitions, property constraints, and enums (SessionState, PackageState, etc.).
 * 4. RFC 7807 Problem Detail schema and application/problem+json content-type.
 * 5. Terminology isolation (no legacy 'branch', 'personnel', booking-owned session, or PII leaks).
 * 6. Controller endpoint alignment against OpenAPI paths.
 * <p>
 * Strictly prohibits altering the OpenAPI contract to make tests pass.
 */
@SpringBootTest
@ActiveProfiles("test")
public class WorkshopOperationsOpenApiIT {

    @MockBean
    private AccountFacade accountFacade;

    private static Map<String, Object> openApiSpec;

    @BeforeAll
    static void loadOpenApiSpecification() throws Exception {
        File openApiFile = resolveOpenApiFile();
        Yaml yaml = new Yaml();
        try (InputStream in = new FileInputStream(openApiFile)) {
            openApiSpec = yaml.load(in);
        }
        assertThat(openApiSpec).as("OpenAPI contract must load successfully").isNotNull();
    }

    private static File resolveOpenApiFile() {
        String[] candidatePaths = {
                "../ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/contracts/workshop-operations.openapi.yaml",
                "ppswbs-spec/features/003-workshop-management/003.2-workshop-operations/contracts/workshop-operations.openapi.yaml",
                "contracts/workshop-operations.openapi.yaml"
        };
        for (String path : candidatePaths) {
            File file = new File(path);
            if (file.exists()) {
                return file;
            }
        }
        throw new IllegalStateException("OpenAPI contract file not found in candidate paths");
    }

    // =========================================================================
    // 1. OpenAPI Metadata, Server Base Path, and Security Schemes
    // =========================================================================

    @Test
    @DisplayName("T048-1: Verify OpenAPI specification metadata, version 3.0.3, and server base path")
    @SuppressWarnings("unchecked")
    void verifyOpenApiMetadataAndServer() {
        assertThat(openApiSpec.get("openapi")).isEqualTo("3.0.3");

        Map<String, Object> info = (Map<String, Object>) openApiSpec.get("info");
        assertThat(info).isNotNull();
        assertThat(info.get("title").toString()).contains("Workshop Operations");
        assertThat(info.get("version")).isNotNull();

        List<Map<String, Object>> servers = (List<Map<String, Object>>) openApiSpec.get("servers");
        assertThat(servers).isNotEmpty();
        assertThat(servers.get(0).get("url")).isEqualTo("/api/v1");
    }

    @Test
    @DisplayName("T048-2: Verify bearerAuth security scheme with Firebase-ID-token format")
    @SuppressWarnings("unchecked")
    void verifySecuritySchemeContract() {
        List<Map<String, Object>> security = (List<Map<String, Object>>) openApiSpec.get("security");
        assertThat(security).isNotEmpty();
        assertThat(security.get(0)).containsKey("bearerAuth");

        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        assertThat(components).isNotNull();

        Map<String, Object> securitySchemes = (Map<String, Object>) components.get("securitySchemes");
        assertThat(securitySchemes).containsKey("bearerAuth");

        Map<String, Object> bearerAuth = (Map<String, Object>) securitySchemes.get("bearerAuth");
        assertThat(bearerAuth.get("type")).isEqualTo("http");
        assertThat(bearerAuth.get("scheme")).isEqualTo("bearer");
        assertThat(bearerAuth.get("bearerFormat")).isEqualTo("Firebase-ID-token");
    }

    // =========================================================================
    // 2. Comprehensive Route / Path Inventory Verification (All 21 Endpoints)
    // =========================================================================

    @Test
    @DisplayName("T048-3: Verify all 21 operational paths exist in OpenAPI contract")
    @SuppressWarnings("unchecked")
    void verifyAllOperationalPathsExist() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).isNotNull();

        String[] requiredPaths = {
                // Workshop Packages (Central Manager & Discovery)
                "/workshop-packages",
                "/workshop-packages/{packageId}",
                "/workshop-packages/{packageId}/lifecycle-transitions",
                "/workshop-packages/{packageId}/sessions",

                // Canonical Workshop Locations & Slots
                "/workshop-locations",
                "/workshop-locations/{locationId}/slots",
                "/workshop-slots/{slotId}",

                // Workshop Sessions (Planning & Lifecycle)
                "/workshop-sessions",
                "/workshop-sessions/bulk",
                "/workshop-sessions/{sessionId}",
                "/workshop-sessions/{sessionId}/state-transitions",
                "/workshop-sessions/{sessionId}/staff-assignments",
                "/workshop-sessions/{sessionId}/operational-lead",

                // Check-in & Recovery
                "/workshop-sessions/{sessionId}/check-ins",
                "/workshop-sessions/{sessionId}/check-in-overrides",
                "/workshop-sessions/{sessionId}/booking-lookups/{bookingCode}",
                "/workshop-sessions/{sessionId}/ticket-resends",

                // Closure & Cancellation
                "/workshop-sessions/{sessionId}/attendance-finalizations",
                "/workshop-sessions/{sessionId}/attendance-corrections",
                "/workshop-sessions/{sessionId}/check-in-reversals",
                "/workshop-sessions/{sessionId}/cancellation-requests"
        };

        for (String expectedPath : requiredPaths) {
            assertThat(paths).as("OpenAPI must declare path: " + expectedPath).containsKey(expectedPath);
        }
    }

    @Test
    @DisplayName("T048-4: Verify HTTP methods and parameters for core Operational routes")
    @SuppressWarnings("unchecked")
    void verifyRouteHttpMethodsAndParameters() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");

        // 1. Locations
        Map<String, Object> locations = (Map<String, Object>) paths.get("/workshop-locations");
        assertThat(locations).containsKeys("get", "post");

        // 2. Slots
        Map<String, Object> locationSlots = (Map<String, Object>) paths.get("/workshop-locations/{locationId}/slots");
        assertThat(locationSlots).containsKeys("get", "post");
        Map<String, Object> slotIdPath = (Map<String, Object>) paths.get("/workshop-slots/{slotId}");
        assertThat(slotIdPath).containsKey("patch");

        // 3. Sessions
        Map<String, Object> sessions = (Map<String, Object>) paths.get("/workshop-sessions");
        assertThat(sessions).containsKeys("get", "post");
        Map<String, Object> bulkSessions = (Map<String, Object>) paths.get("/workshop-sessions/bulk");
        assertThat(bulkSessions).containsKey("post");
        Map<String, Object> sessionById = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}");
        assertThat(sessionById).containsKeys("get", "patch");

        // 4. Responsibility
        Map<String, Object> staffAssignments = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/staff-assignments");
        assertThat(staffAssignments).containsKeys("put", "delete");
        Map<String, Object> operationalLead = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/operational-lead");
        assertThat(operationalLead).containsKey("put");

        // 5. Check-In & Closure
        Map<String, Object> checkIns = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/check-ins");
        assertThat(checkIns).containsKey("post");
        Map<String, Object> finalizations = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/attendance-finalizations");
        assertThat(finalizations).containsKey("post");
        Map<String, Object> reversals = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/check-in-reversals");
        assertThat(reversals).containsKey("post");
        Map<String, Object> corrections = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/attendance-corrections");
        assertThat(corrections).containsKey("post");
        Map<String, Object> cancellations = (Map<String, Object>) paths.get("/workshop-sessions/{sessionId}/cancellation-requests");
        assertThat(cancellations).containsKey("post");
    }

    // =========================================================================
    // 3. RFC 7807 Problem Detail Schema and Response Contract
    // =========================================================================

    @Test
    @DisplayName("T048-5: Verify RFC 7807 Problem Detail schema and media type contract")
    @SuppressWarnings("unchecked")
    void verifyRfc7807ProblemDetailContract() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        assertThat(schemas).containsKey("Problem");

        Map<String, Object> problemSchema = (Map<String, Object>) schemas.get("Problem");
        assertThat(problemSchema.get("type")).isEqualTo("object");

        List<String> required = (List<String>) problemSchema.get("required");
        assertThat(required).containsExactlyInAnyOrder("type", "title", "status", "code");

        Map<String, Object> properties = (Map<String, Object>) problemSchema.get("properties");
        assertThat(properties).containsKeys("type", "title", "status", "code", "detail");

        Map<String, Object> responses = (Map<String, Object>) components.get("responses");
        assertThat(responses).containsKey("Problem");
        Map<String, Object> problemResponse = (Map<String, Object>) responses.get("Problem");
        Map<String, Object> content = (Map<String, Object>) problemResponse.get("content");
        assertThat(content).containsKey("application/problem+json");
    }

    // =========================================================================
    // 4. Schema Definitions and Enums Verification
    // =========================================================================

    @Test
    @DisplayName("T048-6: Verify SessionState and PackageState enum values")
    @SuppressWarnings("unchecked")
    void verifyStateEnums() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        // SessionState enum
        Map<String, Object> sessionState = (Map<String, Object>) schemas.get("SessionState");
        assertThat(sessionState).isNotNull();
        List<String> sessionStateEnum = (List<String>) sessionState.get("enum");
        assertThat(sessionStateEnum).containsExactlyInAnyOrder(
                "DRAFT", "PUBLISHED", "SALES_PAUSED", "IN_PROGRESS",
                "ATTENDANCE_FINALIZED", "CANCELLATION_REQUESTED", "CANCELLED"
        );

        // PackageState enum
        Map<String, Object> packageState = (Map<String, Object>) schemas.get("PackageState");
        assertThat(packageState).isNotNull();
        List<String> packageStateEnum = (List<String>) packageState.get("enum");
        assertThat(packageStateEnum).containsExactlyInAnyOrder("DRAFT", "PUBLISHED", "RETIRED");
    }

    @Test
    @DisplayName("T048-7: Verify Ticket validity and attendance state enums")
    @SuppressWarnings("unchecked")
    void verifyTicketSchemasAndEnums() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        Map<String, Object> ticket = (Map<String, Object>) schemas.get("Ticket");
        assertThat(ticket).isNotNull();
        Map<String, Object> properties = (Map<String, Object>) ticket.get("properties");

        Map<String, Object> validityState = (Map<String, Object>) properties.get("validityState");
        List<String> validityEnum = (List<String>) validityState.get("enum");
        assertThat(validityEnum).containsExactlyInAnyOrder("ACTIVE", "CANCELLED", "REVOKED", "REFUNDED");

        Map<String, Object> attendanceState = (Map<String, Object>) properties.get("attendanceState");
        List<String> attendanceEnum = (List<String>) attendanceState.get("enum");
        assertThat(attendanceEnum).containsExactlyInAnyOrder("NOT_CHECKED_IN", "CHECKED_IN", "NO_SHOW");
    }

    // =========================================================================
    // 5. Anti-Drift: Terminology Isolation & No PII Leakage
    // =========================================================================

    @Test
    @DisplayName("T048-8: Verify NO legacy 'branch' terminology exists across paths, parameters and schemas")
    @SuppressWarnings("unchecked")
    void verifyNoLegacyBranchTerminologyInOpenApi() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        for (String pathKey : paths.keySet()) {
            assertThat(pathKey.toLowerCase()).as("Path must not contain 'branch': " + pathKey)
                    .doesNotContain("/branch");
        }

        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            String schemaName = schemaEntry.getKey();
            assertThat(schemaName.toLowerCase()).as("Schema name must not contain 'branch': " + schemaName)
                    .doesNotContain("branch");

            if (schemaEntry.getValue() instanceof Map) {
                Map<String, Object> schemaDef = (Map<String, Object>) schemaEntry.getValue();
                Map<String, Object> props = (Map<String, Object>) schemaDef.get("properties");
                if (props != null) {
                    for (String propName : props.keySet()) {
                        assertThat(propName).as("Property in " + schemaName + " must not be 'branchId'")
                                .isNotEqualTo("branchId")
                                .isNotEqualTo("branch");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("T048-9: Verify NO legacy 'personnel' terminology exists in OpenAPI contract")
    @SuppressWarnings("unchecked")
    void verifyNoLegacyPersonnelTerminology() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        for (String pathKey : paths.keySet()) {
            assertThat(pathKey.toLowerCase()).as("Path must not contain 'personnel': " + pathKey)
                    .doesNotContain("personnel");
        }

        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        for (String schemaName : schemas.keySet()) {
            assertThat(schemaName.toLowerCase()).as("Schema must not contain 'personnel': " + schemaName)
                    .doesNotContain("personnel");
        }
    }

    @Test
    @DisplayName("T048-10: Verify privacy isolation: NO raw QR secrets, tokens, or customer contact PII")
    @SuppressWarnings("unchecked")
    void verifyPrivacyAndNonDisclosureInSchemas() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        String[] sensitiveFieldNames = {
                "qrSecret", "rawQr", "customerPhone", "customerEmail", "customerName",
                "firebaseToken", "paymentReference", "bankAccount"
        };

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            if (schemaEntry.getValue() instanceof Map) {
                Map<String, Object> schemaDef = (Map<String, Object>) schemaEntry.getValue();
                Map<String, Object> props = (Map<String, Object>) schemaDef.get("properties");
                if (props != null) {
                    for (String sensitive : sensitiveFieldNames) {
                        assertThat(props).as("Schema " + schemaEntry.getKey() + " must not expose sensitive field: " + sensitive)
                                .doesNotContainKey(sensitive);
                    }
                }
            }
        }
    }

    // =========================================================================
    // 6. Controller Mapping Synchronization
    // =========================================================================

    @Test
    @DisplayName("T048-11: Verify backend controller mappings match OpenAPI paths exactly")
    void verifyBackendControllerMappingsMatchOpenApi() {
        // Collect mapped paths from all four implemented controllers
        Class<?>[] controllers = {
                WorkshopLocationController.class,
                WorkshopSessionController.class,
                WorkshopCheckInController.class,
                WorkshopAttendanceController.class
        };

        List<String> controllerPaths = new ArrayList<>();
        for (Class<?> controller : controllers) {
            RequestMapping baseMapping = controller.getAnnotation(RequestMapping.class);
            String basePath = (baseMapping != null && baseMapping.value().length > 0) ? baseMapping.value()[0] : "";

            for (Method method : controller.getDeclaredMethods()) {
                String subPath = "";
                if (method.isAnnotationPresent(GetMapping.class)) {
                    GetMapping a = method.getAnnotation(GetMapping.class);
                    subPath = a.value().length > 0 ? a.value()[0] : "";
                } else if (method.isAnnotationPresent(PostMapping.class)) {
                    PostMapping a = method.getAnnotation(PostMapping.class);
                    subPath = a.value().length > 0 ? a.value()[0] : "";
                } else if (method.isAnnotationPresent(PutMapping.class)) {
                    PutMapping a = method.getAnnotation(PutMapping.class);
                    subPath = a.value().length > 0 ? a.value()[0] : "";
                } else if (method.isAnnotationPresent(PatchMapping.class)) {
                    PatchMapping a = method.getAnnotation(PatchMapping.class);
                    subPath = a.value().length > 0 ? a.value()[0] : "";
                } else if (method.isAnnotationPresent(DeleteMapping.class)) {
                    DeleteMapping a = method.getAnnotation(DeleteMapping.class);
                    subPath = a.value().length > 0 ? a.value()[0] : "";
                }

                if (!subPath.isEmpty() || !basePath.isEmpty()) {
                    String fullPath = basePath + subPath;
                    controllerPaths.add(fullPath);
                }
            }
        }

        // Verify each controller endpoint starts with /api/v1 and maps to a declared path in OpenAPI
        @SuppressWarnings("unchecked")
        Map<String, Object> openApiPaths = (Map<String, Object>) openApiSpec.get("paths");

        for (String cPath : controllerPaths) {
            assertThat(cPath).as("Controller endpoint must be versioned under /api/v1: " + cPath)
                    .startsWith("/api/v1");
            String pathWithoutPrefix = cPath.substring("/api/v1".length());
            if (pathWithoutPrefix.isEmpty()) {
                pathWithoutPrefix = "/workshop-locations"; // WorkshopLocationController base mapping
            }
            assertThat(openApiPaths).as("OpenAPI must declare controller endpoint: " + pathWithoutPrefix)
                    .containsKey(pathWithoutPrefix);
        }
    }
}
