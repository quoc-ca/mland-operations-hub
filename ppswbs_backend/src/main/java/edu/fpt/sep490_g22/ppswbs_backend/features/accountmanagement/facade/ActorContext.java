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
}
