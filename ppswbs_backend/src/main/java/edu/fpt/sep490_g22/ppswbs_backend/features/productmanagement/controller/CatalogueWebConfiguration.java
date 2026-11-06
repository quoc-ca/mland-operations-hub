package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Puts the strict JSON reader first in Spring MVC's converter list. It is deliberately NOT a bean: a
 * {@code MappingJackson2HttpMessageConverter} bean makes Spring Boot skip its own Jackson converter, and every
 * feature would then write dates as numbers instead of ISO-8601 strings. The reader only reads productmanagement
 * DTOs and never writes, so all other requests and every response keep Boot's application-wide Jackson settings.
 */
@Configuration(proxyBeanMethods = false)
class CatalogueWebConfiguration implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    CatalogueWebConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, new CatalogueStrictJsonConverter(objectMapper));
    }
}
