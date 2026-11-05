package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T008: typed safe errors, field errors, no-store and correlation ID for productmanagement. */
class CatalogueExceptionHandlerTests {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ObjectMapper applicationMapper = Jackson2ObjectMapperBuilder.json().build();
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setMessageConverters(new CatalogueStrictJsonConverter(applicationMapper),
                        new StringHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(applicationMapper))
                .setControllerAdvice(new CatalogueExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void businessErrorUsesContractCodeAndStatus() throws Exception {
        mvc.perform(get("/probe/error/PENDING_PRICE_PROPOSAL_EXISTS"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("PENDING_PRICE_PROPOSAL_EXISTS"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void identityUnavailableIsServiceUnavailable() throws Exception {
        mvc.perform(get("/probe/error/IDENTITY_DEPENDENCY_UNAVAILABLE"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IDENTITY_DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void valueRuleViolationReturnsFieldError() throws Exception {
        mvc.perform(get("/probe/amount/0100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("proposedPrice"));
    }

    @Test
    void unknownJsonFieldIsRejected() throws Exception {
        mvc.perform(post("/probe/body")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nhẫn\",\"sku\":\"SP000001-S05\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sku"));
    }

    @Test
    void wrongTypeJsonIsValidationError() throws Exception {
        mvc.perform(post("/probe/body")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nhẫn\",\"expectedVersion\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("expectedVersion"));
    }

    @Test
    void numericStringIsNotSilentlyCoercedToNumber() throws Exception {
        mvc.perform(post("/probe/body")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nhẫn\",\"expectedVersion\":\"5\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("expectedVersion"));
    }

    @Test
    void validStrictBodyIsAccepted() throws Exception {
        mvc.perform(post("/probe/body")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nhẫn\",\"expectedVersion\":5}"))
                .andExpect(status().isOk());
    }

    @Test
    void missingIdempotencyKeyIsValidationError() throws Exception {
        mvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nhẫn\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));
    }

    @Test
    void optimisticLockIsStaleVersion() throws Exception {
        mvc.perform(get("/probe/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void lockTimeoutIsBusyAndConnectionLossIsSaveUnavailable() throws Exception {
        mvc.perform(get("/probe/busy"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOGUE_BUSY"));
        mvc.perform(get("/probe/db-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOGUE_SAVE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-host"))));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/error/{code}")
        void error(@PathVariable String code) {
            throw new CatalogueException(CatalogueErrorCode.valueOf(code));
        }

        @GetMapping("/probe/amount/{raw}")
        String amount(@PathVariable String raw) {
            return CatalogueValues.parseAmount("proposedPrice", raw).toString();
        }

        @PostMapping("/probe/body")
        String body(@RequestHeader("Idempotency-Key") UUID key, @RequestBody StrictBody body) {
            return body.name();
        }

        @GetMapping("/probe/stale")
        void stale() {
            throw new ObjectOptimisticLockingFailureException("ProductEntity", 1L);
        }

        @GetMapping("/probe/busy")
        void busy() {
            throw new CannotAcquireLockException("lock wait timeout");
        }

        @GetMapping("/probe/db-down")
        void dbDown() {
            throw new DataAccessResourceFailureException("connection to secret-host refused");
        }
    }

    record StrictBody(String name, Long expectedVersion) {
    }
}
