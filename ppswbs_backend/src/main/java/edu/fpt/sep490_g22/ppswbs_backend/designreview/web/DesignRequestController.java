package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignAnalysisService;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/design-requests")
@RequiredArgsConstructor
@Slf4j
public class DesignRequestController {

    private final DesignAnalysisService designAnalysisService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> submitDesignRequest(
            @RequestParam("bookingCode") String bookingCode,
            @RequestParam("consentVersion") String consentVersion,
            @RequestParam(value = "designVersion", defaultValue = "1") Integer designVersion,
            @RequestPart("image") MultipartFile image,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Yêu cầu xác thực tài khoản Firebase ID Token.");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw DesignReviewException.badRequest("AI_INPUT_INVALID", "Header Idempotency-Key là bắt buộc.");
        }

        String callerUid = authentication.getPrincipal().toString();
        DesignAnalysisService.DesignRequestViewDto response = designAnalysisService.submitDesignRequest(
                callerUid,
                bookingCode,
                consentVersion,
                designVersion,
                idempotencyKey,
                image
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{designRequestId}")
    public ResponseEntity<DesignAnalysisService.DesignRequestViewDto> getDesignRequest(
            @PathVariable("designRequestId") String designRequestId,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Yêu cầu xác thực tài khoản.");
        }

        String callerUid = authentication.getPrincipal().toString();
        DesignAnalysisService.DesignRequestViewDto view = designAnalysisService.getDesignRequestById(
                designRequestId,
                callerUid,
                authentication.getAuthorities()
        );

        return ResponseEntity.ok(view);
    }
}
