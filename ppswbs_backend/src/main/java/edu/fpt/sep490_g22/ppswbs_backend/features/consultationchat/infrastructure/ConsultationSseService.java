package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.ConversationDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.MessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
@Slf4j
public class ConsultationSseService {

    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    // conversationReference -> list of active emitters
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void initHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeats, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroyHeartbeat() {
        heartbeatScheduler.shutdownNow();
    }

    private void sendHeartbeats() {
        emitters.forEach((convRef, emitterList) -> {
            for (SseEmitter emitter : emitterList) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    removeEmitter(convRef, emitter);
                }
            }
        });
    }

    public SseEmitter createEmitter(String conversationReference, Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.computeIfAbsent(conversationReference, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(conversationReference, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(conversationReference, emitter);
        });
        emitter.onError(e -> removeEmitter(conversationReference, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "status", "CONNECTED",
                            "conversationReference", conversationReference,
                            "userId", userId != null ? userId : -1L
                    ), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE connected event to user {} for conversation {}: {}",
                    userId, conversationReference, e.getMessage());
            emitter.complete();
            removeEmitter(conversationReference, emitter);
        }

        return emitter;
    }

    public void broadcastMessage(String conversationReference, MessageDto message) {
        if (conversationReference == null || message == null) return;
        List<SseEmitter> convEmitters = emitters.get(conversationReference);
        if (convEmitters == null || convEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : convEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(message, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                log.warn("Failed to send SSE message event for {}: {}", conversationReference, e.getMessage());
                removeEmitter(conversationReference, emitter);
            }
        }
    }

    public void broadcastConversation(String conversationReference, ConversationDto conversation) {
        if (conversationReference == null || conversation == null) return;
        List<SseEmitter> convEmitters = emitters.get(conversationReference);
        if (convEmitters == null || convEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : convEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("conversation")
                        .data(conversation, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                log.warn("Failed to send SSE conversation event for {}: {}", conversationReference, e.getMessage());
                removeEmitter(conversationReference, emitter);
            }
        }
    }

    private void removeEmitter(String conversationReference, SseEmitter emitter) {
        List<SseEmitter> convEmitters = emitters.get(conversationReference);
        if (convEmitters != null) {
            convEmitters.remove(emitter);
            if (convEmitters.isEmpty()) {
                emitters.remove(conversationReference);
            }
        }
    }
}
