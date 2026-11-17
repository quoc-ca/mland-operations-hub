package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.controller;

import com.fasterxml.jackson.databind.JsonNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueCommandExecutor;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Shared response rules of the catalogue API: every management response and every public read is
 * {@code Cache-Control: no-store} (interfaces "API conventions"); a command returns the committed result of its
 * receipt with the status it was committed with, also on a same-key replay.
 */
final class CatalogueResponses {

    private CatalogueResponses() {
    }

    static ResponseEntity<JsonNode> committed(CatalogueCommandExecutor.Outcome outcome) {
        return ResponseEntity.status(outcome.status()).cacheControl(CacheControl.noStore()).body(outcome.body());
    }

    static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    static <T> ResponseEntity<T> created(T body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }

    static long version(Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw CatalogueException.invalidField("expectedVersion", "A nonnegative expected version is required.");
        }
        return expectedVersion;
    }

    static boolean flag(String field, Boolean value) {
        if (value == null) {
            throw CatalogueException.invalidField(field, "Required.");
        }
        return value;
    }

    static <T> T required(String field, T value) {
        if (value == null) {
            throw CatalogueException.invalidField(field, "Required.");
        }
        return value;
    }
}
