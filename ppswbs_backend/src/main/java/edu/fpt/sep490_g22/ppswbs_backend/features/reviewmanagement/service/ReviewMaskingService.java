package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ContactType;
import org.springframework.stereotype.Service;

@Service
public class ReviewMaskingService {

    /** Shown when the reviewer chose to be anonymous. */
    public static final String ANONYMOUS_DISPLAY_NAME = "Khách hàng ẩn danh";
    /** Shown for a Member whose account has no name. */
    public static final String MEMBER_ANONYMOUS_DISPLAY_NAME = "Hội viên ẩn danh";

    public String maskContact(ContactType contactType, String contactValue) {
        if (contactValue == null || contactValue.isBlank()) {
            return "Khách hàng ẩn danh";
        }
        if (contactType == ContactType.EMAIL) {
            return maskEmail(contactValue.trim());
        } else if (contactType == ContactType.PHONE) {
            return maskPhone(contactValue.trim());
        }
        return "Khách hàng ẩn danh";
    }

    public String maskMemberDisplayName(Long memberAccountId, String rawNameOrEmail) {
        if (rawNameOrEmail != null && !rawNameOrEmail.isBlank()) {
            if (rawNameOrEmail.contains("@")) {
                return maskEmail(rawNameOrEmail.trim());
            }
            return maskFullName(rawNameOrEmail.trim());
        }
        // No name on the account: stay anonymous. The internal account number is never shown, not even in part.
        return MEMBER_ANONYMOUS_DISPLAY_NAME;
    }

    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "k***@domain.com";
        }
        int atIdx = email.indexOf('@');
        String username = email.substring(0, atIdx);
        String domain = email.substring(atIdx);

        if (username.length() <= 2) {
            return username.charAt(0) + "***" + domain;
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1) + domain;
    }

    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) {
            return "090****000";
        }
        int len = phone.length();
        String prefix = phone.substring(0, Math.min(3, len - 3));
        String suffix = phone.substring(len - 3);
        return prefix + "****" + suffix;
    }

    private String maskFullName(String name) {
        String[] parts = name.split("\\s+");
        if (parts.length == 1) {
            String word = parts[0];
            if (word.length() <= 2) return word.charAt(0) + "***";
            return word.charAt(0) + "***" + word.charAt(word.length() - 1);
        }
        // Giữ họ và tên cuối, mask phần đệm
        String first = parts[0];
        String last = parts[parts.length - 1];
        return first.charAt(0) + "*** " + last;
    }
}

