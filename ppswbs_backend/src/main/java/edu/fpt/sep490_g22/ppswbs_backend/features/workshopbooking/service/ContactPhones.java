package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

/**
 * One canonical form of a booking contact phone (003.1 FR-017a, T112): separators removed and the Vietnamese
 * country prefix written as a leading 0, so "0901 234 567", "+84 901 234 567" and "84901234567" are the same
 * contact. Never rejects a value; anything that is not a Vietnamese number keeps its digits (and a leading +).
 * Stored groups created before this rule are compared through the same method, so they still match.
 */
public final class ContactPhones {

    private ContactPhones() {
    }

    public static String canonical(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("[\\s\\-.()]+", "");
        if (cleaned.startsWith("+84")) {
            return "0" + cleaned.substring(3);
        }
        if (cleaned.startsWith("84") && cleaned.length() == 11) {
            return "0" + cleaned.substring(2);
        }
        return cleaned;
    }

    public static boolean sameContact(String a, String b) {
        return a != null && b != null && !canonical(a).isEmpty() && canonical(a).equals(canonical(b));
    }
}
