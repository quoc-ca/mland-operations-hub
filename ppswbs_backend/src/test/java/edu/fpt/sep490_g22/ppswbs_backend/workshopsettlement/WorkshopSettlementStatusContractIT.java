package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.InvalidTokenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenExpiredException;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundReceiptImageRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementRefundRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashReversalRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementAbsorbedUnderchargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashDayCloseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.RefundRequestStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementRefundRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt.SettlementReceiptException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt.SettlementReceiptStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 003.3 T161 (SC-016, CHK037) and T171 (FR-022d, CHK018): the status rules of the ticket settlement contract, run
 * through the real security chain and the real controllers.
 *
 * <ul>
 *   <li>Every operation that accepts {@code bearerAuth} declares {@code 401}; a missing, malformed or expired token
 *       answers {@code 401}; on guest-or-Member routes an invalid bearer is {@code 401} and no token takes the guest path.</li>
 *   <li>Every Manager-only operation answers a verified Staff caller {@code 403} {@code MANAGER_REQUIRED} and records no
 *       change (the authorization check comes before any lookup or write).</li>
 *   <li>The receipt-image routes answer {@code 413}, {@code 415} and {@code 503} as declared.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class WorkshopSettlementStatusContractIT {

    private static final Path CONTRACT = Path.of("..", "ppswbs-spec", "features", "003-workshop-management",
            "003.3-workshop-settlement-and-changes", "contracts", "workshop-settlement-ticket.openapi.yaml");
    private static final String IDEMPOTENCY_KEY = "contract-test-key-0001";
    private static final String STAFF_TOKEN = "staff-token";
    private static final String MANAGER_TOKEN = "manager-token";

    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired SettlementRefundRequestRepository refunds;
    @Autowired SettlementFinalBillRepository bills;
    @Autowired SettlementChargeRepository charges;
    @Autowired SettlementFolioCollectionRepository collections;
    @Autowired SettlementCashReversalRepository cashReversals;
    @Autowired SettlementAbsorbedUnderchargeRepository undercharges;
    @Autowired SettlementCashDayCloseRepository cashCloses;
    @Autowired SettlementRefundReceiptImageRepository receiptImages;
    @MockBean TokenVerificationService tokens;
    @MockBean SettlementReceiptStoragePort receiptStorage;

    private final List<String> failures = new ArrayList<>();

    @BeforeEach
    void identities() {
        String staffUid = "settlement-staff-" + UUID.randomUUID();
        String managerUid = "settlement-manager-" + UUID.randomUUID();
        accounts.save(Account.builder().firebaseUid(staffUid).loginEmail(staffUid + "@test.com")
                .role(AccountRole.STAFF).status(AccountStatus.ACTIVE).build());
        accounts.save(Account.builder().firebaseUid(managerUid).loginEmail(managerUid + "@test.com")
                .role(AccountRole.MANAGER).status(AccountStatus.ACTIVE).build());
        when(tokens.verify(STAFF_TOKEN)).thenReturn(new VerifiedIdentity(staffUid, staffUid + "@test.com", true, Instant.now()));
        when(tokens.verify(MANAGER_TOKEN)).thenReturn(new VerifiedIdentity(managerUid, managerUid + "@test.com", true, Instant.now()));
        when(tokens.verify("malformed")).thenThrow(new InvalidTokenException("malformed"));
        when(tokens.verify("expired")).thenThrow(new TokenExpiredException("expired"));
    }

    @Test
    @DisplayName("ST_TC_07: an Admin calling the change-request queue gets 403 STAFF_OR_MANAGER_REQUIRED")
    void adminCannotReadTheChangeRequestQueue() throws Exception {
        String uid = "settlement-admin-" + UUID.randomUUID();
        accounts.save(Account.builder().firebaseUid(uid).loginEmail(uid + "@test.com")
                .role(AccountRole.ADMIN).status(AccountStatus.ACTIVE).build());
        when(tokens.verify("admin-token")).thenReturn(new VerifiedIdentity(uid, uid + "@test.com", true, Instant.now()));

        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/v1/workshop-change-requests")
                .header("Authorization", "Bearer admin-token")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).contains("STAFF_OR_MANAGER_REQUIRED");
    }

    // ---- T171: the declared 401 and the real 401 ----

    @Test
    @DisplayName("T171: every operation that accepts bearerAuth declares 401 (also the correction read)")
    void everyBearerOperationDeclares401() throws IOException {
        List<Operation> operations = operations();
        assertThat(operations).hasSizeGreaterThan(30);
        for (Operation op : operations) {
            if (op.acceptsBearer() && !op.responses().containsKey("401")) {
                failures.add(op.label() + " accepts bearerAuth but declares no 401");
            }
        }
        assertThat(operations).anyMatch(op -> op.label().equals(
                "GET /api/v1/workshop-tickets/{ticketCode}/folio/corrections/{correctionReference}") && op.responses().containsKey("401"));
        assertThat(failures).isEmpty();
    }

    @Test
    @DisplayName("T171: a missing, malformed or expired token answers 401 on every bearer-only route")
    void bearerOnlyRoutesAnswer401() throws Exception {
        int checked = 0;
        for (Operation op : operations()) {
            if (!op.bearerOnly()) continue;
            checked++;
            expect401(op, null, "MISSING_BEARER_TOKEN");
            expect401(op, "malformed", "INVALID_TOKEN");
            expect401(op, "expired", "TOKEN_EXPIRED");
        }
        assertThat(checked).isGreaterThan(25);
        assertThat(failures).isEmpty();
    }

    @Test
    @DisplayName("T171: on guest-or-Member routes an invalid bearer is 401 and no token takes the guest path")
    void guestOrMemberRoutesRejectAnInvalidBearerButAcceptNoToken() throws Exception {
        int checked = 0;
        for (Operation op : operations()) {
            if (!op.guestOrBearer()) continue;
            checked++;
            expect401(op, "malformed", "INVALID_TOKEN");
            MvcResult guest = mvc.perform(op.request(null)).andReturn();
            if (guest.getResponse().getStatus() == 401) {
                failures.add(op.label() + " answered 401 to a request without a token (guest path expected)");
            }
        }
        assertThat(checked).isGreaterThanOrEqualTo(3);
        assertThat(failures).isEmpty();
    }

    // ---- T161: Manager-only operations refuse Staff ----

    @Test
    @DisplayName("T161: every Manager-only operation answers a Staff caller 403 MANAGER_REQUIRED and records no change")
    void managerOnlyOperationsRefuseStaffAndChangeNothing() throws Exception {
        long before = rowCount();
        String reason = "{\"reason\":\"Contract test\"}";
        List<MockHttpServletRequestBuilder> managerOnly = List.of(
                post("/api/v1/workshop-tickets/T-1/folio/lines/L-1/voids", reason),
                post("/api/v1/workshop-tickets/T-1/folio/collections/C-1/voids", reason),
                post("/api/v1/workshop-tickets/T-1/folio/collections/C-1/failures", reason),
                post("/api/v1/workshop-tickets/T-1/folio/collections/C-1/confirmations",
                        "{\"bankTransactionReference\":\"FT-1\"}"),
                post("/api/v1/workshop-tickets/T-1/folio/handover-exceptions", reason),
                post("/api/v1/workshop-tickets/T-1/folio/debts", "{\"amountVnd\":1000,\"reason\":\"Contract test\"}"),
                post("/api/v1/workshop-tickets/T-1/folio/debts/closures", reason),
                post("/api/v1/workshop-tickets/T-1/folio/collections/C-1/cash-reversals", reason),
                post("/api/v1/workshop-tickets/T-1/folio/absorbed-undercharges",
                        "{\"amountVnd\":\"1000\",\"reason\":\"Contract test\"}"),
                post("/api/v1/workshop-tickets/T-1/folio/absorbed-undercharges/U-1/voids", reason),
                post("/api/v1/workshop-cash-closes/CLOSE-1/difference-confirmations", reason),
                MockMvcRequestBuilders.get("/api/v1/workshop-reconciliation-collections")
                        .param("locationId", "1").param("workingDate", "2026-11-16"),
                MockMvcRequestBuilders.get("/api/v1/workshop-absorbed-undercharges")
                        .param("locationId", "1").param("from", "2026-11-01").param("to", "2026-11-16"),
                MockMvcRequestBuilders.get("/api/v1/workshop-refund-requests").param("waiting", "true"),
                post("/api/v1/workshop-refund-requests/R-1/transitions", "{\"action\":\"APPROVE\",\"reason\":\"x\"}"),
                MockMvcRequestBuilders.get("/api/v1/workshop-refund-requests/R-1/account-number"),
                MockMvcRequestBuilders.get("/api/v1/workshop-refund-requests/R-1/receipt-image"),
                MockMvcRequestBuilders.multipart("/api/v1/workshop-refund-requests/R-1/receipt-images")
                        .file(new MockMultipartFile("file", "r.png", "image/png", new byte[]{1}))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY));
        for (MockHttpServletRequestBuilder request : managerOnly) {
            MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + STAFF_TOKEN)
                    .header("Idempotency-Key", IDEMPOTENCY_KEY)).andReturn();
            String label = result.getRequest().getMethod() + " " + result.getRequest().getRequestURI();
            if (result.getResponse().getStatus() != 403
                    || !result.getResponse().getContentAsString().contains("MANAGER_REQUIRED")) {
                failures.add(label + " answered " + result.getResponse().getStatus() + " "
                        + result.getResponse().getContentAsString());
            }
        }
        assertThat(failures).isEmpty();
        assertThat(rowCount()).as("no business row is written by a refused Staff caller").isEqualTo(before);
    }

    // ---- T171: the receipt image routes answer 413, 415, 503 as declared ----

    @Test
    @DisplayName("T171: the receipt-image routes answer 413, 415 and 503 as declared, and Staff 403")
    void receiptImageRoutesAnswer413And415And503() throws Exception {
        Operation upload = operations().stream().filter(op -> op.label()
                .equals("POST /api/v1/workshop-refund-requests/{refundId}/receipt-images")).findFirst().orElseThrow();
        assertThat(upload.responses()).containsKeys("401", "403", "404", "409", "413", "415", "503");
        assertThat(upload.responses().get("201")).isNotNull();

        String reference = "REFUND-CONTRACT-" + UUID.randomUUID();
        refunds.save(SettlementRefundRequest.builder().requestReference(reference).bookingReference("T-CONTRACT")
                .groupReference("G-CONTRACT").reason(RefundReason.EXCESS_OR_DUPLICATE_MONEY)
                .sourceReference("src-" + reference).amountVnd(50_000L).status(RefundRequestStatus.PENDING_TRANSFER).build());
        String route = "/api/v1/workshop-refund-requests/" + reference + "/receipt-images";

        // 415: a PDF, and a declared type that does not match the bytes.
        mvc.perform(MockMvcRequestBuilders.multipart(route)
                        .file(new MockMultipartFile("file", "receipt.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .header("Authorization", "Bearer " + MANAGER_TOKEN).header("Idempotency-Key", "contract-key-pdf-0001"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("RECEIPT_IMAGE_TYPE_UNSUPPORTED"));
        // 413: 5,000,001 bytes.
        mvc.perform(MockMvcRequestBuilders.multipart(route)
                        .file(new MockMultipartFile("file", "big.png", "image/png", pngPaddedTo(5_000_001)))
                        .header("Authorization", "Bearer " + MANAGER_TOKEN).header("Idempotency-Key", "contract-key-big-0001"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("RECEIPT_IMAGE_TOO_LARGE"));
        // 503: storage cannot take the object; nothing is recorded and the provider detail is not returned.
        when(receiptStorage.storageKeyFor(any())).thenReturn("ppswbs/settlement-receipts/test");
        when(receiptStorage.put(anyString(), anyString(), any()))
                .thenThrow(new SettlementReceiptException(SettlementReceiptException.Failure.STORAGE_UNAVAILABLE));
        long rowsBefore = receiptImages.count();
        MvcResult unavailable = mvc.perform(MockMvcRequestBuilders.multipart(route)
                        .file(new MockMultipartFile("file", "ok.png", "image/png", pngPaddedTo(2_000)))
                        .header("Authorization", "Bearer " + MANAGER_TOKEN).header("Idempotency-Key", "contract-key-503-0001"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RECEIPT_STORAGE_UNAVAILABLE")).andReturn();
        assertThat(unavailable.getResponse().getContentAsString()).doesNotContain("cloudinary").doesNotContain("http");
        assertThat(receiptImages.count()).isEqualTo(rowsBefore);
        // 409: outside PENDING_TRANSFER.
        SettlementRefundRequest request = refunds.findByRequestReference(reference).orElseThrow();
        request.setStatus(RefundRequestStatus.PENDING_RECONCILIATION);
        refunds.save(request);
        mvc.perform(MockMvcRequestBuilders.multipart(route)
                        .file(new MockMultipartFile("file", "ok.png", "image/png", pngPaddedTo(2_000)))
                        .header("Authorization", "Bearer " + MANAGER_TOKEN).header("Idempotency-Key", "contract-key-409-0001"))
                .andExpect(status().isConflict());
        // 404 on the stream route while no image is attached.
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/workshop-refund-requests/" + reference + "/receipt-image")
                        .header("Authorization", "Bearer " + MANAGER_TOKEN))
                .andExpect(status().isNotFound());
    }

    // ---- helpers ----

    private long rowCount() {
        return bills.count() + charges.count() + collections.count() + refunds.count() + cashReversals.count()
                + undercharges.count() + cashCloses.count() + receiptImages.count();
    }

    private void expect401(Operation op, String token, String expectedCode) throws Exception {
        MvcResult result = mvc.perform(op.request(token)).andReturn();
        if (result.getResponse().getStatus() != 401 || !result.getResponse().getContentAsString().contains(expectedCode)) {
            failures.add(op.label() + " with token=" + token + " answered " + result.getResponse().getStatus() + " "
                    + result.getResponse().getContentAsString());
        }
    }

    private static MockHttpServletRequestBuilder post(String path, String json) {
        return MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static byte[] pngPaddedTo(int length) throws IOException {
        java.awt.image.BufferedImage picture = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(picture, "png", out);
        return java.util.Arrays.copyOf(out.toByteArray(), length);
    }

    @SuppressWarnings("unchecked")
    private static List<Operation> operations() throws IOException {
        assertThat(CONTRACT).as("the 003.3 ticket settlement contract").exists();
        Map<String, Object> document;
        try (InputStream in = Files.newInputStream(CONTRACT)) {
            document = new Yaml().load(in);
        }
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) document.get("paths");
        List<Operation> operations = new ArrayList<>();
        paths.forEach((path, item) -> item.forEach((method, definition) -> {
            if (!List.of("get", "post", "put", "patch", "delete").contains(method)) return;
            Map<String, Object> op = (Map<String, Object>) definition;
            operations.add(new Operation(method.toUpperCase(), path, (List<Map<String, Object>>) op.get("security"),
                    (Map<String, Object>) op.get("responses")));
        }));
        return operations;
    }

    private record Operation(String method, String path, List<Map<String, Object>> security,
                             Map<String, Object> rawResponses) {

        String label() {
            return method + " " + path;
        }

        Map<String, Object> responses() {
            Map<String, Object> normalised = new java.util.LinkedHashMap<>();
            rawResponses.forEach((code, value) -> normalised.put(String.valueOf(code), value));
            return normalised;
        }

        boolean acceptsBearer() {
            return security != null && security.stream().anyMatch(entry -> entry.containsKey("bearerAuth"));
        }

        boolean guestOrBearer() {
            return acceptsBearer() && security.stream().anyMatch(Map::isEmpty);
        }

        boolean bearerOnly() {
            return acceptsBearer() && !guestOrBearer();
        }

        MockHttpServletRequestBuilder request(String token) {
            String url = path.replaceAll("\\{[^}]+}", "X-1");
            MockHttpServletRequestBuilder builder = switch (method) {
                case "GET" -> MockMvcRequestBuilders.get(url);
                case "POST" -> MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content("{}");
                case "PUT" -> MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content("{}");
                case "PATCH" -> MockMvcRequestBuilders.patch(url).contentType(MediaType.APPLICATION_JSON).content("{}");
                default -> MockMvcRequestBuilders.delete(url);
            };
            builder.header("Idempotency-Key", IDEMPOTENCY_KEY);
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            return builder;
        }
    }
}
