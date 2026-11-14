package edu.fpt.sep490_g22.ppswbs_backend.designreview.web;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DesignReviewException;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.VerifiedIdentity;
import org.springframework.security.core.Authentication;

import java.util.Optional;

public final class SecurityIdentityExtractor {

    private SecurityIdentityExtractor() {}

    public static String requireUid(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Yêu cầu xác thực tài khoản.");
        }

        return ActorContext.getVerifiedUid()
                .or(() -> {
                    if (authentication.getDetails() instanceof VerifiedIdentity vi) {
                        return Optional.ofNullable(vi.uid());
                    }
                    return Optional.empty();
                })
                .or(() -> {
                    Object principal = authentication.getPrincipal();
                    if (principal instanceof String str && !str.isBlank() && !str.startsWith("CurrentAccountActor[")) {
                        return Optional.of(str);
                    }
                    return Optional.empty();
                })
                .orElseThrow(() -> DesignReviewException.unauthorized("AUTHENTICATION_REQUIRED", "Không tìm thấy danh tính Firebase UID hợp lệ."));
    }
}
