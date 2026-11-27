package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import java.util.List;

/** One email ready to send. {@code images} are the pictures the HTML refers to as {@code cid:<contentId>}. */
record MailMessage(String to, String subject, String text, String html, List<InlineImage> images) {

    record InlineImage(String contentId, byte[] png) { }

    MailMessage(String to, String subject, String text, String html) {
        this(to, subject, text, html, List.of());
    }
}
