package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notification_rendition")
@Getter
@Setter
@NoArgsConstructor
public class Rendition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_request_id", nullable = false, unique = true)
    private NotificationRequest notificationRequest;

    @Column(name = "rendered_subject", nullable = false, length = 255)
    private String renderedSubject;

    @Column(name = "rendered_html", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String renderedHtml;

    @Column(name = "rendered_plain_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String renderedPlainText;

    @Column(name = "rendered_at", nullable = false, updatable = false)
    private Instant renderedAt;

    @PrePersist
    protected void onCreate() {
        if (renderedAt == null) {
            renderedAt = Instant.now();
        }
    }
}

