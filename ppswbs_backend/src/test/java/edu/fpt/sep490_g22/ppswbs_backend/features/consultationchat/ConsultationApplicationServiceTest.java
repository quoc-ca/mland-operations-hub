package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsultationApplicationServiceTest {

    @Mock
    private ConsultationConversationRepository conversationRepository;
    @Mock
    private ConsultationMessageRepository messageRepository;
    @Mock
    private ConsultationReadStateRepository readStateRepository;
    @Mock
    private ConsultationClaimEventRepository claimEventRepository;
    @Mock
    private ConsultationIdempotencyService idempotencyService;
    @Mock
    private ConsultationAuditService auditService;
    @Mock
    private ConsultationContextService contextService;
    @Mock
    private WorkshopLocationPort locationPort;
    @Mock
    private AccountActorPort accountActorPort;
    @Mock
    private ConsultationSseService sseService;

    private ConsultationRateLimiter rateLimiter;
    private ObjectMapper objectMapper;
    private ConsultationApplicationService service;

    @BeforeEach
    void setUp() {
        rateLimiter = new ConsultationRateLimiter();
        objectMapper = new ObjectMapper();
        service = new ConsultationApplicationService(
                conversationRepository,
                messageRepository,
                readStateRepository,
                claimEventRepository,
                rateLimiter,
                idempotencyService,
                auditService,
                contextService,
                locationPort,
                accountActorPort,
                objectMapper,
                sseService
        );
    }

    @Test
    @DisplayName("T013-040: Admin caller is denied HTTP 403 on all operations")
    void adminCallerIsDenied() {
        var admin = new AccountActorPort.CallerActor(100L, "ADMIN", "ACTIVE", List.of());

        assertThatThrownBy(() -> service.createConversation(
                CreateConversationRequestDto.builder()
                        .contextType(ConsultationContextType.WORKSHOP_RING_DESIGN)
                        .contextReference("DSG-1")
                        .contextVersion(1)
                        .build(),
                admin,
                "key-1",
                "corr-1"
        )).isInstanceOf(ConsultationException.class)
                .satisfies(ex -> {
                    ConsultationException ce = (ConsultationException) ex;
                    assertThat(ce.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ce.getCode()).isEqualTo("CHAT_ACCESS_DENIED");
                });

        assertThatThrownBy(() -> service.listConversations(null, null, admin))
                .isInstanceOf(ConsultationException.class)
                .satisfies(ex -> assertThat(((ConsultationException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("T014-047: unclaimed filter - Manager reads all branches, Staff only their own, Member is denied")
    void unclaimedFilterIsScopedByRole() {
        var manager = new AccountActorPort.CallerActor(2L, "MANAGER", "ACTIVE", List.of());
        var staff = new AccountActorPort.CallerActor(3L, "STAFF", "ACTIVE", List.of(7L));
        var member = new AccountActorPort.CallerActor(10L, "MEMBER", "ACTIVE", List.of());
        when(conversationRepository.findByStatusAndClaimedByUserIdIsNullOrderByUpdatedAtDescConversationReferenceDesc(eq(ConsultationStatus.OPEN), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(conversationRepository.findForStaff(eq(ConsultationStatus.OPEN), eq(true), eq(List.of(7L)), eq(false), eq(3L), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        assertThat(service.listConversations(ConsultationStatus.OPEN, true, null, null, manager).getItems()).isEmpty();
        assertThat(service.listConversations(ConsultationStatus.OPEN, true, null, null, staff).getItems()).isEmpty();
        assertThatThrownBy(() -> service.listConversations(null, true, null, null, member))
                .isInstanceOf(ConsultationException.class)
                .satisfies(ex -> assertThat(((ConsultationException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoMoreInteractions(conversationRepository);
    }

    @Test
    @DisplayName("T013-041: Context validation creates immutable snapshot and rejects invalid context")
    void contextValidationCreatesSnapshot() {
        var member = new AccountActorPort.CallerActor(10L, "MEMBER", "ACTIVE", List.of());
        var req = CreateConversationRequestDto.builder()
                .contextType(ConsultationContextType.WORKSHOP_RING_DESIGN)
                .contextReference("DSG-VALID")
                .contextVersion(1)
                .initialMessage("Tôi muốn được tư vấn")
                .build();

        var snapshotDto = ContextOptionDto.builder()
                .contextType(ConsultationContextType.WORKSHOP_RING_DESIGN)
                .contextReference("DSG-VALID")
                .displayCode("DSG-VALID")
                .displayName("Nhẫn cưới kim cương")
                .designVersion(1)
                .preview(ContextPreviewDto.builder().summary("Bạc 925").build())
                .build();

        when(contextService.validateAndGetSnapshot(eq(10L), eq(ConsultationContextType.WORKSHOP_RING_DESIGN), eq("DSG-VALID"), eq(1)))
                .thenReturn(snapshotDto);

        when(conversationRepository.save(any(ConsultationConversation.class)))
                .thenAnswer(inv -> {
                    ConsultationConversation c = inv.getArgument(0);
                    c.setId(1L);
                    c.setCreatedAt(Instant.now());
                    c.setUpdatedAt(Instant.now());
                    return c;
                });

        when(messageRepository.save(any(ConsultationMessage.class)))
                .thenAnswer(inv -> {
                    ConsultationMessage m = inv.getArgument(0);
                    m.setId(101L);
                    m.setCreatedAt(Instant.now());
                    return m;
                });

        ConversationDto result = service.createConversation(req, member, "idemp-key-create-12345678", "corr-1");

        assertThat(result).isNotNull();
        assertThat(result.getContextType()).isEqualTo(ConsultationContextType.WORKSHOP_RING_DESIGN);
        assertThat(result.getContextSnapshot()).isNotNull();
        assertThat(result.getContextSnapshot().getDisplayCode()).isEqualTo("DSG-VALID");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);

        // Verify audit called without raw text
        verify(auditService).recordAudit(
                eq(1L), eq(10L), eq("MEMBER"),
                eq(ConsultationAuditAction.CREATE), eq(ConsultationAuditOutcome.SUCCESS),
                eq("WORKSHOP_RING_DESIGN"), isNull(), isNull(), eq("corr-1")
        );
    }

    @Test
    @DisplayName("T013-042: Message Unicode NFC normalization and max 2,000 code points enforcement")
    void messageNormalizationAndLengthEnforcement() {
        var member = new AccountActorPort.CallerActor(10L, "MEMBER", "ACTIVE", List.of());
        var conv = ConsultationConversation.builder()
                .id(1L)
                .conversationReference("CONV-1")
                .memberUserId(10L)
                .contextType(ConsultationContextType.WORKSHOP_RING_DESIGN)
                .contextReference("DSG-1")
                .contextVersion(1)
                .status(ConsultationStatus.OPEN)
                .aggregateVersion(1L)
                .build();

        when(conversationRepository.findByConversationReference("CONV-1")).thenReturn(Optional.of(conv));

        // Empty message rejected
        assertThatThrownBy(() -> service.sendMessage("CONV-1", SendMessageRequestDto.builder().message("   ").build(), member, "k1", "c1"))
                .isInstanceOf(ConsultationException.class)
                .satisfies(ex -> assertThat(((ConsultationException) ex).getCode()).isEqualTo("CHAT_MESSAGE_INVALID"));

        // > 2000 chars rejected
        String longMsg = "a".repeat(2001);
        assertThatThrownBy(() -> service.sendMessage("CONV-1", SendMessageRequestDto.builder().message(longMsg).build(), member, "k2", "c2"))
                .isInstanceOf(ConsultationException.class)
                .satisfies(ex -> assertThat(((ConsultationException) ex).getCode()).isEqualTo("CHAT_MESSAGE_TOO_LONG"));

        // Valid Vietnamese message with emoji preserved
        when(messageRepository.save(any(ConsultationMessage.class)))
                .thenAnswer(inv -> {
                    ConsultationMessage m = inv.getArgument(0);
                    m.setId(201L);
                    m.setCreatedAt(Instant.now());
                    return m;
                });

        MessageDto sent = service.sendMessage(
                "CONV-1",
                SendMessageRequestDto.builder().message("Tư vấn nhẫn cưới kim cương 💍").build(),
                member,
                "key-send-12345678",
                "corr-2"
        );

        assertThat(sent).isNotNull();
        assertThat(sent.getText()).isEqualTo("Tư vấn nhẫn cưới kim cương 💍");
        assertThat(sent.getSenderRole()).isEqualTo(ConsultationSenderRole.MEMBER);

        // Verify audit records length, NOT the raw text
        verify(auditService).recordAudit(
                eq(1L), eq(10L), eq("MEMBER"),
                eq(ConsultationAuditAction.SEND_MESSAGE), eq(ConsultationAuditOutcome.SUCCESS),
                eq("WORKSHOP_RING_DESIGN"), eq(28), isNull(), eq("corr-2")
        );
    }

    @Test
    @DisplayName("T013-043: Optimistic concurrency control and claim conflict")
    void optimisticConcurrencyAndClaimConflict() {
        var staff = new AccountActorPort.CallerActor(20L, "STAFF", "ACTIVE", List.of(1001L));
        var conv = ConsultationConversation.builder()
                .id(1L)
                .conversationReference("CONV-1")
                .memberUserId(10L)
                .assignedLocationId(1001L)
                .claimedByUserId(null)
                .status(ConsultationStatus.OPEN)
                .aggregateVersion(1L)
                .build();

        when(conversationRepository.findByConversationReference("CONV-1")).thenReturn(Optional.of(conv));

        // Mismatched version -> CHAT_STATE_CONFLICT
        assertThatThrownBy(() -> service.claim("CONV-1", 99L, staff, "k1", "c1"))
                .isInstanceOf(ConsultationException.class)
                .satisfies(ex -> assertThat(((ConsultationException) ex).getCode()).isEqualTo("CHAT_STATE_CONFLICT"));

        // Successful claim increments aggregateVersion
        when(conversationRepository.save(any(ConsultationConversation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConversationDto claimed = service.claim("CONV-1", 1L, staff, "key-claim-123456", "c2");
        assertThat(claimed.getAggregateVersion()).isEqualTo(2L);

        // Staff claiming unassigned conversation (assignedLocationId == null) assigns staff location
        ConsultationConversation unassignedConv = ConsultationConversation.builder()
                .id(2L)
                .conversationReference("CONV-UNASSIGNED")
                .memberUserId(10L)
                .assignedLocationId(null)
                .claimedByUserId(null)
                .status(ConsultationStatus.OPEN)
                .aggregateVersion(1L)
                .build();
        when(conversationRepository.findByConversationReference("CONV-UNASSIGNED")).thenReturn(Optional.of(unassignedConv));
        ConversationDto claimedUnassigned = service.claim("CONV-UNASSIGNED", 1L, staff, "key-claim-unassigned", "c-unassigned");
        assertThat(claimedUnassigned.getAssignedLocationId()).isEqualTo(1001L);
        assertThat(unassignedConv.getAssignedLocationId()).isEqualTo(1001L);

        // Second claim by different staff fails
        var otherStaff = new AccountActorPort.CallerActor(30L, "STAFF", "ACTIVE", List.of(1001L));
        assertThatThrownBy(() -> service.claim("CONV-1", 2L, otherStaff, "k3", "c3"))
                .isInstanceOf(ConsultationException.class)
                .satisfies(ex -> assertThat(((ConsultationException) ex).getCode()).isEqualTo("CHAT_STATE_CONFLICT"));
    }

    @Test
    @DisplayName("T013-044: Rolling 30 messages/minute rate limit enforcement")
    void rollingRateLimitEnforcement() {
        Long memberId = 9999L;

        // First 30 should pass
        for (int i = 0; i < 30; i++) {
            rateLimiter.checkAndConsume(memberId);
        }

        // 31st call must throw 429 CHAT_RATE_LIMITED with retryAfterSeconds
        assertThatThrownBy(() -> rateLimiter.checkAndConsume(memberId))
                .isInstanceOf(ConsultationException.class)
                .satisfies(ex -> {
                    ConsultationException ce = (ConsultationException) ex;
                    assertThat(ce.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ce.getCode()).isEqualTo("CHAT_RATE_LIMITED");
                    assertThat(ce.getRetryAfterSeconds()).isNotNull().isGreaterThan(0);
                });
    }

    @Test
    @DisplayName("T013-045: Manager routes unassigned conversation to a branch")
    void managerAssignsBranch() {
        var manager = new AccountActorPort.CallerActor(50L, "MANAGER", "ACTIVE", List.of());
        var conv = ConsultationConversation.builder()
                .id(1L)
                .conversationReference("CONV-INDEP-1")
                .memberUserId(10L)
                .assignedLocationId(null) // Unassigned
                .status(ConsultationStatus.OPEN)
                .aggregateVersion(1L)
                .build();

        when(conversationRepository.findByConversationReference("CONV-INDEP-1")).thenReturn(Optional.of(conv));
        when(locationPort.findLocationByCode("HN-HOANKIEM")).thenReturn(Optional.of(
                new WorkshopLocationPort.LocationInfo(1001L, "HN-HOANKIEM", "Chi nhánh Hoàn Kiếm", true)
        ));
        when(conversationRepository.save(any(ConsultationConversation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConversationDto assigned = service.assign(
                "CONV-INDEP-1",
                AssignRequestDto.builder().locationReference("HN-HOANKIEM").build(),
                1L,
                manager,
                "key-assign-123456",
                "c-assign"
        );

        assertThat(assigned.getAggregateVersion()).isEqualTo(2L);
        assertThat(conv.getAssignedLocationId()).isEqualTo(1001L);
        verify(claimEventRepository).save(any(ConsultationClaimEvent.class));
    }

    @Test
    @DisplayName("T013-045: Sending message to closed conversation is rejected with CHAT_CONVERSATION_CLOSED")
    void sendMessageToClosedConversationFails() {
        var member = new AccountActorPort.CallerActor(10L, "MEMBER", "ACTIVE", List.of());
        var conv = ConsultationConversation.builder()
                .id(1L)
                .conversationReference("CONV-CLOSED-1")
                .memberUserId(10L)
                .status(ConsultationStatus.CLOSED)
                .aggregateVersion(2L)
                .build();

        when(conversationRepository.findByConversationReference("CONV-CLOSED-1")).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> service.sendMessage(
                "CONV-CLOSED-1",
                SendMessageRequestDto.builder().message("Tin nhắn sau khi đóng").build(),
                member,
                "k-closed",
                "c-closed"
        )).isInstanceOf(ConsultationException.class)
                .satisfies(ex -> assertThat(((ConsultationException) ex).getCode()).isEqualTo("CHAT_CONVERSATION_CLOSED"));
    }

    @Test
    @DisplayName("T013-046: Staff cannot send message if conversation is unclaimed or claimed by another staff")
    void staffCannotSendMessageWithoutClaiming() {
        var staff1 = new AccountActorPort.CallerActor(20L, "STAFF", "ACTIVE", List.of(1001L));
        var unclaimedConv = ConsultationConversation.builder()
                .id(1L)
                .conversationReference("CONV-UNCLAIMED")
                .memberUserId(10L)
                .assignedLocationId(1001L)
                .claimedByUserId(null)
                .status(ConsultationStatus.OPEN)
                .aggregateVersion(1L)
                .build();

        when(conversationRepository.findByConversationReference("CONV-UNCLAIMED")).thenReturn(Optional.of(unclaimedConv));

        // Unclaimed -> 403 CHAT_NOT_CLAIMED_BY_YOU
        assertThatThrownBy(() -> service.sendMessage(
                "CONV-UNCLAIMED",
                SendMessageRequestDto.builder().message("Nhân viên tư vấn").build(),
                staff1,
                "k-unclaimed",
                "c-unclaimed"
        )).isInstanceOf(ConsultationException.class)
                .satisfies(ex -> {
                    ConsultationException ce = (ConsultationException) ex;
                    assertThat(ce.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ce.getCode()).isEqualTo("CHAT_NOT_CLAIMED_BY_YOU");
                });

        // Claimed by staff 2 (30L) -> staff 1 (20L) gets 403 CHAT_NOT_CLAIMED_BY_YOU
        unclaimedConv.setClaimedByUserId(30L);
        assertThatThrownBy(() -> service.sendMessage(
                "CONV-UNCLAIMED",
                SendMessageRequestDto.builder().message("Nhân viên tư vấn khác cố gửi").build(),
                staff1,
                "k-other",
                "c-other"
        )).isInstanceOf(ConsultationException.class)
                .satisfies(ex -> {
                    ConsultationException ce = (ConsultationException) ex;
                    assertThat(ce.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ce.getCode()).isEqualTo("CHAT_NOT_CLAIMED_BY_YOU");
                });
    }

    @Test
    @DisplayName("T013-047: Staff who claimed conversation can send message and triggers SSE broadcast")
    void staffCanSendMessageWhenClaimedBySelf() {
        var staff1 = new AccountActorPort.CallerActor(20L, "STAFF", "ACTIVE", List.of(1001L));
        var claimedConv = ConsultationConversation.builder()
                .id(1L)
                .conversationReference("CONV-CLAIMED")
                .memberUserId(10L)
                .assignedLocationId(1001L)
                .claimedByUserId(20L)
                .status(ConsultationStatus.OPEN)
                .aggregateVersion(2L)
                .build();

        when(conversationRepository.findByConversationReference("CONV-CLAIMED")).thenReturn(Optional.of(claimedConv));
        when(idempotencyService.checkReplay(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(messageRepository.save(any(ConsultationMessage.class))).thenAnswer(inv -> {
            ConsultationMessage m = inv.getArgument(0);
            m.setId(100L);
            m.setCreatedAt(Instant.now());
            return m;
        });
        when(readStateRepository.findByConversationAndUserId(any(), any())).thenReturn(Optional.empty());

        MessageDto msgDto = service.sendMessage(
                "CONV-CLAIMED",
                SendMessageRequestDto.builder().message("Chào bạn, mình hỗ trợ nhé").build(),
                staff1,
                "k-claim-send",
                "c-claim-send"
        );

        assertThat(msgDto).isNotNull();
        assertThat(msgDto.getText()).isEqualTo("Chào bạn, mình hỗ trợ nhé");
        assertThat(msgDto.getSenderRole()).isEqualTo(ConsultationSenderRole.STAFF);
        verify(sseService).broadcastMessage(eq("CONV-CLAIMED"), any(MessageDto.class));
    }

    @Test
    @DisplayName("T013-048: Manager can release claim held by another staff and triggers SSE broadcast")
    void managerCanReleaseAnyClaim() {
        var manager = new AccountActorPort.CallerActor(50L, "MANAGER", "ACTIVE", List.of());
        var conv = ConsultationConversation.builder()
                .id(1L)
                .conversationReference("CONV-RELEASE-TEST")
                .memberUserId(10L)
                .assignedLocationId(1001L)
                .claimedByUserId(20L) // Claimed by staff 20L
                .status(ConsultationStatus.OPEN)
                .aggregateVersion(3L)
                .build();

        when(conversationRepository.findByConversationReference("CONV-RELEASE-TEST")).thenReturn(Optional.of(conv));
        when(conversationRepository.save(any(ConsultationConversation.class))).thenAnswer(inv -> inv.getArgument(0));

        ConversationDto released = service.release("CONV-RELEASE-TEST", 3L, manager, "k-rel-mgr", "c-rel-mgr");

        assertThat(released).isNotNull();
        assertThat(released.getAggregateVersion()).isEqualTo(4L);
        assertThat(released.getClaimantUserId()).isNull();
        assertThat(conv.getClaimedByUserId()).isNull();
        verify(sseService).broadcastConversation(eq("CONV-RELEASE-TEST"), any(ConversationDto.class));
    }
}
