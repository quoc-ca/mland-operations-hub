package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default adapter ({@code app.mail.adapter=captured}): no email leaves the machine. The message is kept in memory (for
 * tests) and written to a local outbox folder so a developer can open it and follow the booking link. The application
 * log only says that a message was captured: it never prints the recipient, tickets or link.
 */
@Component
@ConditionalOnProperty(name = "app.mail.adapter", havingValue = "captured", matchIfMissing = true)
@Slf4j
class CapturedMailAdapter implements MailAdapter {

    private final List<MailMessage> captured = new CopyOnWriteArrayList<>();
    private final Path outbox;

    CapturedMailAdapter(@Value("${app.mail.outbox-dir:target/mail-outbox}") String outboxDir) {
        this.outbox = Path.of(outboxDir);
    }

    @Override
    public void send(String notificationReference, MailMessage message) throws Exception {
        captured.add(message);
        Files.createDirectories(outbox);
        String safeName = notificationReference.replaceAll("[^A-Za-z0-9_-]", "_");
        Files.writeString(outbox.resolve(safeName + ".txt"),
                "To: " + message.to() + "\nSubject: " + message.subject() + "\n\n" + message.text(), StandardCharsets.UTF_8);
        log.info("Captured booking email {} in the local outbox (set app.mail.adapter=smtp to send for real)", notificationReference);
    }

    List<MailMessage> captured() {
        return List.copyOf(captured);
    }
}
