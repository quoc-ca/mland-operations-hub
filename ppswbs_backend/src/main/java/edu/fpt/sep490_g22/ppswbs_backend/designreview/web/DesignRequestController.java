package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignAnalysisService;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        String callerUid = SecurityIdentityExtractor.requireUid(authentication);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Header Idempotency-Key là bắt buộc.");
        }

        DesignAnalysisService.DesignRequestViewDto response = designAnalysisService.submitDesignRequest(
                callerUid,
                description,
                catalogueReference,
                consentVersion,
                consentPurpose,
                designVersion,
                idempotencyKey,
                image
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
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
            Authentication authentication
    ) {
        String callerUid = (authentication != null && authentication.isAuthenticated())
                ? SecurityIdentityExtractor.extractUid(authentication).orElse(null)
                : null;
        Collection<? extends GrantedAuthority> authorities = authentication != null
                ? authentication.getAuthorities()
                : Collections.emptyList();

        DesignAnalysisService.DesignRequestViewDto view = designAnalysisService.getDesignRequestById(
                designRequestId,
                callerUid,
                authorities
        );

        return ResponseEntity.ok(view);
    }
}
