package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One canonical form of a booking contact phone (003.1 FR-017a, FR-030, T112): separators removed and the Vietnamese
 * country prefix written as a leading 0, so "0901 234 567", "+84 901 234 567" and "84901234567" are the same
 * contact. {@link #canonical} never rejects a value; anything that is not a Vietnamese number keeps its digits (and a
 * leading +). Stored groups created before this rule are compared through the same method, so they still match.
 *
 * <p>{@link #isValidInternational} is the create-booking rule (FR-030): the frontend sends
 * {@code +<country code><national number>} for the supported countries only; lookup, hold release and verification
 * stay tolerant and use {@link #sameContact}.
 */
public final class ContactPhones {

    public static final int MAX_LENGTH = 32;

    /** Supported country calling codes and the national-number rule (digits after the code), as the frontend uses. */
    private static final Map<String, Pattern> NATIONAL_RULES = new LinkedHashMap<>();

    static {
        NATIONAL_RULES.put("84", Pattern.compile("[35789]\\d{8}"));   // VN
        NATIONAL_RULES.put("1", Pattern.compile("[2-9]\\d{9}"));      // US / CA
        NATIONAL_RULES.put("81", Pattern.compile("[1-9]\\d{8,9}"));   // JP
        NATIONAL_RULES.put("82", Pattern.compile("[1-9]\\d{7,9}"));   // KR
        NATIONAL_RULES.put("86", Pattern.compile("1[3-9]\\d{9}"));    // CN
        NATIONAL_RULES.put("65", Pattern.compile("[3689]\\d{7}"));    // SG
        NATIONAL_RULES.put("66", Pattern.compile("[1-9]\\d{7,8}"));   // TH
        NATIONAL_RULES.put("61", Pattern.compile("[2-478]\\d{8}"));   // AU
        NATIONAL_RULES.put("44", Pattern.compile("[1-9]\\d{9}"));     // GB
        NATIONAL_RULES.put("33", Pattern.compile("[1-9]\\d{8}"));     // FR
        NATIONAL_RULES.put("49", Pattern.compile("[1-9]\\d{9,10}"));  // DE
    }

    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-.()]+");

    private ContactPhones() {
    }

    /** True for {@code +<supported country code><national number matching that country's rule>}, at most 32 characters. */
    public static boolean isValidInternational(String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        if (value.length() > MAX_LENGTH || value.length() < 2 || value.charAt(0) != '+') return false;
        String digits = value.substring(1);
        for (Map.Entry<String, Pattern> rule : NATIONAL_RULES.entrySet()) {
            String code = rule.getKey();
            if (digits.startsWith(code) && rule.getValue().matcher(digits.substring(code.length())).matches()) {
                return true;
            }
        }
        return false;
    }

    public static String canonical(String raw) {
        if (raw == null) return null;
        String cleaned = SEPARATORS.matcher(raw).replaceAll("");
        if (cleaned.startsWith("+84")) {
            String rest = cleaned.substring(3);
            // "+84 0338..." (trunk 0 typed after the code) is the same number as "+84 338...".
            return rest.startsWith("0") ? rest : "0" + rest;
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
