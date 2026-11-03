package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.CatalogueRequests;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ProductImportDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueAuthorization;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueAuthorization.Capability;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueMediaService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ImportTemplateWriter;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing.ProductImportService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.created;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.ok;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller.CatalogueResponses.version;

/**
 * File endpoints: image upload (T044) and Excel import (T099). One file per request in the part {@code file};
 * the services check type, signature and size. Import uses {@code product.create}; a batch of another actor is
 * {@code IMPORT_BATCH_NOT_FOUND}. Downloads are {@code no-store} attachments.
 */
@RestController
@RequestMapping("/api/v1/catalogue-management")
class ManagementFileApiController {

    private static final String KEY = "Idempotency-Key";
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CatalogueAuthorization authorization;
    private final CatalogueMediaService media;
    private final ProductImportService imports;
    private final ProductCategoryRepository categories;
    private final TransactionTemplate transaction;

    ManagementFileApiController(CatalogueAuthorization authorization, CatalogueMediaService media,
                                ProductImportService imports, ProductCategoryRepository categories,
                                TransactionTemplate transaction) {
        this.authorization = authorization;
        this.media = media;
        this.imports = imports;
        this.categories = categories;
        this.transaction = transaction;
    }

    @PostMapping(path = "/media-uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<CatalogueMediaService.UploadResult> upload(@RequestHeader(KEY) UUID key,
                                                              @RequestPart("file") MultipartFile file) {
        String actor = authorization.require(Capability.MEDIA_MANAGE);
        return created(media.upload(actor, key, file.getContentType(), bytes(file, CatalogueErrorCode.VALIDATION_ERROR)));
    }

    @GetMapping("/product-imports/template")
    ResponseEntity<byte[]> template() {
        authorization.require(Capability.PRODUCT_CREATE);
        byte[] file = transaction.execute(status -> ImportTemplateWriter.write(categories.findByActiveTrueOrderByNameAscIdAsc()));
        return attachment(file, "mau-import-san-pham.xlsx");
    }

    @PostMapping(path = "/product-imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ProductImportDtos.ImportBatch> validate(@RequestHeader(KEY) UUID key,
                                                           @RequestPart("file") MultipartFile file) {
        String actor = authorization.require(Capability.PRODUCT_CREATE);
        String batchId = imports.validate(actor, key, file.getOriginalFilename(),
                bytes(file, CatalogueErrorCode.IMPORT_FILE_INVALID)).resourceId();
        return created(imports.get(actor, batchId));
    }

    @GetMapping("/product-imports/{batchId}")
    ResponseEntity<ProductImportDtos.ImportBatch> get(@PathVariable String batchId) {
        String actor = authorization.require(Capability.PRODUCT_CREATE);
        return ok(imports.get(actor, batchId));
    }

    @PostMapping("/product-imports/{batchId}/confirmations")
    ResponseEntity<ProductImportDtos.ImportBatch> confirm(@PathVariable String batchId, @RequestHeader(KEY) UUID key,
                                                          @RequestBody CatalogueRequests.VersionRequest body) {
        String actor = authorization.require(Capability.PRODUCT_CREATE);
        imports.confirm(actor, key, batchId, version(body.expectedVersion()));
        return ok(imports.get(actor, batchId));
    }

    @GetMapping("/product-imports/{batchId}/error-report")
    ResponseEntity<byte[]> errorReport(@PathVariable String batchId) {
        String actor = authorization.require(Capability.PRODUCT_CREATE);
        return attachment(imports.errorReport(actor, batchId), "bao-cao-loi-import.xlsx");
    }

    private static byte[] bytes(MultipartFile file, CatalogueErrorCode unreadable) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new CatalogueException(unreadable);
        }
    }

    private static ResponseEntity<byte[]> attachment(byte[] content, String fileName) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(content);
    }
}
