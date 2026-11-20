package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignAnalysisService;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequestImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/design-requests")
@RequiredArgsConstructor
@Slf4j
public class DesignRequestController {

    private final DesignAnalysisService designAnalysisService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> submitDesignRequest(
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "catalogueReference", required = false) String catalogueReference,
            @RequestParam(value = "consentVersion", required = false) String consentVersion,
            @RequestParam(value = "consentPurpose", required = false) String consentPurpose,
            @RequestParam(value = "designVersion", defaultValue = "1") Integer designVersion,
            @RequestPart(value = "image", required = false) MultipartFile singleImage,
            @RequestPart(value = "images", required = false) List<MultipartFile> multipleImages,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Guest-Design-Session", required = false) String guestSessionId,
            Authentication authentication
    ) {
        String callerUid = (authentication != null && authentication.isAuthenticated())
                ? SecurityIdentityExtractor.extractUid(authentication).orElse(null)
                : null;

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw DesignReviewException.badRequest("DESIGN_REQUEST_INVALID", "Header Idempotency-Key là bắt buộc.");
        }

        List<MultipartFile> allFiles = new ArrayList<>();
        if (multipleImages != null && !multipleImages.isEmpty()) {
            allFiles.addAll(multipleImages);
        }
        if (singleImage != null && !singleImage.isEmpty() && !allFiles.contains(singleImage)) {
            allFiles.add(singleImage);
        }

        DesignAnalysisService.DesignRequestViewDto response = designAnalysisService.submitDesignRequest(
                callerUid,
                guestSessionId,
                description,
                catalogueReference,
                consentVersion,
                consentPurpose,
                designVersion,
                idempotencyKey,
                allFiles
        );

        HttpHeaders headers = new HttpHeaders();
        if (response.getGuestTrackingToken() != null) {
            headers.add("X-Guest-Tracking-Token", response.getGuestTrackingToken());
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).headers(headers).body(response);
    }

    @GetMapping
    public ResponseEntity<List<DesignAnalysisService.DesignRequestViewDto>> getMyDesignRequests(
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);
        List<DesignAnalysisService.DesignRequestViewDto> list = designAnalysisService.getMyDesignRequests(callerUid);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{designRequestId}")
    public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> getDesignRequest(
            @PathVariable("designRequestId") String designRequestId,
            @RequestHeader(value = "X-Guest-Tracking-Token", required = false) String trackingTokenHeader,
            @RequestParam(value = "token", required = false) String trackingTokenParam,
            Authentication authentication
    ) {
        String callerUid = (authentication != null && authentication.isAuthenticated())
                ? SecurityIdentityExtractor.extractUid(authentication).orElse(null)
                : null;
        Collection<? extends GrantedAuthority> authorities = authentication != null
                ? authentication.getAuthorities()
                : Collections.emptyList();

        String token = (trackingTokenHeader != null && !trackingTokenHeader.isBlank())
                ? trackingTokenHeader
                : trackingTokenParam;

        DesignAnalysisService.DesignRequestViewDto view = designAnalysisService.getDesignRequestById(
                designRequestId,
                callerUid,
                authorities,
                token
        );

        return ResponseEntity.ok(view);
    }

    @GetMapping("/{designRequestId}/tracking")
    public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> getGuestTracking(
            @PathVariable("designRequestId") String designRequestId,
            @RequestHeader(value = "X-Guest-Tracking-Token", required = false) String trackingTokenHeader,
            @RequestParam(value = "token", required = false) String trackingTokenParam,
            Authentication authentication
    ) {
        return getDesignRequest(designRequestId, trackingTokenHeader, trackingTokenParam, authentication);
    }

    @GetMapping("/images/{imageReference}")
    public ResponseEntity<byte[]> getImage(
            @PathVariable("imageReference") String imageReference
    ) {
        DesignRequestImage imageEntity = designAnalysisService.getImageEntity(imageReference);
        byte[] bytes = designAnalysisService.getImageBytes(imageReference);

        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imageEntity.getContentType()))
                .contentLength(bytes.length)
                .body(bytes);
    }
}
