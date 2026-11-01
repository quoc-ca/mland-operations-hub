package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingNotificationIntentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MagicLinkAccessRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkAccess;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkLifecycleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Scrubs the narrowly permitted raw bearer URL once its Magic Link is no longer usable. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingMagicLinkSnapshotCleanupService {

    private static final List<MagicLinkLifecycleStatus> TERMINAL_STATUSES = List.of(
            MagicLinkLifecycleStatus.REVOKED, MagicLinkLifecycleStatus.EXPIRED);

    private final MagicLinkAccessRepository magicLinkRepository;
    private final BookingNotificationIntentRepository notificationIntentRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 60_000L)
    @Transactional
    public void scrubExpiredOrRevokedLinks() {
        var now = java.time.Instant.now();
        List<MagicLinkAccess> candidates = magicLinkRepository
                .findLinksWhoseSnapshotsMustBeScrubbed(TERMINAL_STATUSES, now);
        int scrubbed = 0;
        for (MagicLinkAccess link : candidates) {
            if (link.getLifecycleStatus() == MagicLinkLifecycleStatus.ACTIVE
                    && !link.getExpiresAt().isAfter(now)) {
                link.setLifecycleStatus(MagicLinkLifecycleStatus.EXPIRED);
                magicLinkRepository.save(link);
            }
            boolean cleanupFailed = false;
            for (var intent : notificationIntentRepository.findByBookingId(link.getBooking().getId())) {
                String snapshot = intent.getDeliverySnapshot();
                try {
                    JsonNode root = objectMapper.readTree(snapshot);
                    if (root.isTextual()) root = objectMapper.readTree(root.asText());
                    if (!(root instanceof ObjectNode objectNode)) {
                        // Do not claim cleanup completion when the snapshot shape is unknown.
                        log.warn("Could not scrub booking notification snapshot with unexpected shape: notificationReference={}",
                                intent.getNotificationReference());
                        cleanupFailed = true;
                        continue;
                    }
                    String accessLink = objectNode.path("bookingAccessLink").asText("");
                    String token = tokenFromAccessLink(accessLink);
                    if (token == null || !hashToken(token).equals(link.getTokenHash())) continue;
                    objectNode.remove("bookingAccessLink");
                    intent.setDeliverySnapshot(objectMapper.writeValueAsString(objectNode));
                    notificationIntentRepository.save(intent);
                    scrubbed++;
                } catch (Exception exception) {
                    // Snapshot data can contain a bearer URL; never log the parser exception or payload.
                    log.warn("Could not scrub malformed booking notification snapshot: notificationReference={}",
                            intent.getNotificationReference());
                    cleanupFailed = true;
                }
            }
            if (!cleanupFailed) {
                link.setDeliveryValueScrubbedAt(now);
                magicLinkRepository.save(link);
            }
        }
        if (scrubbed > 0) log.info("Scrubbed expired/revoked booking access links from {} notification snapshots", scrubbed);
    }

    private String tokenFromAccessLink(String accessLink) {
        if (accessLink == null || accessLink.isBlank()) return null;
        int fragmentStart = accessLink.indexOf('#');
        if (fragmentStart < 0 || fragmentStart == accessLink.length() - 1) return null;
        return accessLink.substring(fragmentStart + 1);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
