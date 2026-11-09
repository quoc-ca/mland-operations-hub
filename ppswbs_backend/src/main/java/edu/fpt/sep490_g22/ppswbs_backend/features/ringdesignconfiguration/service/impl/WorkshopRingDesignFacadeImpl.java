package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingDesignRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesign;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesignStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.AttachDesignRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.BookingDesignSnapshotDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.facade.WorkshopRingDesignFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.WorkshopRingDesignApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopRingDesignFacadeImpl implements WorkshopRingDesignFacade {

    private final WorkshopRingDesignApplicationService applicationService;
    private final RingDesignRepository designRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public BookingDesignSnapshotDto attachAndLockDesignForBooking(
            String designId,
            String bookingReference,
            String packageReference,
            String branchReference,
            Long memberAccountId,
            String guestSessionReference
    ) {
        AttachDesignRequestDto req = AttachDesignRequestDto.builder()
                .bookingReference(bookingReference)
                .packageReference(packageReference)
                .branchReference(branchReference)
                .build();

        String internalIdempotencyKey = "FACADE-ATTACH-" + UUID.randomUUID();
        return applicationService.attachToBooking(designId, req, memberAccountId, guestSessionReference, null, internalIdempotencyKey);
    }

    @Override
    @Transactional
    public void unlockDesignOnBookingRollback(String designId) {
        designRepository.findLatestByDesignReference(designId).ifPresent(design -> {
            if (design.getStatus() == RingDesignStatus.ATTACHED_TO_BOOKING) {
                design.setStatus(RingDesignStatus.VALIDATED);
                design.setBookingReference(null);
                designRepository.save(design);
                log.info("Rolled back design {} to VALIDATED state after booking failure", designId);
            }
        });
    }

    @Override
    @Transactional
    public void finalizeLock(String designId) {
        designRepository.findLatestByDesignReference(designId).ifPresent(design -> {
            if (design.getStatus() == RingDesignStatus.ATTACHED_TO_BOOKING) {
                design.setStatus(RingDesignStatus.LOCKED);
                designRepository.save(design);
                log.info("Locked design {} upon booking completion", designId);
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingDesignSnapshotDto> getDesignSnapshot(String designId) {
        return designRepository.findLatestByDesignReference(designId).map(design -> {
            Map<String, Object> snapshot = Collections.emptyMap();
            if (design.getComponentSnapshot() != null && !design.getComponentSnapshot().isBlank()) {
                try {
                    snapshot = objectMapper.readValue(design.getComponentSnapshot(), new TypeReference<>() {});
                } catch (Exception ignored) {}
            }
            return BookingDesignSnapshotDto.builder()
                    .designReference(design.getDesignReference())
                    .designVersion(design.getDesignVersion())
                    .packageReference(design.getPackageReference())
                    .snapshot(snapshot)
                    .build();
        });
    }
}

