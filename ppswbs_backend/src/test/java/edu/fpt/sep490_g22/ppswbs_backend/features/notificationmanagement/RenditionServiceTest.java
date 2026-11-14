package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.RenditionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.Rendition;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateVersion;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service.RenditionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RenditionServiceTest {

    @Mock
    private RenditionRepository renditionRepository;

    private RenditionService renditionService;

    @BeforeEach
    void setUp() {
        renditionService = new RenditionService(renditionRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("Should extract placeholders correctly")
    void shouldExtractPlaceholders() {
        String template = "Hello {{customerName}}, your booking #{{bookingCode}} is confirmed for {{workshopTitle}}.";
        Set<String> placeholders = renditionService.extractPlaceholders(template);

        assertThat(placeholders).containsExactlyInAnyOrder("customerName", "bookingCode", "workshopTitle");
    }

    @Test
    @DisplayName("Should substitute placeholders with values")
    void shouldSubstitutePlaceholders() {
        String template = "Hello {{customerName}}, booking #{{bookingCode}}.";
        Map<String, Object> data = Map.of("customerName", "Alice", "bookingCode", "BK-100");

        String result = renditionService.substitute(template, data);
        assertThat(result).isEqualTo("Hello Alice, booking #BK-100.");
    }

    @Test
    @DisplayName("Should return existing rendition if present")
    void shouldReturnExistingRendition() {
        NotificationRequest request = new NotificationRequest();
        request.setId(10L);

        Rendition existing = new Rendition();
        existing.setId(5L);
        existing.setRenderedSubject("Existing Subject");

        when(renditionRepository.findByNotificationRequestId(10L)).thenReturn(Optional.of(existing));

        Rendition result = renditionService.getOrCreateRendition(request);
        assertThat(result).isSameAs(existing);
        verify(renditionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should generate and save new rendition if not exists")
    void shouldGenerateNewRendition() {
        NotificationRequest request = new NotificationRequest();
        request.setId(20L);
        request.setBusinessDataSnapshot("{\"customerName\":\"Bob\",\"orderCode\":\"ORD-99\"}");

        TemplateVersion version = new TemplateVersion();
        version.setSubjectTemplate("Order {{orderCode}}");
        version.setHtmlTemplate("<p>Dear {{customerName}}</p>");
        version.setPlainTextTemplate("Dear {{customerName}}");
        request.setTemplateVersion(version);

        when(renditionRepository.findByNotificationRequestId(20L)).thenReturn(Optional.empty());
        when(renditionRepository.save(any(Rendition.class))).thenAnswer(inv -> inv.getArgument(0));

        Rendition result = renditionService.getOrCreateRendition(request);
        assertThat(result.getRenderedSubject()).isEqualTo("Order ORD-99");
        assertThat(result.getRenderedHtml()).isEqualTo("<p>Dear Bob</p>");
        assertThat(result.getRenderedPlainText()).isEqualTo("Dear Bob");
        verify(renditionRepository).save(any(Rendition.class));
    }
}

