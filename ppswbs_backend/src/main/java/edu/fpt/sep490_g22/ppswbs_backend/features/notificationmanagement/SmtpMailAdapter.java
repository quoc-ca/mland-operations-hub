package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Real delivery ({@code app.mail.adapter=smtp}) through the configured SMTP account. */
@Component
@ConditionalOnProperty(name = "app.mail.adapter", havingValue = "smtp")
class SmtpMailAdapter implements MailAdapter {

    private final JavaMailSender sender;
    private final String from;

    SmtpMailAdapter(JavaMailSender sender,
                    @Value("${app.mail.from:${spring.mail.username:}}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String notificationReference, MailMessage message) throws Exception {
        MimeMessage mime = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
        if (from != null && !from.isBlank()) helper.setFrom(from);
        helper.setTo(message.to());
        helper.setSubject(message.subject());
        helper.setText(message.text(), message.html());
        sender.send(mime);
    }
}
