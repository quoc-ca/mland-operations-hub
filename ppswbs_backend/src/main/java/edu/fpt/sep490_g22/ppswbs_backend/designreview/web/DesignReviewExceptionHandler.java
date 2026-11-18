package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "edu.fpt.sep490_g22.ppswbs_backend.designreview.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DesignReviewExceptionHandler {

    @ExceptionHandler(DesignReviewException.class)
    public ResponseEntity<DesignReviewDtos.ErrorResponse> handleDesignReviewException(DesignReviewException ex) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getRetryAfterSeconds() != null && ex.getRetryAfterSeconds() > 0) {
            headers.add("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        }

        if (ex.getQuota() != null) {
            headers.add("X-AI-Quota-Limit", String.valueOf(ex.getQuota().getLimit()));
            headers.add("X-AI-Quota-Remaining", String.valueOf(ex.getQuota().getRemaining()));
            headers.add("X-AI-Quota-Reset-At", ex.getQuota().getResetAt());
        }

        DesignReviewDtos.ErrorResponse body = DesignReviewDtos.ErrorResponse.builder()
                .code(ex.getCode())
                .message(ex.getMessage())
                .retryAfterSeconds(ex.getRetryAfterSeconds())
                .correlationId(ex.getCorrelationId())
                .quota(ex.getQuota())
                .build();

        return new ResponseEntity<>(body, headers, ex.getStatus());
    }
}
