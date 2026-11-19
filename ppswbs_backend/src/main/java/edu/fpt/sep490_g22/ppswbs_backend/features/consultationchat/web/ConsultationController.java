package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.web;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.ConsultationApplicationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.ConsultationContextService;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.ConsultationException;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.AccountActorPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/consultations")
@RequiredArgsConstructor
@Slf4j
public class ConsultationController {

    private final ConsultationApplicationService applicationService;
    private final ConsultationContextService contextService;
    private final AccountActorPort accountActorPort;

    @GetMapping("/context-options")
    public ResponseEntity<ContextOptionListDto> getContextOptions(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        AccountActorPort.CallerActor caller = requireCaller();
        if ("ADMIN".equals(caller.role())) {
            throw ConsultationException.forbidden("CHAT_ACCESS_DENIED", "Tài khoản quản trị viên không có quyền truy cập.");
        }
        if (!"MEMBER".equals(caller.role())) {
            throw ConsultationException.forbidden("CHAT_ACCESS_DENIED", "Chỉ Member mới có quyền xem danh sách ngữ cảnh thiết kế.");
        }

        List<ContextOptionDto> items = contextService.getMemberContextOptions(caller.accountId());
        return ResponseEntity.ok(ContextOptionListDto.builder().items(items).build());
    }

    @PostMapping
    public ResponseEntity<ConversationDto> createConversation(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody CreateConversationRequestDto body
    ) {
        AccountActorPort.CallerActor caller = requireCaller();
        ConversationDto result = applicationService.createConversation(body, caller, idempotencyKey, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<ConversationListDto> listConversations(
            @RequestParam(value = "status", required = false) ConsultationStatus status,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        AccountActorPort.CallerActor caller = requireCaller();
        ConversationListDto result = applicationService.listConversations(status, cursor, limit, caller);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{conversationReference}")
    public ResponseEntity<ConversationDetailDto> getConversationDetail(
            @PathVariable("conversationReference") String conversationReference
    ) {
        AccountActorPort.CallerActor caller = requireCaller();
        ConversationDetailDto result = applicationService.getConversationDetail(conversationReference, caller);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{conversationReference}/messages")
    public ResponseEntity<MessageDto> sendMessage(
            @PathVariable("conversationReference") String conversationReference,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody SendMessageRequestDto body
    ) {
        AccountActorPort.CallerActor caller = requireCaller();
        MessageDto result = applicationService.sendMessage(conversationReference, body, caller, idempotencyKey, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{conversationReference}/claim")
    public ResponseEntity<ConversationDto> claim(
            @PathVariable("conversationReference") String conversationReference,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        AccountActorPort.CallerActor caller = requireCaller();
        ConversationDto result = applicationService.claim(conversationReference, ifMatchVersion, caller, idempotencyKey, correlationId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{conversationReference}/release")
    public ResponseEntity<ConversationDto> release(
            @PathVariable("conversationReference") String conversationReference,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        AccountActorPort.CallerActor caller = requireCaller();
        ConversationDto result = applicationService.release(conversationReference, ifMatchVersion, caller, idempotencyKey, correlationId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{conversationReference}/assign")
    public ResponseEntity<ConversationDto> assign(
            @PathVariable("conversationReference") String conversationReference,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody AssignRequestDto body
    ) {
        AccountActorPort.CallerActor caller = requireCaller();
        ConversationDto result = applicationService.assign(conversationReference, body, ifMatchVersion, caller, idempotencyKey, correlationId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{conversationReference}/read")
    public ResponseEntity<Void> recordRead(
            @PathVariable("conversationReference") String conversationReference,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody ReadStateRequestDto body
    ) {
        AccountActorPort.CallerActor caller = requireCaller();
        applicationService.recordRead(conversationReference, body, caller, correlationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationReference}/close")
    public ResponseEntity<ConversationDto> close(
            @PathVariable("conversationReference") String conversationReference,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        AccountActorPort.CallerActor caller = requireCaller();
        ConversationDto result = applicationService.close(conversationReference, ifMatchVersion, caller, idempotencyKey, correlationId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{conversationReference}")
    public ResponseEntity<Void> hideForMember(
            @PathVariable("conversationReference") String conversationReference,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        AccountActorPort.CallerActor caller = requireCaller();
        applicationService.hideForMember(conversationReference, ifMatchVersion, caller, idempotencyKey, correlationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationReference}/reopen")
    public ResponseEntity<ConversationDto> reopen(
            @PathVariable("conversationReference") String conversationReference,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match-Version") Long ifMatchVersion,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        AccountActorPort.CallerActor caller = requireCaller();
        ConversationDto result = applicationService.reopen(conversationReference, ifMatchVersion, caller, idempotencyKey, correlationId);
        return ResponseEntity.ok(result);
    }

    private AccountActorPort.CallerActor requireCaller() {
        return accountActorPort.getCurrentCaller()
                .orElseThrow(() -> ConsultationException.unauthorized("CHAT_AUTHENTICATION_REQUIRED", "Yêu cầu đăng nhập."));
    }
}
