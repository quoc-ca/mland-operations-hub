package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewImageSizeTest {

    @Test
    void acceptsNoImagesAndImagesWithinTheLimit() {
        assertDoesNotThrow(() -> ReviewApplicationService.requireImagesFit(null));
        assertDoesNotThrow(() -> ReviewApplicationService.requireImagesFit(List.of()));
        assertDoesNotThrow(() -> ReviewApplicationService.requireImagesFit(
                List.of("x".repeat(ReviewApplicationService.MAX_IMAGE_CHARS))));
    }

    @Test
    void rejectsImagesOverTheLimitAsBadRequest() {
        ReviewManagementException ex = assertThrows(ReviewManagementException.class,
                () -> ReviewApplicationService.requireImagesFit(
                        List.of("x".repeat(ReviewApplicationService.MAX_IMAGE_CHARS), "y")));
        assertEquals("IMAGES_TOO_LARGE", ex.getCode());
    }
}
