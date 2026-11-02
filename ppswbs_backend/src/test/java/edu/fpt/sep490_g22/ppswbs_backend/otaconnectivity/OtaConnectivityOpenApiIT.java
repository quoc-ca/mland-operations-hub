package edu.fpt.sep490_g22.ppswbs_backend.otaconnectivity;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T053: Contract drift & API documentation verification for
 * contracts/ota-connectivity.openapi.yaml against backend controllers and DTOs.
 *
 * Verifies:
 * 1. OpenAPI 3.1.0 specification metadata, title, and base server URL (/api/v1).
 * 2. Complete 14-route operational inventory and HTTP methods.
 * 3. Exact alignment between Spring controllers and OpenAPI paths.
 * 4. Response status codes (200, 201, 202) and typed error responses (400, 401, 403, 404, 409, 422).
 * 5. Schema completeness, property constraints, and additionalProperties: false enforcement.
 * 6. Enum fidelity across domain models and OpenAPI contract.
 * 7. Privacy non-disclosure: strictly prohibited credentials, tokens, QR secrets, or customer PII.
 * 8. Header parameters (Idempotency-Key) and path parameters (UUID format).
 *
 * Strictly prohibits altering the OpenAPI contract to make tests pass.
 */
@DisplayName("003.4 T053: OTA Connectivity OpenAPI Contract Drift & Documentation Verification")
public class OtaConnectivityOpenApiIT {

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
                "../ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/contracts/ota-connectivity.openapi.yaml",
                "ppswbs-spec/features/003-workshop-management/003.4-workshop-ota-connectivity/contracts/ota-connectivity.openapi.yaml",
                "contracts/ota-connectivity.openapi.yaml"
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
    // 1. OpenAPI Specification Metadata and Server Base Path
    // =========================================================================

    @Test
    @DisplayName("T053-1: Verify OpenAPI specification metadata, version 3.1.0, and server base path")
    @SuppressWarnings("unchecked")
    void verifyOpenApiMetadataAndServer() {
        assertThat(openApiSpec.get("openapi")).isEqualTo("3.1.0");

        Map<String, Object> info = (Map<String, Object>) openApiSpec.get("info");
        assertThat(info).isNotNull();
        assertThat(info.get("title")).isEqualTo("Mland OTA Connectivity API");
        assertThat(info.get("version")).isNotNull();

        List<Map<String, Object>> servers = (List<Map<String, Object>>) openApiSpec.get("servers");
        assertThat(servers).isNotEmpty();
        assertThat(servers.get(0).get("url")).isEqualTo("/api/v1");
    }

    // =========================================================================
    // 2. Comprehensive Route / Path Inventory Verification (All 14 Endpoints)
    // =========================================================================

    @Test
    @DisplayName("T053-2: Verify all 14 operational REST paths exist in OpenAPI contract")
    @SuppressWarnings("unchecked")
    void verifyAllOperationalPathsExist() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        assertThat(paths).isNotNull();

        String[] requiredPaths = {
                // Channel & Mapping management
                "/ota-channels",
                "/ota-channels/{channelId}/mappings",
                "/ota-channel-mappings/{mappingId}",
                "/ota-channel-mappings/{mappingId}/price-terms",
                "/ota-channel-mappings/{mappingId}/sync",
                "/ota-channel-mappings/{mappingId}/sales-close",
                "/ota-channel-mappings/{mappingId}/booking-enable",

                // Reconciliation & Sync Recovery
                "/ota-reconciliation-work-items",
                "/ota-reconciliation-work-items/{workItemId}/resolve",

                // Financial Evidence & Payout Reconciliation
                "/ota-channel-bookings/{channelBookingId}/financial-evidence",
                "/ota-channel-bookings/{channelBookingId}/payout-reconciliation",

                // Shop Add-ons outside OTA checkout
                "/ota-channel-bookings/{channelBookingId}/add-on-quotes",
                "/ota-addon-quotes/{quoteId}/acceptance",
                "/ota-addon-quotes/{quoteId}/direct-bill"
        };

        for (String expectedPath : requiredPaths) {
            assertThat(paths).as("OpenAPI must declare path: " + expectedPath).containsKey(expectedPath);
        }
        assertThat(paths).hasSize(requiredPaths.length);
    }

    @Test
    @DisplayName("T053-3: Verify HTTP methods and operationIds for all 14 operational routes")
    @SuppressWarnings("unchecked")
    void verifyRouteHttpMethodsAndOperationIds() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");

        // 1. Channel catalog
        Map<String, Object> channels = (Map<String, Object>) paths.get("/ota-channels");
        assertThat(channels).containsKey("get");
        assertThat(((Map<String, Object>) channels.get("get")).get("operationId")).isEqualTo("listOtaChannels");

        // 2. Mapping creation
        Map<String, Object> mappings = (Map<String, Object>) paths.get("/ota-channels/{channelId}/mappings");
        assertThat(mappings).containsKey("post");
        assertThat(((Map<String, Object>) mappings.get("post")).get("operationId")).isEqualTo("upsertOtaChannelMapping");

        // 3. Mapping update
        Map<String, Object> mappingUpdate = (Map<String, Object>) paths.get("/ota-channel-mappings/{mappingId}");
        assertThat(mappingUpdate).containsKey("put");
        assertThat(((Map<String, Object>) mappingUpdate.get("put")).get("operationId")).isEqualTo("updateOtaChannelMapping");

        // 4. Price terms
        Map<String, Object> priceTerms = (Map<String, Object>) paths.get("/ota-channel-mappings/{mappingId}/price-terms");
        assertThat(priceTerms).containsKey("post");
        assertThat(((Map<String, Object>) priceTerms.get("post")).get("operationId")).isEqualTo("recordProviderConfirmedOtaPriceTerms");

        // 5. Sync
        Map<String, Object> sync = (Map<String, Object>) paths.get("/ota-channel-mappings/{mappingId}/sync");
        assertThat(sync).containsKey("post");
        assertThat(((Map<String, Object>) sync.get("post")).get("operationId")).isEqualTo("queueOtaMappingSync");

        // 6. Sales close
        Map<String, Object> salesClose = (Map<String, Object>) paths.get("/ota-channel-mappings/{mappingId}/sales-close");
        assertThat(salesClose).containsKey("post");
        assertThat(((Map<String, Object>) salesClose.get("post")).get("operationId")).isEqualTo("closeOtaMappingSales");

        // 7. Booking enable
        Map<String, Object> bookingEnable = (Map<String, Object>) paths.get("/ota-channel-mappings/{mappingId}/booking-enable");
        assertThat(bookingEnable).containsKey("post");
        assertThat(((Map<String, Object>) bookingEnable.get("post")).get("operationId")).isEqualTo("requestOtaBookingEnablement");

        // 8. Work items list
        Map<String, Object> workItems = (Map<String, Object>) paths.get("/ota-reconciliation-work-items");
        assertThat(workItems).containsKey("get");
        assertThat(((Map<String, Object>) workItems.get("get")).get("operationId")).isEqualTo("listOtaReconciliationWorkItems");

        // 9. Work item resolve
        Map<String, Object> resolve = (Map<String, Object>) paths.get("/ota-reconciliation-work-items/{workItemId}/resolve");
        assertThat(resolve).containsKey("post");
        assertThat(((Map<String, Object>) resolve.get("post")).get("operationId")).isEqualTo("resolveOtaReconciliationWorkItem");

        // 10. Financial evidence read
        Map<String, Object> finEvidence = (Map<String, Object>) paths.get("/ota-channel-bookings/{channelBookingId}/financial-evidence");
        assertThat(finEvidence).containsKey("get");
        assertThat(((Map<String, Object>) finEvidence.get("get")).get("operationId")).isEqualTo("getOtaFinancialEvidence");

        // 11. Payout reconciliation
        Map<String, Object> payoutRecon = (Map<String, Object>) paths.get("/ota-channel-bookings/{channelBookingId}/payout-reconciliation");
        assertThat(payoutRecon).containsKey("post");
        assertThat(((Map<String, Object>) payoutRecon.get("post")).get("operationId")).isEqualTo("reconcileOtaPayout");

        // 12. Addon quote creation
        Map<String, Object> addonQuote = (Map<String, Object>) paths.get("/ota-channel-bookings/{channelBookingId}/add-on-quotes");
        assertThat(addonQuote).containsKey("post");
        assertThat(((Map<String, Object>) addonQuote.get("post")).get("operationId")).isEqualTo("createOtaAddonQuote");

        // 13. Addon acceptance
        Map<String, Object> addonAccept = (Map<String, Object>) paths.get("/ota-addon-quotes/{quoteId}/acceptance");
        assertThat(addonAccept).containsKey("post");
        assertThat(((Map<String, Object>) addonAccept.get("post")).get("operationId")).isEqualTo("acceptOtaAddonQuote");

        // 14. Addon direct bill
        Map<String, Object> directBill = (Map<String, Object>) paths.get("/ota-addon-quotes/{quoteId}/direct-bill");
        assertThat(directBill).containsKey("post");
        assertThat(((Map<String, Object>) directBill.get("post")).get("operationId")).isEqualTo("createDirectMlandAddonBill");
    }

    // =========================================================================
    // 3. Controller Mapping Synchronization
    // =========================================================================

    @Test
    @DisplayName("T053-4: Verify all Spring REST controller mappings match OpenAPI paths exactly")
    void verifyBackendControllerMappingsMatchOpenApi() {
        Class<?>[] controllers = {
                OtaChannelController.class,
                OtaReconciliationController.class,
                OtaFinancialEvidenceController.class,
                OtaAddonController.class
        };

        Set<String> controllerPaths = new HashSet<>();
        for (Class<?> controller : controllers) {
            RequestMapping baseMapping = controller.getAnnotation(RequestMapping.class);
            String basePath = (baseMapping != null && baseMapping.value().length > 0) ? baseMapping.value()[0] : "";

            for (Method method : controller.getDeclaredMethods()) {
                String subPath = "";
                boolean hasMapping = false;
                if (method.isAnnotationPresent(GetMapping.class)) {
                    GetMapping a = method.getAnnotation(GetMapping.class);
                    subPath = a.value().length > 0 ? a.value()[0] : "";
                    hasMapping = true;
                } else if (method.isAnnotationPresent(PostMapping.class)) {
                    PostMapping a = method.getAnnotation(PostMapping.class);
                    subPath = a.value().length > 0 ? a.value()[0] : "";
                    hasMapping = true;
                } else if (method.isAnnotationPresent(PutMapping.class)) {
                    PutMapping a = method.getAnnotation(PutMapping.class);
                    subPath = a.value().length > 0 ? a.value()[0] : "";
                    hasMapping = true;
                } else if (method.isAnnotationPresent(PatchMapping.class)) {
                    PatchMapping a = method.getAnnotation(PatchMapping.class);
                    subPath = a.value().length > 0 ? a.value()[0] : "";
                    hasMapping = true;
                } else if (method.isAnnotationPresent(DeleteMapping.class)) {
                    DeleteMapping a = method.getAnnotation(DeleteMapping.class);
                    subPath = a.value().length > 0 ? a.value()[0] : "";
                    hasMapping = true;
                }

                if (hasMapping) {
                    String fullPath = basePath + subPath;
                    controllerPaths.add(fullPath);
                }
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> openApiPaths = (Map<String, Object>) openApiSpec.get("paths");

        // Every controller endpoint must start with /api/v1 and map to declared path in OpenAPI
        Set<String> matchedOpenApiPaths = new HashSet<>();
        for (String cPath : controllerPaths) {
            assertThat(cPath).as("Controller endpoint must be versioned under /api/v1: " + cPath)
                    .startsWith("/api/v1");
            String pathWithoutPrefix = cPath.substring("/api/v1".length());
            assertThat(openApiPaths).as("OpenAPI must declare controller endpoint: " + pathWithoutPrefix)
                    .containsKey(pathWithoutPrefix);
            matchedOpenApiPaths.add(pathWithoutPrefix);
        }

        // Every path in OpenAPI must be covered by a controller
        for (String oPath : openApiPaths.keySet()) {
            assertThat(matchedOpenApiPaths).as("Controller must implement OpenAPI path: " + oPath)
                    .contains(oPath);
        }
    }

    // =========================================================================
    // 4. Response Status Codes and Typed Errors Contract
    // =========================================================================

    @Test
    @DisplayName("T053-5: Verify response status codes and typed error references across all routes")
    @SuppressWarnings("unchecked")
    void verifyResponseStatusCodesAndTypedErrors() {
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Map<String, Object> operations = (Map<String, Object>) pathEntry.getValue();
            for (Map.Entry<String, Object> opEntry : operations.entrySet()) {
                if (!(opEntry.getValue() instanceof Map)) continue;
                Map<String, Object> op = (Map<String, Object>) opEntry.getValue();
                Map<String, Object> responses = (Map<String, Object>) op.get("responses");
                assertThat(responses).as("Operation responses must exist for " + opEntry.getKey() + " " + pathEntry.getKey())
                        .isNotNull();

                // Standard security responses required on all authenticated endpoints
                assertThat(responses).as("401 Unauthorized required on " + pathEntry.getKey())
                        .containsKey("401");
                assertThat(responses).as("403 Forbidden required on " + pathEntry.getKey())
                        .containsKey("403");

                // Has a success status code (200, 201, or 202)
                boolean hasSuccess = responses.containsKey("200") || responses.containsKey("201") || responses.containsKey("202");
                assertThat(hasSuccess).as("Success response required on " + pathEntry.getKey()).isTrue();
            }
        }

        // Verify ApiError schema and standard responses in components
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        assertThat(schemas).containsKey("ApiError");

        Map<String, Object> apiError = (Map<String, Object>) schemas.get("ApiError");
        List<String> requiredFields = (List<String>) apiError.get("required");
        assertThat(requiredFields).containsExactlyInAnyOrder("code", "message", "correlationId");

        Map<String, Object> responses = (Map<String, Object>) components.get("responses");
        assertThat(responses).containsKeys("ValidationError", "Unauthorized", "Forbidden", "NotFound", "Conflict", "Unprocessable");
    }

    // =========================================================================
    // 5. Schema Definitions and AdditionalProperties: false Contract
    // =========================================================================

    @Test
    @DisplayName("T053-6: Verify all schemas enforce additionalProperties: false to prevent data leakage")
    @SuppressWarnings("unchecked")
    void verifyAdditionalPropertiesFalseOnAllSchemas() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        String[] requiredSchemas = {
                "MappingUpsertRequest", "MappingUpdateRequest", "Money", "ZeroMoney",
                "PriceTermsRequest", "SyncRequest", "ReasonRequest", "BookingEnableRequest",
                "ReadinessCheckEvidence", "BookingEnableDecision", "ReconciliationResolutionRequest",
                "OperationAccepted", "ChannelMapping", "PriceTerms", "ChannelList",
                "ReconciliationWorkItem", "ReconciliationWorkItemList", "PayoutReconciliationRequest",
                "FinancialEvidence", "AddonAcceptanceRequest", "AddonQuote",
                "DirectBillLink", "ApiError"
        };

        for (String schemaName : requiredSchemas) {
            assertThat(schemas).as("OpenAPI must declare schema: " + schemaName).containsKey(schemaName);
            Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);

            if ("object".equals(schema.get("type"))) {
                assertThat(schema.get("additionalProperties"))
                        .as("Schema " + schemaName + " must enforce additionalProperties: false")
                        .isEqualTo(false);
            }
        }
    }

    // =========================================================================
    // 6. Enum Fidelity Across Domain and OpenAPI
    // =========================================================================

    @Test
    @DisplayName("T053-7: Verify domain enum fidelity in OpenAPI components")
    @SuppressWarnings("unchecked")
    void verifyEnumFidelity() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        // 1. ChannelMapping provider enum
        Map<String, Object> channelMapping = (Map<String, Object>) schemas.get("ChannelMapping");
        Map<String, Object> cmProps = (Map<String, Object>) channelMapping.get("properties");
        List<String> providerEnum = (List<String>) ((Map<String, Object>) cmProps.get("provider")).get("enum");
        assertThat(providerEnum).containsExactlyInAnyOrder("KLOOK", "GETYOURGUIDE");

        // 2. ChannelMapping state enum
        List<String> stateEnum = (List<String>) ((Map<String, Object>) cmProps.get("state")).get("enum");
        assertThat(stateEnum).containsExactlyInAnyOrder(
                "DRAFT", "PREPARATION_SYNC", "SALES_CLOSED", "BOOKING_ENABLED", "SYNC_SUSPENDED", "DISABLED");

        // 3. Commercial status enum
        List<String> commercialEnum = (List<String>) ((Map<String, Object>) cmProps.get("commercialStatus")).get("enum");
        assertThat(commercialEnum).containsExactlyInAnyOrder("PENDING_COMMERCIAL_CONFIRMATION", "APPROVED");

        // 4. Readiness checks (12 checks)
        Map<String, Object> readinessEvidence = (Map<String, Object>) schemas.get("ReadinessCheckEvidence");
        Map<String, Object> reProps = (Map<String, Object>) readinessEvidence.get("properties");
        List<String> checkEnum = (List<String>) ((Map<String, Object>) reProps.get("check")).get("enum");
        assertThat(checkEnum).containsExactlyInAnyOrder(
                "CONTRACT_ONBOARDING", "COMMERCIAL_TERMS", "CONNECTION_OWNERSHIP", "CALLBACK_AUTHENTICATION",
                "QUOTA_DELIVERY", "PRODUCT_SESSION_MAPPING", "DATA_ALLOWLIST", "PREPARATION_SYNC",
                "TICKET_DELIVERY_SCAN", "CANCELLATION_REFUND", "LATE_BOOKING_CAPACITY", "FAILURE_RECONCILIATION");

        // 5. Work item status enum
        Map<String, Object> workItem = (Map<String, Object>) schemas.get("ReconciliationWorkItem");
        Map<String, Object> wiProps = (Map<String, Object>) workItem.get("properties");
        List<String> wiStatusEnum = (List<String>) ((Map<String, Object>) wiProps.get("status")).get("enum");
        assertThat(wiStatusEnum).containsExactlyInAnyOrder("OPEN", "IN_PROGRESS", "RESOLVED", "ESCALATED");

        // 6. Resolution enum
        Map<String, Object> resolutionReq = (Map<String, Object>) schemas.get("ReconciliationResolutionRequest");
        Map<String, Object> rrProps = (Map<String, Object>) resolutionReq.get("properties");
        List<String> resEnum = (List<String>) ((Map<String, Object>) rrProps.get("resolution")).get("enum");
        assertThat(resEnum).containsExactlyInAnyOrder("RESYNC_REQUESTED", "ACCEPTED", "REJECTED", "ESCALATED");

        // 7. Customer payment state enum
        Map<String, Object> finEvidence = (Map<String, Object>) schemas.get("FinancialEvidence");
        Map<String, Object> feProps = (Map<String, Object>) finEvidence.get("properties");
        List<String> payStateEnum = (List<String>) ((Map<String, Object>) feProps.get("customerPaymentState")).get("enum");
        assertThat(payStateEnum).containsExactlyInAnyOrder("PAID_ON_CHANNEL", "REFUND_REQUESTED", "PARTIALLY_REFUNDED", "REFUNDED");

        // 8. Payout state enum
        List<String> payoutStateEnum = (List<String>) ((Map<String, Object>) feProps.get("payoutState")).get("enum");
        assertThat(payoutStateEnum).containsExactlyInAnyOrder("NOT_ELIGIBLE", "ACCRUED", "INVOICED", "PAYOUT_PENDING", "PAID_OUT");

        // 9. Addon type and status
        Map<String, Object> addonQuote = (Map<String, Object>) schemas.get("AddonQuote");
        Map<String, Object> aqProps = (Map<String, Object>) addonQuote.get("properties");
        List<String> addonTypeEnum = (List<String>) ((Map<String, Object>) aqProps.get("addonType")).get("enum");
        assertThat(addonTypeEnum).containsExactlyInAnyOrder("LASER_ENGRAVING", "PAID_OPTION");
        List<String> addonStatusEnum = (List<String>) ((Map<String, Object>) aqProps.get("status")).get("enum");
        assertThat(addonStatusEnum).containsExactlyInAnyOrder("RECORDED_FREE", "QUOTED", "ACCEPTED", "BILLED", "REJECTED");
    }

    // =========================================================================
    // 7. Privacy Non-Disclosure and Redaction
    // =========================================================================

    @Test
    @DisplayName("T053-8: Verify NO sensitive secrets, provider tokens, or customer PII are declared in schemas")
    @SuppressWarnings("unchecked")
    void verifyPrivacyAndNonDisclosureInSchemas() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        String[] forbiddenSensitiveFields = {
                "secret", "credential", "apiKey", "password", "token", "firebaseToken",
                "qrSecret", "rawQr", "creditCard", "customerPhone", "customerEmail",
                "customerName", "signatureKey"
        };

        for (Map.Entry<String, Object> schemaEntry : schemas.entrySet()) {
            if (!(schemaEntry.getValue() instanceof Map)) continue;
            Map<String, Object> schemaDef = (Map<String, Object>) schemaEntry.getValue();
            Map<String, Object> props = (Map<String, Object>) schemaDef.get("properties");
            if (props != null) {
                for (String sensitive : forbiddenSensitiveFields) {
                    assertThat(props).as("Schema " + schemaEntry.getKey() + " must not declare sensitive property: " + sensitive)
                            .doesNotContainKey(sensitive);
                }
            }
        }
    }

    // =========================================================================
    // 8. Parameter Contracts (UUID Format and Idempotency-Key Header)
    // =========================================================================

    @Test
    @DisplayName("T053-9: Verify parameter contracts for UUID formats and Idempotency-Key headers")
    @SuppressWarnings("unchecked")
    void verifyParameterContracts() {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        Map<String, Object> parameters = (Map<String, Object>) components.get("parameters");

        String[] uuidParameters = {"ChannelId", "MappingId", "WorkItemId", "ChannelBookingId", "QuoteId"};
        for (String paramName : uuidParameters) {
            assertThat(parameters).containsKey(paramName);
            Map<String, Object> param = (Map<String, Object>) parameters.get(paramName);
            assertThat(param.get("in")).isEqualTo("path");
            assertThat(param.get("required")).isEqualTo(true);
            Map<String, Object> schema = (Map<String, Object>) param.get("schema");
            assertThat(schema.get("type")).isEqualTo("string");
            assertThat(schema.get("format")).isEqualTo("uuid");
        }

        // IdempotencyKey header parameter
        assertThat(parameters).containsKey("IdempotencyKey");
        Map<String, Object> idempParam = (Map<String, Object>) parameters.get("IdempotencyKey");
        assertThat(idempParam.get("in")).isEqualTo("header");
        assertThat(idempParam.get("name")).isEqualTo("Idempotency-Key");
        assertThat(idempParam.get("required")).isEqualTo(true);
    }
}

