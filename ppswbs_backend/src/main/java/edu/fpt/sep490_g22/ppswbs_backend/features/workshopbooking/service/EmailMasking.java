package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import java.util.Locale;

/** Masks an email for audit and other people's view, e.g. {@code ng***@gmail.com} (approved email rules). */
public final class EmailMasking {

    private EmailMasking() {
    }

    public static String mask(String email) {
        if (email == null || email.isBlank()) return null;
        String trimmed = email.trim();
        int at = trimmed.lastIndexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) return "***";
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1).toLowerCase(Locale.ROOT);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible.toLowerCase(Locale.ROOT) + "***@" + domain;
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
