package edu.fpt.sep490_g22.ppswbs_backend.designreview;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.application.DataRetentionCleanupJob;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ActorScope;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.AiScope;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.AiTextInteraction;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.AiTextInteractionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DataRetentionIT {

    @Autowired
    private AiTextInteractionRepository aiTextInteractionRepository;

    @Autowired
    private DataRetentionCleanupJob dataRetentionCleanupJob;

    @Test
    void whenTextInteractionCreated_thenRetentionIsExactly30Days() {
        Instant before = Instant.now();
        AiTextInteraction interaction = AiTextInteraction.builder()
                .actorScope(ActorScope.GUEST)
                .promptText("Workshop có những khung giờ nào?")
                .responseText("Workshop mở cửa các ngày trong tuần.")
                .scope(AiScope.WORKSHOP)
                .build();

        AiTextInteraction saved = aiTextInteractionRepository.saveAndFlush(interaction);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isNotNull();

        // Verify retention deadline is exactly 30 days from creation
        long durationDays = Duration.between(saved.getCreatedAt(), saved.getExpiresAt()).toDays();
        assertThat(durationDays).isEqualTo(30L);
    }

    @Test
    void whenCleanupJobRuns_thenExpiredInteractionsArePurgedAndActiveRetained() {
        Instant now = Instant.now();

        // 1. Expired interaction (simulating 31 days ago)
        AiTextInteraction expiredInteraction = AiTextInteraction.builder()
                .actorScope(ActorScope.GUEST)
                .promptText("Câu hỏi cũ cách đây 31 ngày")
                .responseText("Phản hồi cũ")
                .scope(AiScope.FAQ)
                .createdAt(now.minus(Duration.ofDays(31)))
                .expiresAt(now.minus(Duration.ofDays(1)))
                .build();
        aiTextInteractionRepository.saveAndFlush(expiredInteraction);

        // 2. Active interaction (created today, expires in 30 days)
        AiTextInteraction activeInteraction = AiTextInteraction.builder()
                .actorScope(ActorScope.GUEST)
                .promptText("Câu hỏi hôm nay")
                .responseText("Phản hồi hôm nay")
                .scope(AiScope.STORE)
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofDays(30)))
                .build();
        aiTextInteractionRepository.saveAndFlush(activeInteraction);

        // 3. Execute cleanup job
        int deletedCount = dataRetentionCleanupJob.cleanupExpiredTextInteractions(now);

        // 4. Verify results
        assertThat(deletedCount).isGreaterThanOrEqualTo(1);

        List<AiTextInteraction> remaining = aiTextInteractionRepository.findAll();
        assertThat(remaining.stream().anyMatch(i -> "Câu hỏi hôm nay".equals(i.getPromptText()))).isTrue();
        assertThat(remaining.stream().anyMatch(i -> "Câu hỏi cũ cách đây 31 ngày".equals(i.getPromptText()))).isFalse();
    }
}
