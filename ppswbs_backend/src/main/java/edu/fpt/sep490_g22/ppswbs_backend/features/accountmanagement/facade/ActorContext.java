package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class ActorContext {

    private ActorContext() {}

    public static Optional<CurrentAccountActor> getCurrentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentAccountActor actor) {
            return Optional.of(actor);
        }
        return Optional.empty();
    }

    public static CurrentAccountActor requireCurrentActor() {
        return getCurrentActor()
            .orElseThrow(() -> new IllegalStateException("No CurrentAccountActor present in request security context"));
    }

    public static Optional<VerifiedIdentity> getVerifiedIdentity() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof VerifiedIdentity vi) {
            return Optional.of(vi);
        }
        if (auth != null && auth.getDetails() instanceof edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity vi) {
            return Optional.of(vi.toFacade());
        }
        org.springframework.web.context.request.RequestAttributes attrs =
            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
            Object obj = servletAttrs.getRequest().getAttribute("verifiedIdentity");
            if (obj instanceof VerifiedIdentity vi) {
                return Optional.of(vi);
            }
            if (obj instanceof edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity vi) {
                return Optional.of(vi.toFacade());
            }
        }
        return Optional.empty();
    }

    public static Optional<String> getVerifiedUid() {
        return getVerifiedIdentity().map(VerifiedIdentity::uid);
    }
}
