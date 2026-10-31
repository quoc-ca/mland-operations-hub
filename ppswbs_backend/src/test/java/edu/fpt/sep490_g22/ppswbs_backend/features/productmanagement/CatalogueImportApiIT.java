package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ImportColumn;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T094 and the HTTP side of T078/T044 on MySQL: import (template, validate, read, confirm, error report),
 * categories and image upload through the real security chain. Firebase verification and the 001 actor lookup
 * are stubbed per role; the image provider is a stand-in (the real one is covered by CatalogueCloudinaryIT).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueImportApiIT {

    private static final String BASE = "/api/v1/catalogue-management";
    private static final Map<String, CurrentAccountActor> ACTORS = Map.of(
            "staff", new CurrentAccountActor(9201L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of()),
            "manager", new CurrentAccountActor(9202L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of()),
            "admin", new CurrentAccountActor(9203L, ActorRole.ADMIN, ActorStatus.ACTIVE, true, List.of()),
            "member", new CurrentAccountActor(9204L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of()));

    @MockitoBean private TokenVerificationService tokens;
    @MockitoBean private AccountFacade accounts;
    @MockitoBean private MediaStoragePort storage;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CatalogueFlowFixture fixture;

    @BeforeEach
    void stubs() {
        when(tokens.verify(anyString())).thenAnswer(call -> new VerifiedIdentity(call.getArgument(0), "x@example.test",
                true, Instant.now()));
        when(accounts.resolveActor(anyString(), anyBoolean()))
                .thenAnswer(call -> Optional.ofNullable(ACTORS.get((String) call.getArgument(0))));
        when(storage.storageKeyFor(any())).thenAnswer(call -> "it/" + call.getArgument(0));
        when(storage.putIfAbsent(anyString(), any())).thenAnswer(call ->
                new MediaStoragePort.StoredObject(call.getArgument(0), "asset", "1", "png"));
    }

    @Test
    void everyImportOperationIsRefusedToGuestMemberAndAdmin() throws Exception {
        mvc.perform(get(BASE + "/product-imports/template")).andExpect(status().isUnauthorized());
        for (String role : List.of("member", "admin")) {
            mvc.perform(as(role, get(BASE + "/product-imports/template"))).andExpect(status().isForbidden());
            mvc.perform(as(role, multipart(BASE + "/product-imports").file(xlsxPart(validRows())))
                            .header("Idempotency-Key", UUID.randomUUID()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(as("staff", get(BASE + "/product-imports/template")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void validateThenConfirmCreatesDraftsAndAnotherActorSeesNothing() throws Exception {
        JsonNode batch = json.readTree(mvc.perform(as("staff", multipart(BASE + "/product-imports")
                                .file(xlsxPart(validRows()))).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("VALIDATED"))
                .andExpect(jsonPath("$.preview.length()").value(1))
                .andReturn().getResponse().getContentAsString());
        String id = batch.get("batchId").asText();

        mvc.perform(as("manager", get(BASE + "/product-imports/" + id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMPORT_BATCH_NOT_FOUND"));
        mvc.perform(as("staff", post(BASE + "/product-imports/" + id + "/confirmations"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + batch.get("version").asLong() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IMPORTED"))
                .andExpect(jsonPath("$.created[0].skus.length()").value(2));
    }

    @Test
    void aFileWithRowErrorsIsARejectedBatchWithADownloadableReport() throws Exception {
        String category = fixture.category(fixture.activeCategory()).getName();
        JsonNode batch = json.readTree(mvc.perform(as("staff", multipart(BASE + "/product-imports")
                                .file(xlsxPart(new Object[][]{{"A", "Nhẫn", "Mô tả", category, 4, "1.5", 1}})))
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("REJECTED"))
                .andReturn().getResponse().getContentAsString());
        assertThat(batch.get("errorCount").asInt()).isGreaterThanOrEqualTo(2);

        mvc.perform(as("staff", get(BASE + "/product-imports/" + batch.get("batchId").asText() + "/error-report")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mvc.perform(as("staff", multipart(BASE + "/product-imports")
                                .file(new MockMultipartFile("file", "a.xls", "application/vnd.ms-excel", new byte[]{1, 2})))
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("IMPORT_FILE_TYPE_UNSUPPORTED"));
    }

    @Test
    void categoriesOverHttpForStaffAndManagerOnly() throws Exception {
        String name = "Danh mục API " + UUID.randomUUID();
        JsonNode created = json.readTree(mvc.perform(as("manager", post(BASE + "/categories"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        mvc.perform(as("staff", post(BASE + "/categories"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name.toUpperCase()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CATEGORY_NAME"));
        String id = created.get("resourceId").asText();
        mvc.perform(as("staff", get(BASE + "/categories/" + id + "/impact")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(as("staff", put(BASE + "/categories/" + id + "/activation"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + created.get("version").asLong() + ",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(as("admin", get(BASE + "/categories"))).andExpect(status().isForbidden());
        mvc.perform(as("member", get(BASE + "/categories"))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void imageUploadAcceptsOneVerifiedImageAndRefusesOtherTypes() throws Exception {
        mvc.perform(as("staff", multipart(BASE + "/media-uploads")
                                .file(new MockMultipartFile("file", "ring.png", "image/png", png())))
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("READY"))
                .andExpect(jsonPath("$.contentType").value("image/png"));
        mvc.perform(as("staff", multipart(BASE + "/media-uploads")
                                .file(new MockMultipartFile("file", "ring.gif", "image/gif", new byte[]{'G', 'I', 'F'})))
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MEDIA_TYPE_UNSUPPORTED"));
    }

    @Test
    void aPublicImageIsServedOnlyWhileItsProductIsPublic() throws Exception {
        long productId = fixture.publishedProduct("9201", "9202");
        String mediaId = fixture.images(productId).get(0).getMediaObjectId().toString();
        byte[] bytes = png();
        when(storage.open(any())).thenAnswer(call -> Optional.of(new java.io.ByteArrayInputStream(bytes)));

        mvc.perform(get("/api/v1/product-media/" + mediaId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"));
        mvc.perform(as("staff", put(BASE + "/products/" + productId + "/publication"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + fixture.productVersion(productId) + ",\"published\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/product-media/" + mediaId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    private Object[][] validRows() {
        String category = fixture.category(fixture.activeCategory()).getName();
        return new Object[][]{{"G1", "Nhẫn import API " + UUID.randomUUID(), "Mô tả", category, 6, "150000", 2},
                {"G1", null, "Mô tả", category, 8, "150000", 1}};
    }

    private static MockMultipartFile xlsxPart(Object[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sản phẩm");
            Row header = sheet.createRow(0);
            for (int i = 0; i < ImportColumn.ORDER.size(); i++) {
                header.createCell(i).setCellValue(ImportColumn.ORDER.get(i).header());
            }
            String sameName = rows.length > 0 ? (String) rows[0][1] : null;
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object value = c == 1 && rows[r][c] == null ? sameName : rows[r][c];
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else if (value != null) {
                        row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
            return new MockMultipartFile("file", "san-pham.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }
}
