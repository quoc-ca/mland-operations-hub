package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ContactType;
import org.springframework.stereotype.Service;

@Service
public class ReviewMaskingService {

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
        if (memberAccountId != null) {
            String idStr = String.valueOf(memberAccountId);
            if (idStr.length() > 3) {
                return "Hội viên #" + idStr.substring(0, 1) + "***" + idStr.substring(idStr.length() - 2);
            }
            return "Hội viên #" + idStr;
        }
        return "Hội viên ẩn danh";
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

