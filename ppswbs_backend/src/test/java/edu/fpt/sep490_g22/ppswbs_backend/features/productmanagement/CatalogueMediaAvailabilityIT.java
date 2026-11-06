package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PublicCatalogueQueryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueMediaService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.MANAGER;
import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T073 on MySQL with a controlled provider port: an image the provider has lost removes its product from the
 * public catalogue without touching the publication flag; once readable again it is restored; a provider failure
 * is never taken as "missing" (FR-028/031/036).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
class CatalogueMediaAvailabilityIT {

    @MockitoBean private MediaStoragePort storage;
    @Autowired private MockMvc mvc;
    @Autowired private CatalogueFlowFixture fixture;
    @Autowired private CatalogueMediaService mediaService;
    @Autowired private PublicCatalogueQueryRepository publicQueries;
    @Autowired private ProductRepository products;
    @Autowired private TransactionTemplate transaction;

    @Test
    void aLostImageTakesTheProductOutOfThePublicCatalogueUntilItIsReadableAgain() throws Exception {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        UUID image = fixture.images(productId).get(0).getMediaObjectId();
        when(storage.open(any())).thenReturn(Optional.empty());
        when(storage.verifyReadable(any())).thenReturn(MediaStoragePort.Availability.MISSING);

        mvc.perform(get("/api/v1/product-media/" + image))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));

        assertThat(fixture.media(image).getState()).isEqualTo(CatalogueMediaObject.State.UNAVAILABLE);
        assertThat(eligible(productId)).isFalse();
        Boolean published = transaction.execute(status -> products.findById(productId).orElseThrow().isPublished());
        assertThat(published).as("the publication flag is not changed").isTrue();

        when(storage.verifyReadable(any())).thenReturn(MediaStoragePort.Availability.READABLE);
        mediaService.reverifyUnavailable();

        assertThat(fixture.media(image).getState()).isEqualTo(CatalogueMediaObject.State.READY);
        assertThat(eligible(productId)).isTrue();
    }

    @Test
    void aProviderFailureChangesNothing() {
        long productId = fixture.publishedProduct(STAFF, MANAGER);
        UUID image = fixture.images(productId).get(0).getMediaObjectId();
        when(storage.verifyReadable(any())).thenThrow(new CatalogueException(CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE));

        assertThatThrownBy(() -> mediaService.verify(image))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE));
        assertThat(fixture.media(image).getState()).isEqualTo(CatalogueMediaObject.State.READY);
        assertThat(eligible(productId)).isTrue();
    }

    private boolean eligible(long productId) {
        List<Long> ids = transaction.execute(status -> publicQueries.findEligibleIds(List.of(productId)));
        return ids != null && ids.contains(productId);
    }
}
