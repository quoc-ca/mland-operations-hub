package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the strict JSON reader; Spring Boot places converter beans ahead of the defaults. */
@Configuration(proxyBeanMethods = false)
class CatalogueWebConfiguration {

    @Bean
    CatalogueStrictJsonConverter catalogueStrictJsonConverter(ObjectMapper objectMapper) {
        return new CatalogueStrictJsonConverter(objectMapper);
    }
}
