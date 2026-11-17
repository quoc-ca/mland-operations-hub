package edu.fpt.sep490_g22.ppswbs_backend.designreview.application;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public final class DesignReviewPermissions {

    private DesignReviewPermissions() {}

    public static boolean isManager(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) return false;
        return authorities.stream().anyMatch(a ->
                "ROLE_MANAGER".equals(a.getAuthority()) || "ROLE_OWNER".equals(a.getAuthority()));
    }

    public static boolean isStaff(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) return false;
        return authorities.stream().anyMatch(a -> "ROLE_STAFF".equals(a.getAuthority()));
    }

    public static boolean isAdmin(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) return false;
        return authorities.stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_ADMIN_TECHNICAL".equals(a.getAuthority()));
    }

    public static boolean isInternalReviewer(Collection<? extends GrantedAuthority> authorities) {
        return isManager(authorities) || isStaff(authorities);
    }

    public static boolean canViewDesignRequest(Collection<? extends GrantedAuthority> authorities) {
        return isManager(authorities) || isStaff(authorities) || isAdmin(authorities);
    }

    public static boolean canViewPriceSuggestion(Collection<? extends GrantedAuthority> authorities) {
        return isManager(authorities) || isAdmin(authorities);
    }

    public static boolean canDecidePriceSuggestion(Collection<? extends GrantedAuthority> authorities) {
        // Invariant: Only Manager has commercial pricing authority; Admin has technical/audit visibility only
        return isManager(authorities);
    }
}
