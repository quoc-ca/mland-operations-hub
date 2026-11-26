package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingDesignRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesign;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesignStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDesignOwnerType;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.AttachDesignRequestDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto.BookingDesignSnapshotDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.facade.WorkshopRingDesignConsultationOption;
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
            String guestSessionReference) {
        AttachDesignRequestDto req = AttachDesignRequestDto.builder()
                .bookingReference(bookingReference)
                .packageReference(packageReference)
                .branchReference(branchReference)
                .build();

        String internalIdempotencyKey = "FACADE-ATTACH-" + UUID.randomUUID();
        return applicationService.attachToBooking(designId, req, memberAccountId, guestSessionReference, null,
                internalIdempotencyKey);
    }

    @Override
    @Transactional
    public void unlockDesignOnBookingRollback(String designId) {
        designRepository.findLatestByDesignReference(designId).ifPresent(design -> {
            if (design.getStatus() == RingDesignStatus.ATTACHED_TO_BOOKING) {
                design.setStatus(RingDesignStatus.SAVED);
                design.setBookingReference(null);
                designRepository.save(design);
                log.info("Rolled back design {} to SAVED state after booking failure", designId);
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
                    snapshot = objectMapper.readValue(design.getComponentSnapshot(), new TypeReference<>() {
                    });
                } catch (Exception ignored) {
                }
            }
            return BookingDesignSnapshotDto.builder()
                    .designReference(design.getDesignReference())
                    .designVersion(design.getDesignVersion())
                    .packageReference(design.getPackageReference())
                    .snapshot(snapshot)
                    .build();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<WorkshopRingDesignConsultationOption> findMemberDesignsForConsultation(Long memberAccountId) {
        if (memberAccountId == null)
            return Collections.emptyList();
        var list = designRepository.findAllByOwner(RingDesignOwnerType.MEMBER, String.valueOf(memberAccountId));
        if (list.isEmpty())
            return Collections.emptyList();

        Map<String, RingDesign> latest = new java.util.LinkedHashMap<>();
        for (RingDesign d : list) {
            RingDesign ex = latest.get(d.getDesignReference());
            if (ex == null || d.getDesignVersion() > ex.getDesignVersion()) {
                latest.put(d.getDesignReference(), d);
            }
        }

        return latest.values().stream().map(this::toConsultationOption).toList();
    }

    @Override
    @Transactional
    public Optional<WorkshopRingDesignConsultationOption> validateMemberDesignForConsultation(
            Long memberAccountId, String designReference, Integer designVersion
    ) {
        if (memberAccountId == null || designReference == null || designReference.isBlank()) {
            return Optional.empty();
        }
        Optional<RingDesign> opt;
        if (designVersion != null) {
            opt = designRepository.findByDesignReferenceAndDesignVersion(designReference.trim(), designVersion);
        } else {
            opt = designRepository.findLatestByDesignReference(designReference.trim());
        }
        if (opt.isEmpty()) return Optional.empty();
        RingDesign d = opt.get();
        if (d.getOwnerType() != RingDesignOwnerType.MEMBER || !String.valueOf(memberAccountId).equals(d.getOwnerReference())) {
            return Optional.empty();
        }
        return Optional.of(toConsultationOption(d));
    }

    private WorkshopRingDesignConsultationOption toConsultationOption(RingDesign d) {
        String code = d.getDesignReference();
        String name = d.getDesignName() != null && !d.getDesignName().isBlank() ? d.getDesignName()
                : "Thiết kế nhẫn tùy chỉnh";
        String summary = String.format("Chất liệu: %s, Dáng: %s, Kích thước: %s",
                d.getMaterialReference() != null ? d.getMaterialReference() : "Tiêu chuẩn",
                d.getShapeReference() != null ? d.getShapeReference() : "Tiêu chuẩn",
                d.getRingSize() != null ? d.getRingSize() : "Mặc định");
        String imageUrl = null;
        if (d.getComponentSnapshot() != null && !d.getComponentSnapshot().isBlank()) {
            try {
                Map<String, Object> snap = objectMapper.readValue(d.getComponentSnapshot(), new TypeReference<>() {
                });
                if (snap.get("previewImageUrl") != null) {
                    imageUrl = snap.get("previewImageUrl").toString();
                } else if (snap.get("thumbnailUrl") != null) {
                    imageUrl = snap.get("thumbnailUrl").toString();
                }
            } catch (Exception ignored) {
            }
        }
        return new WorkshopRingDesignConsultationOption(
                d.getDesignReference(),
                code,
                name,
                d.getDesignVersion(),
                imageUrl,
                summary);
    }
}
