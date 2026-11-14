package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao.RenditionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.NotificationRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.Rendition;
import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RenditionService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");

    private final RenditionRepository renditionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Gets an existing frozen Rendition for the given request or generates, persists, and returns a new one.
     */
    @Transactional
    public Rendition getOrCreateRendition(NotificationRequest request) {
        Optional<Rendition> existing = renditionRepository.findByNotificationRequestId(request.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        TemplateVersion version = request.getTemplateVersion();
        Map<String, Object> data = parseSnapshot(request.getBusinessDataSnapshot());

        String renderedSubject = substitute(version.getSubjectTemplate(), data);
        String renderedHtml = substitute(version.getHtmlTemplate(), data);
        String renderedPlainText = substitute(version.getPlainTextTemplate(), data);

        Rendition rendition = new Rendition();
        rendition.setNotificationRequest(request);
        rendition.setRenderedSubject(renderedSubject);
        rendition.setRenderedHtml(renderedHtml);
        rendition.setRenderedPlainText(renderedPlainText);
        rendition.setRenderedAt(Instant.now());

        return renditionRepository.save(rendition);
    }

    /**
     * Renders templates with sample data for dashboard preview.
     */
    public RenderedOutput renderSample(String subjectTemplate, String htmlTemplate, String plainTextTemplate, Map<String, Object> sampleData) {
        String subject = substitute(subjectTemplate, sampleData);
        String html = substitute(htmlTemplate, sampleData);
        String plainText = substitute(plainTextTemplate, sampleData);
        return new RenderedOutput(subject, html, plainText);
    }

    /**
     * Replaces {{placeholder}} tokens with values from data map.
     */
    public String substitute(String template, Map<String, Object> data) {
        if (template == null) {
            return "";
        }
        if (data == null || data.isEmpty()) {
            return template;
        }

        String result = template;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String token = "{{" + entry.getKey() + "}}";
            String val = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            result = result.replace(token, val);
        }
        return result;
    }

    /**
     * Extracts all unique placeholder names from a string (without the {{ }} delimiters).
     */
    public Set<String> extractPlaceholders(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> placeholders = new HashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return placeholders;
    }

    private Map<String, Object> parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to parse businessDataSnapshot JSON: {}", json, e);
            return Collections.emptyMap();
        }
    }

    public record RenderedOutput(String subject, String html, String plainText) {}
}

