package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.DeliveryAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.NotificationRequestRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.DeliveryAttempt;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.Rendition;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.DeliveryWorkerService;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.RenditionService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryWorkerServiceTest {

    @Mock
    private NotificationRequestRepository notificationRequestRepository;
    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;
    @Mock
    private RenditionService renditionService;
    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock
    private JavaMailSender javaMailSender;
    @Mock
    private PlatformTransactionManager transactionManager;

    private DeliveryWorkerService workerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        workerService = new DeliveryWorkerService(
                notificationRequestRepository,
                deliveryAttemptRepository,
                renditionService,
                mailSenderProvider,
                objectMapper,
                transactionManager,
                "noreply@mland.vn"
        );
    }

    @Test
    @DisplayName("Should successfully deliver email and update state to SENT")
    void shouldDeliverSuccessfully() {
        NotificationRequest req = new NotificationRequest();
        req.setId(1L);
        req.setDeliveryState("PENDING");
        req.setAttemptCount(0);
        req.setRecipientSnapshot("{\"email\":\"test@example.com\"}");

        Rendition rendition = new Rendition();
        rendition.setRenderedSubject("Subject");
        rendition.setRenderedHtml("<p>Body</p>");
        rendition.setRenderedPlainText("Body");

        when(notificationRequestRepository.findById(1L)).thenReturn(Optional.of(req));
        when(renditionService.getOrCreateRendition(req)).thenReturn(rendition);
        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        workerService.deliverSingleRequest(1L);

        verify(javaMailSender).send(mimeMessage);
        verify(deliveryAttemptRepository).save(any(DeliveryAttempt.class));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationRequestRepository, atLeastOnce()).save(captor.capture());

        NotificationRequest finalReq = captor.getValue();
        assertThat(finalReq.getDeliveryState()).isEqualTo("SENT");
        assertThat(finalReq.getAttemptCount()).isEqualTo(1);
        assertThat(finalReq.getTerminalAt()).isNotNull();
    }

    @Test
    @DisplayName("Should schedule retry on temporary error (attempt 1 -> RETRY_SCHEDULED in 1 min)")
    void shouldScheduleRetryOnTemporaryError() {
        NotificationRequest req = new NotificationRequest();
        req.setId(2L);
        req.setDeliveryState("PENDING");
        req.setAttemptCount(0);
        req.setRecipientSnapshot("{\"email\":\"test@example.com\"}");

        Rendition rendition = new Rendition();
        rendition.setRenderedSubject("Subject");
        rendition.setRenderedHtml("<p>Body</p>");
        rendition.setRenderedPlainText("Body");

        when(notificationRequestRepository.findById(2L)).thenReturn(Optional.of(req));
        when(renditionService.getOrCreateRendition(req)).thenReturn(rendition);
        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("Connection refused connecting to mail host")).when(javaMailSender).send(mimeMessage);

        workerService.deliverSingleRequest(2L);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationRequestRepository, atLeastOnce()).save(captor.capture());

        NotificationRequest finalReq = captor.getValue();
        assertThat(finalReq.getDeliveryState()).isEqualTo("RETRY_SCHEDULED");
        assertThat(finalReq.getAttemptCount()).isEqualTo(1);
        assertThat(finalReq.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("Should transition to FAILED after max 3 attempts (SC-003)")
    void shouldFailAfterMaxAttempts() {
        NotificationRequest req = new NotificationRequest();
        req.setId(3L);
        req.setDeliveryState("RETRY_SCHEDULED");
        req.setAttemptCount(2); // this will be attempt 3
        req.setRecipientSnapshot("{\"email\":\"test@example.com\"}");

        Rendition rendition = new Rendition();
        rendition.setRenderedSubject("Subject");
        rendition.setRenderedHtml("<p>Body</p>");
        rendition.setRenderedPlainText("Body");

        when(notificationRequestRepository.findById(3L)).thenReturn(Optional.of(req));
        when(renditionService.getOrCreateRendition(req)).thenReturn(rendition);
        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("Timeout connecting to mail host")).when(javaMailSender).send(mimeMessage);

        workerService.deliverSingleRequest(3L);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationRequestRepository, atLeastOnce()).save(captor.capture());

        NotificationRequest finalReq = captor.getValue();
        assertThat(finalReq.getDeliveryState()).isEqualTo("FAILED");
        assertThat(finalReq.getAttemptCount()).isEqualTo(3);
        assertThat(finalReq.getTerminalAt()).isNotNull();
    }
}

