package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationApplicationService {

    private final ConsultationConversationRepository conversationRepository;
    private final ConsultationMessageRepository messageRepository;
    private final ConsultationReadStateRepository readStateRepository;
    private final ConsultationClaimEventRepository claimEventRepository;
    private final ConsultationRateLimiter rateLimiter;
    private final ConsultationIdempotencyService idempotencyService;
    private final ConsultationAuditService auditService;
    private final ConsultationContextService contextService;
    private final WorkshopLocationPort locationPort;
    private final AccountActorPort accountActorPort;
    private final ObjectMapper objectMapper;

    @Transactional
    public ConversationDto createConversation(
            CreateConversationRequestDto req,
            AccountActorPort.CallerActor caller,
            String idempotencyKey,
            String correlationId
    ) {
        validateCallerRole(caller, List.of("MEMBER"));

        String actorScope = "MEMBER:" + caller.accountId();
        var replay = idempotencyService.checkReplay(idempotencyKey, "CREATE_CONVERSATION", actorScope, req);
        if (replay.isPresent()) {
            return idempotencyService.deserialize(replay.get().getResponseBody(), ConversationDto.class);
        }

        validateContextShape(req);
        ContextOptionDto snapshot = req.getContextType() == null
                ? null
                : contextService.validateAndGetSnapshot(
                    caller.accountId(), req.getContextType(), req.getContextReference(), req.getContextVersion()
                );

        String snapshotJson;
        if (snapshot == null) {
            snapshotJson = null;
        } else {
            try {
                snapshotJson = objectMapper.writeValueAsString(snapshot);
            } catch (JsonProcessingException e) {
                throw ConsultationException.unavailable("CHAT_UNAVAILABLE", "Lỗi tạo snapshot ngữ cảnh.");
            }
        }

        String conversationReference = "CONV-" + UUID.randomUUID();

        ConsultationConversation conv = ConsultationConversation.builder()
                .conversationReference(conversationReference)
                .memberUserId(caller.accountId())
                .contextType(req.getContextType())
                .contextReference(req.getContextReference() == null ? null : req.getContextReference().trim())
                .contextVersion(req.getContextVersion())
                .contextSnapshot(snapshotJson)
                .status(ConsultationStatus.OPEN)
                .aggregateVersion(1L)
                .build();

        ConsultationConversation saved = conversationRepository.save(conv);

        // Process optional initial message
        if (req.getInitialMessage() != null && !req.getInitialMessage().isBlank()) {
            rateLimiter.checkAndConsume(caller.accountId());
            String normalizedText = normalizeAndValidateText(req.getInitialMessage());

            ConsultationMessage msg = ConsultationMessage.builder()
                    .conversation(saved)
                    .senderUserId(caller.accountId())
                    .senderRole(ConsultationSenderRole.MEMBER)
                    .clientMessageReference(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : "INIT-" + UUID.randomUUID())
                    .messageText(normalizedText)
                    .correlationId(correlationId)
                    .build();

            ConsultationMessage savedMsg = messageRepository.save(msg);

            // Record initial read state
            ConsultationReadState readState = ConsultationReadState.builder()
                    .conversation(saved)
                    .userId(caller.accountId())
                    .lastReadMessageId(savedMsg.getId())
                    .build();
            readStateRepository.save(readState);
        }

        auditService.recordAudit(
                saved.getId(), caller.accountId(), caller.role(),
                ConsultationAuditAction.CREATE, ConsultationAuditOutcome.SUCCESS,
                saved.getContextType() == null ? null : saved.getContextType().name(), null, null, correlationId
        );

        ConversationDto result = mapToConversationDto(saved, caller);
        idempotencyService.record(idempotencyKey, "CREATE_CONVERSATION", actorScope, req, result);
        return result;
    }

    @Transactional(readOnly = true)
    public ConversationListDto listConversations(
            ConsultationStatus status,
            String cursor,
            AccountActorPort.CallerActor caller
    ) {
        return listConversations(status, cursor, null, caller);
    }

    @Transactional(readOnly = true)
    public ConversationListDto listConversations(
            ConsultationStatus status,
            String cursor,
            Integer limit,
            AccountActorPort.CallerActor caller
    ) {
        validateCallerNotAdmin(caller);
        int pageSize = Math.min(Math.max(limit == null ? 20 : limit, 1), 50);
        int pageNumber = decodeCursor(cursor);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("conversationReference")));
        Page<ConsultationConversation> page;
        if ("MEMBER".equals(caller.role())) {
            if (status != null) {
                page = conversationRepository.findByMemberUserIdAndStatusAndMemberHiddenAtIsNullOrderByUpdatedAtDescConversationReferenceDesc(caller.accountId(), status, pageRequest);
            } else {
                page = conversationRepository.findByMemberUserIdAndMemberHiddenAtIsNullOrderByUpdatedAtDescConversationReferenceDesc(caller.accountId(), pageRequest);
            }
        } else if ("STAFF".equals(caller.role())) {
            List<Long> activeLocs = caller.activeLocationIds();
            if (activeLocs == null || activeLocs.isEmpty()) {
                page = Page.empty(pageRequest);
            } else if (status != null) {
                page = conversationRepository.findByAssignedLocationIdInAndStatusOrderByUpdatedAtDescConversationReferenceDesc(activeLocs, status, pageRequest);
            } else {
                page = conversationRepository.findByAssignedLocationIdInOrderByUpdatedAtDescConversationReferenceDesc(activeLocs, pageRequest);
            }
        } else if ("MANAGER".equals(caller.role())) {
            if (status != null) {
                page = conversationRepository.findByStatusOrderByUpdatedAtDescConversationReferenceDesc(status, pageRequest);
            } else {
                page = conversationRepository.findByOrderByUpdatedAtDescConversationReferenceDesc(pageRequest);
            }
        } else {
            throw ConsultationException.forbidden("CHAT_ACCESS_DENIED", "Vai trò không có quyền truy cập.");
        }

        List<ConversationDto> dtos = page.getContent().stream()
                .map(c -> mapToConversationDto(c, caller))
                .toList();

        return ConversationListDto.builder()
                .items(dtos)
                .nextCursor(page.hasNext() ? encodeCursor(page.getNumber() + 1) : null)
                .hasMore(page.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public ConversationDetailDto getConversationDetail(
            String conversationReference,
            AccountActorPort.CallerActor caller
    ) {
        validateCallerNotAdmin(caller);
        ConsultationConversation conv = findAndAuthorizeConversation(conversationReference, caller);

        List<ConsultationMessage> messages = messageRepository.findByConversationOrderByCreatedAtAsc(conv);
        List<MessageDto> messageDtos = messages.stream()
                .map(this::mapToMessageDto)
                .toList();

        ConversationDto basic = mapToConversationDto(conv, caller);

        return ConversationDetailDto.builder()
                .conversationReference(basic.getConversationReference())
                .contextType(basic.getContextType())
                .contextSnapshot(basic.getContextSnapshot())
                .assignedLocationReference(basic.getAssignedLocationReference())
                .claimantRole(basic.getClaimantRole())
                .status(basic.getStatus())
                .aggregateVersion(basic.getAggregateVersion())
                .unreadCount(basic.getUnreadCount())
                .updatedAt(basic.getUpdatedAt())
                .createdAt(basic.getCreatedAt())
                .messages(messageDtos)
                .build();
    }

    @Transactional
    public MessageDto sendMessage(
            String conversationReference,
            SendMessageRequestDto req,
            AccountActorPort.CallerActor caller,
            String idempotencyKey,
            String correlationId
    ) {
        validateCallerNotAdmin(caller);
        ConsultationConversation conv = findAndAuthorizeConversation(conversationReference, caller);

        if (conv.getStatus() == ConsultationStatus.CLOSED) {
            throw ConsultationException.conflict("CHAT_CONVERSATION_CLOSED", "Cuộc trò chuyện đã đóng, không thể gửi tin nhắn mới.");
        }

        if ("MEMBER".equals(caller.role())) {
            rateLimiter.checkAndConsume(caller.accountId());
        }

        String actorScope = caller.role() + ":" + caller.accountId();
        var replay = idempotencyService.checkReplay(idempotencyKey, "SEND_MESSAGE", actorScope, req);
        if (replay.isPresent()) {
            return idempotencyService.deserialize(replay.get().getResponseBody(), MessageDto.class);
        }

        String normalizedText = normalizeAndValidateText(req.getMessage());

        ConsultationSenderRole senderRole = ConsultationSenderRole.valueOf(caller.role());
        String clientMsgRef = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : "MSG-" + UUID.randomUUID();

        ConsultationMessage msg = ConsultationMessage.builder()
                .conversation(conv)
                .senderUserId(caller.accountId())
                .senderRole(senderRole)
                .clientMessageReference(clientMsgRef)
                .messageText(normalizedText)
                .correlationId(correlationId)
                .build();

        ConsultationMessage saved = messageRepository.save(msg);

        // Update read state for sender
        ConsultationReadState readState = readStateRepository.findByConversationAndUserId(conv, caller.accountId())
                .orElse(ConsultationReadState.builder().conversation(conv).userId(caller.accountId()).build());
        readState.setLastReadMessageId(saved.getId());
        readStateRepository.save(readState);

        // Update conversation timestamp
        conv.setUpdatedAt(Instant.now());
        conversationRepository.save(conv);

        auditService.recordAudit(
                conv.getId(), caller.accountId(), caller.role(),
                ConsultationAuditAction.SEND_MESSAGE, ConsultationAuditOutcome.SUCCESS,
                conv.getContextType() != null ? conv.getContextType().name() : null,
                normalizedText.codePointCount(0, normalizedText.length()), null, correlationId
        );

        MessageDto result = mapToMessageDto(saved);
        idempotencyService.record(idempotencyKey, "SEND_MESSAGE", actorScope, req, result);
        return result;
    }

    @Transactional
    public ConversationDto claim(
            String conversationReference,
            Long ifMatchVersion,
            AccountActorPort.CallerActor caller,
            String idempotencyKey,
            String correlationId
    ) {
        validateCallerRole(caller, List.of("STAFF", "MANAGER"));
        ConsultationConversation conv = findAndAuthorizeConversation(conversationReference, caller);

        if ("STAFF".equals(caller.role())) {
            if (conv.getAssignedLocationId() == null || !caller.activeLocationIds().contains(conv.getAssignedLocationId())) {
                throw ConsultationException.forbidden("CHAT_ACCESS_DENIED", "Nhân viên chỉ có thể tiếp nhận cuộc trò chuyện thuộc chi nhánh của mình.");
            }
        }

        checkOptimisticLock(conv, ifMatchVersion);

        if (conv.getClaimedByUserId() != null && !conv.getClaimedByUserId().equals(caller.accountId())) {
            throw ConsultationException.conflict("CHAT_STATE_CONFLICT", "Cuộc trò chuyện đã được tiếp nhận bởi nhân viên khác.");
        }

        Long previousClaimantId = conv.getClaimedByUserId();
        conv.setClaimedByUserId(caller.accountId());
        conv.setAggregateVersion(conv.getAggregateVersion() + 1);
        ConsultationConversation saved = conversationRepository.save(conv);

        ConsultationClaimEvent event = ConsultationClaimEvent.builder()
                .conversation(saved)
                .actorUserId(caller.accountId())
                .eventType(ConsultationClaimEventType.CLAIM)
                .previousClaimantId(previousClaimantId)
                .newClaimantId(caller.accountId())
                .correlationId(correlationId)
                .build();
        claimEventRepository.save(event);

        auditService.recordAudit(
                saved.getId(), caller.accountId(), caller.role(),
                ConsultationAuditAction.CLAIM, ConsultationAuditOutcome.SUCCESS,
                saved.getContextType() != null ? saved.getContextType().name() : null, null, null, correlationId
        );

        return mapToConversationDto(saved, caller);
    }

    @Transactional
    public ConversationDto release(
            String conversationReference,
            Long ifMatchVersion,
            AccountActorPort.CallerActor caller,
            String idempotencyKey,
            String correlationId
    ) {
        validateCallerRole(caller, List.of("STAFF", "MANAGER"));
        ConsultationConversation conv = findAndAuthorizeConversation(conversationReference, caller);

        if ("STAFF".equals(caller.role())) {
            if (conv.getClaimedByUserId() == null || !conv.getClaimedByUserId().equals(caller.accountId())) {
                throw ConsultationException.forbidden("CHAT_ACCESS_DENIED", "Bạn không phải là người đang tiếp nhận cuộc trò chuyện này.");
            }
        }

        checkOptimisticLock(conv, ifMatchVersion);

        Long previousClaimantId = conv.getClaimedByUserId();
        conv.setClaimedByUserId(null);
        conv.setAggregateVersion(conv.getAggregateVersion() + 1);
        ConsultationConversation saved = conversationRepository.save(conv);

        ConsultationClaimEvent event = ConsultationClaimEvent.builder()
                .conversation(saved)
                .actorUserId(caller.accountId())
                .eventType(ConsultationClaimEventType.RELEASE)
                .previousClaimantId(previousClaimantId)
                .newClaimantId(null)
                .correlationId(correlationId)
                .build();
        claimEventRepository.save(event);

        auditService.recordAudit(
                saved.getId(), caller.accountId(), caller.role(),
                ConsultationAuditAction.RELEASE, ConsultationAuditOutcome.SUCCESS,
                saved.getContextType() != null ? saved.getContextType().name() : null, null, null, correlationId
        );

        return mapToConversationDto(saved, caller);
    }

    @Transactional
    public ConversationDto assign(
            String conversationReference,
            AssignRequestDto req,
            Long ifMatchVersion,
            AccountActorPort.CallerActor caller,
            String idempotencyKey,
            String correlationId
    ) {
        validateCallerRole(caller, List.of("MANAGER"));
        ConsultationConversation conv = findAndAuthorizeConversation(conversationReference, caller);

        checkOptimisticLock(conv, ifMatchVersion);

        if (req.getLocationReference() == null || req.getLocationReference().isBlank()) {
            throw ConsultationException.badRequest("CHAT_CONTEXT_INVALID", "Mã chi nhánh là bắt buộc.");
        }

        var loc = locationPort.findLocationByCode(req.getLocationReference().trim())
                .orElseThrow(() -> ConsultationException.badRequest("CHAT_CONTEXT_INVALID", "Chi nhánh không tồn tại."));

        if (!loc.active()) {
            throw ConsultationException.badRequest("CHAT_CONTEXT_INVALID", "Chi nhánh hiện không hoạt động.");
        }

        Long previousLocId = conv.getAssignedLocationId();
        conv.setAssignedLocationId(loc.id());
        conv.setAggregateVersion(conv.getAggregateVersion() + 1);
        ConsultationConversation saved = conversationRepository.save(conv);

        ConsultationClaimEvent event = ConsultationClaimEvent.builder()
                .conversation(saved)
                .actorUserId(caller.accountId())
                .eventType(ConsultationClaimEventType.ASSIGN)
                .previousLocationId(previousLocId)
                .newLocationId(loc.id())
                .correlationId(correlationId)
                .build();
        claimEventRepository.save(event);

        auditService.recordAudit(
                saved.getId(), caller.accountId(), caller.role(),
                ConsultationAuditAction.ASSIGN, ConsultationAuditOutcome.SUCCESS,
                saved.getContextType() != null ? saved.getContextType().name() : null, null, loc.code(), correlationId
        );

        return mapToConversationDto(saved, caller);
    }

    @Transactional
    public ConversationDto close(
            String conversationReference,
            Long ifMatchVersion,
            AccountActorPort.CallerActor caller,
            String idempotencyKey,
            String correlationId
    ) {
        validateCallerNotAdmin(caller);
        ConsultationConversation conv = findAndAuthorizeConversation(conversationReference, caller);

        checkOptimisticLock(conv, ifMatchVersion);

        conv.setStatus(ConsultationStatus.CLOSED);
        conv.setClosedAt(Instant.now());
        conv.setAggregateVersion(conv.getAggregateVersion() + 1);
        ConsultationConversation saved = conversationRepository.save(conv);

        auditService.recordAudit(
                saved.getId(), caller.accountId(), caller.role(),
                ConsultationAuditAction.CLOSE, ConsultationAuditOutcome.SUCCESS,
                saved.getContextType() != null ? saved.getContextType().name() : null, null, null, correlationId
        );

        return mapToConversationDto(saved, caller);
    }

    @Transactional
    public void hideForMember(
            String conversationReference,
            Long ifMatchVersion,
            AccountActorPort.CallerActor caller,
            String idempotencyKey,
            String correlationId
    ) {
        validateCallerRole(caller, List.of("MEMBER"));
        String actorScope = "MEMBER:" + caller.accountId();
        Map<String, Object> command = Map.of("conversationReference", conversationReference, "version", ifMatchVersion);
        if (idempotencyService.checkReplay(idempotencyKey, "HIDE_CONVERSATION", actorScope, command).isPresent()) {
            return;
        }
        ConsultationConversation conv = findAndAuthorizeConversation(conversationReference, caller);
        checkOptimisticLock(conv, ifMatchVersion);
        if (conv.getStatus() != ConsultationStatus.CLOSED) {
            throw ConsultationException.conflict("CHAT_DELETE_REQUIRES_CLOSED", "Vui lòng đóng cuộc trò chuyện trước khi xóa.");
        }
        if (conv.getMemberHiddenAt() != null) {
            throw ConsultationException.conflict("CHAT_ALREADY_HIDDEN", "Cuộc trò chuyện đã được ẩn.");
        }
        conv.setMemberHiddenAt(Instant.now());
        conv.setMemberHiddenByUserId(caller.accountId());
        conv.setAggregateVersion(conv.getAggregateVersion() + 1);
        ConsultationConversation saved = conversationRepository.save(conv);
        auditService.recordAudit(saved.getId(), caller.accountId(), caller.role(),
                ConsultationAuditAction.MEMBER_HIDE, ConsultationAuditOutcome.SUCCESS,
                saved.getContextType() == null ? null : saved.getContextType().name(), null, null, correlationId);
        idempotencyService.record(idempotencyKey, "HIDE_CONVERSATION", actorScope, command, Map.of("hidden", true));
    }

    @Transactional
    public ConversationDto reopen(
            String conversationReference,
            Long ifMatchVersion,
            AccountActorPort.CallerActor caller,
            String idempotencyKey,
            String correlationId
    ) {
        validateCallerNotAdmin(caller);
        ConsultationConversation conv = findAndAuthorizeConversation(conversationReference, caller);

        checkOptimisticLock(conv, ifMatchVersion);

        conv.setStatus(ConsultationStatus.OPEN);
        conv.setClosedAt(null);
        conv.setAggregateVersion(conv.getAggregateVersion() + 1);
        ConsultationConversation saved = conversationRepository.save(conv);

        auditService.recordAudit(
                saved.getId(), caller.accountId(), caller.role(),
                ConsultationAuditAction.REOPEN, ConsultationAuditOutcome.SUCCESS,
                saved.getContextType() != null ? saved.getContextType().name() : null, null, null, correlationId
        );

        return mapToConversationDto(saved, caller);
    }

    @Transactional
    public void recordRead(
            String conversationReference,
            ReadStateRequestDto req,
            AccountActorPort.CallerActor caller,
            String correlationId
    ) {
        validateCallerNotAdmin(caller);
        ConsultationConversation conv = findAndAuthorizeConversation(conversationReference, caller);

        ConsultationMessage targetMsg = messageRepository.findByClientMessageReference(req.getMessageReference().trim())
                .orElse(null);

        Long lastReadId = targetMsg != null ? targetMsg.getId() : 0L;

        ConsultationReadState readState = readStateRepository.findByConversationAndUserId(conv, caller.accountId())
                .orElse(ConsultationReadState.builder().conversation(conv).userId(caller.accountId()).build());

        if (lastReadId > (readState.getLastReadMessageId() != null ? readState.getLastReadMessageId() : 0L)) {
            readState.setLastReadMessageId(lastReadId);
            readStateRepository.save(readState);
        }

        auditService.recordAudit(
                conv.getId(), caller.accountId(), caller.role(),
                ConsultationAuditAction.READ, ConsultationAuditOutcome.SUCCESS,
                conv.getContextType() != null ? conv.getContextType().name() : null, null, null, correlationId
        );
    }

    private ConsultationConversation findAndAuthorizeConversation(
            String conversationReference,
            AccountActorPort.CallerActor caller
    ) {
        if (conversationReference == null || conversationReference.isBlank()) {
            throw ConsultationException.badRequest("CHAT_CONTEXT_INVALID", "Mã cuộc trò chuyện không hợp lệ.");
        }

        ConsultationConversation conv = conversationRepository.findByConversationReference(conversationReference.trim())
                .orElseThrow(() -> ConsultationException.notFound("CHAT_NOT_FOUND", "Không tìm thấy cuộc trò chuyện."));

        if ("MEMBER".equals(caller.role())) {
            if (conv.getMemberHiddenAt() != null) {
                throw ConsultationException.notFound("CHAT_NOT_FOUND", "Conversation not found.");
            }
            if (!conv.getMemberUserId().equals(caller.accountId())) {
                throw ConsultationException.notFound("CHAT_NOT_FOUND", "Không tìm thấy cuộc trò chuyện.");
            }
        } else if ("STAFF".equals(caller.role())) {
            List<Long> activeLocs = caller.activeLocationIds();
            if (conv.getAssignedLocationId() == null || activeLocs == null || !activeLocs.contains(conv.getAssignedLocationId())) {
                throw ConsultationException.notFound("CHAT_NOT_FOUND", "Không tìm thấy cuộc trò chuyện.");
            }
        } else if ("MANAGER".equals(caller.role())) {
            // Manager has global access to all conversations
        } else {
            throw ConsultationException.forbidden("CHAT_ACCESS_DENIED", "Vai trò không có quyền truy cập.");
        }

        return conv;
    }

    private void checkOptimisticLock(ConsultationConversation conv, Long ifMatchVersion) {
        if (ifMatchVersion == null || !conv.getAggregateVersion().equals(ifMatchVersion)) {
            throw ConsultationException.conflict(
                    "CHAT_STATE_CONFLICT",
                    "Phiên bản cuộc trò chuyện đã thay đổi hoặc không khớp. Vui lòng tải lại trang."
            );
        }
    }

    private String normalizeAndValidateText(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ConsultationException.badRequest("CHAT_MESSAGE_INVALID", "Nội dung tin nhắn không được để trống.");
        }
        String normalized = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints > 2000) {
            throw ConsultationException.badRequest(
                    "CHAT_MESSAGE_TOO_LONG",
                    "Tin nhắn vượt quá giới hạn 2.000 ký tự (hiện tại: " + codePoints + " ký tự)."
            );
        }
        return normalized;
    }

    private void validateContextShape(CreateConversationRequestDto req) {
        boolean any = req.getContextType() != null || req.getContextReference() != null || req.getContextVersion() != null;
        boolean complete = req.getContextType() != null && req.getContextReference() != null
                && !req.getContextReference().isBlank() && req.getContextVersion() != null;
        if (any && !complete) {
            throw ConsultationException.badRequest("CHAT_CONTEXT_INVALID", "Ngữ cảnh thiết kế phải được gửi đủ thông tin.");
        }
    }

    private int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            return Math.max(Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor))), 0);
        } catch (Exception e) {
            throw ConsultationException.badRequest("CHAT_CURSOR_INVALID", "Cursor không hợp lệ.");
        }
    }

    private String encodeCursor(int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(offset).getBytes());
    }

    private void validateCallerRole(AccountActorPort.CallerActor caller, List<String> allowedRoles) {
        if (caller == null) {
            throw ConsultationException.unauthorized("CHAT_AUTHENTICATION_REQUIRED", "Yêu cầu đăng nhập.");
        }
        if ("ADMIN".equals(caller.role())) {
            throw ConsultationException.forbidden("CHAT_ACCESS_DENIED", "Tài khoản quản trị viên không có quyền tham gia tư vấn.");
        }
        if (!allowedRoles.contains(caller.role())) {
            throw ConsultationException.forbidden("CHAT_ACCESS_DENIED", "Vai trò không có quyền thực hiện thao tác này.");
        }
    }

    private void validateCallerNotAdmin(AccountActorPort.CallerActor caller) {
        if (caller == null) {
            throw ConsultationException.unauthorized("CHAT_AUTHENTICATION_REQUIRED", "Yêu cầu đăng nhập.");
        }
        if ("ADMIN".equals(caller.role())) {
            throw ConsultationException.forbidden("CHAT_ACCESS_DENIED", "Tài khoản quản trị viên không có quyền tham gia tư vấn.");
        }
    }

    private ConversationDto mapToConversationDto(ConsultationConversation c, AccountActorPort.CallerActor caller) {
        ContextOptionDto snapshot = null;
        if (c.getContextSnapshot() != null && !c.getContextSnapshot().isBlank()) {
            try {
                snapshot = objectMapper.readValue(c.getContextSnapshot(), ContextOptionDto.class);
            } catch (Exception ignored) {}
        }

        String locationRef = null;
        if (c.getAssignedLocationId() != null) {
            locationRef = locationPort.findLocationById(c.getAssignedLocationId())
                    .map(WorkshopLocationPort.LocationInfo::code)
                    .orElse(String.valueOf(c.getAssignedLocationId()));
        }

        ConsultationSenderRole claimantRole = null;
        if (c.getClaimedByUserId() != null) {
            claimantRole = ConsultationSenderRole.STAFF; // default staff/manager
        }

        // Calculate unread count
        int unreadCount = 0;
        Optional<ConsultationReadState> readState = readStateRepository.findByConversationAndUserId(c, caller.accountId());
        Long lastReadId = readState.map(ConsultationReadState::getLastReadMessageId).orElse(0L);

        List<ConsultationSenderRole> unreadSenderRoles = "MEMBER".equals(caller.role())
                ? List.of(ConsultationSenderRole.STAFF, ConsultationSenderRole.MANAGER)
                : List.of(ConsultationSenderRole.MEMBER);

        unreadCount = messageRepository.countUnreadMessages(c, unreadSenderRoles, lastReadId);

        return ConversationDto.builder()
                .conversationReference(c.getConversationReference())
                .contextType(c.getContextType())
                .contextSnapshot(snapshot)
                .assignedLocationReference(locationRef)
                .claimantRole(claimantRole)
                .status(c.getStatus())
                .aggregateVersion(c.getAggregateVersion())
                .unreadCount(unreadCount)
                .updatedAt(c.getUpdatedAt())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private MessageDto mapToMessageDto(ConsultationMessage m) {
        return MessageDto.builder()
                .messageReference(m.getClientMessageReference())
                .senderRole(m.getSenderRole())
                .text(m.getMessageText())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
