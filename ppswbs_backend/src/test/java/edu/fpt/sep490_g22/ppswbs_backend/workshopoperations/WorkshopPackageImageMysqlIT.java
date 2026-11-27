package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageLifecycleTargetState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageImageInvalidException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 003.2 FR-073 on MySQL (V208): the cover image reference, its public read and its release. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
class WorkshopPackageImageMysqlIT {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private WorkshopPackageService packages;

    /** The storage provider is replaced; everything else is real. */
    @MockitoBean private MediaStoragePort storage;

    @Test
    void coverImageIsServedWhilePublishedAndReleasedOnRemoval() throws Exception {
        long manager = 73009L;
        UUID image = readyImage(manager);
        when(storage.open(any())).thenAnswer(invocation ->
                Optional.of(new ByteArrayInputStream(new byte[] {1, 2, 3})));

        var draft = packages.createDraft(request().imageMediaId(image).build(), manager, key());
        String path = "/api/v1/workshop-packages/" + draft.getPackageId() + "/image";
        mockMvc.perform(get(path)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSHOP_PACKAGE_IMAGE_NOT_FOUND"));

        packages.transition(draft.getPackageId(), PackageLifecycleTargetState.PUBLISHED, manager, key());
        mockMvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
        mockMvc.perform(get("/api/v1/workshop-packages/" + draft.getPackageId()))
                .andExpect(jsonPath("$.imageMediaId").value(image.toString()));
        mockMvc.perform(get("/api/v1/workshop-package-management/packages/" + draft.getPackageId() + "/image"))
                .andExpect(status().isUnauthorized());

        // Another Manager keeps the current image without uploading it again.
        packages.updateProspectively(draft.getPackageId(), request().imageMediaId(image).build(), 73010L, key());
        packages.updateProspectively(draft.getPackageId(), request().build(), manager, key());
        mockMvc.perform(get(path)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("""
                select count(*) from catalogue_media_references where media_object_id = ?
                    and owner_module = 'workshopoperations' and released_at is not null""",
                Integer.class, image.toString())).isEqualTo(1);
    }

    @Test
    void anotherManagersUploadIsRefusedAndNothingIsSaved() {
        UUID foreign = readyImage(73011L);
        int before = jdbc.queryForObject("select count(*) from workshop_packages", Integer.class);

        assertThatThrownBy(() -> packages.createDraft(request().imageMediaId(foreign).build(), 73012L, key()))
                .isInstanceOf(WorkshopPackageImageInvalidException.class);

        assertThat(jdbc.queryForObject("select count(*) from workshop_packages", Integer.class)).isEqualTo(before);
    }

    private UUID readyImage(long uploader) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into catalogue_media_objects (id, storage_key, provider_asset_id, provider_version,
                    provider_format, checksum, content_type, byte_count, state, uploaded_by, command_id, created_at)
                values (?, ?, 'asset', '1', 'png', ?, 'image/png', 3, 'READY', ?, ?, utc_timestamp(6))""",
                id.toString(), "pkg-test/" + id, "0".repeat(64), String.valueOf(uploader), UUID.randomUUID().toString());
        return id;
    }

    private static PackageRequest.PackageRequestBuilder request() {
        return PackageRequest.builder().name("Pictured Ring " + UUID.randomUUID()).description("Image test")
                .publicPrice(500_000L).minParticipants(1).maxParticipants(4).requiredSessionCount(1)
                .sameLocationRequired(true).minimumSameDaySlotGap(0);
    }

    private static String key() {
        return "pkg-img-" + UUID.randomUUID();
    }
}
