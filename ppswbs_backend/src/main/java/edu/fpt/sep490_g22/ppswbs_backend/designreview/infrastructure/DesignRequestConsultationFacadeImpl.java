package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.DesignRequestImage;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.TemporaryImageReference;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.facade.DesignRequestConsultationFacade;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.facade.DesignRequestConsultationOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DesignRequestConsultationFacadeImpl implements DesignRequestConsultationFacade {

    private final DesignRequestRepository designRequestRepository;
    private final TemporaryImageReferenceRepository imageReferenceRepository;
    private final DesignRequestImageRepository designRequestImageRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DesignRequestConsultationOption> findMemberRequestsForConsultation(Long memberAccountId) {
        if (memberAccountId == null)
            return Collections.emptyList();
        List<DesignRequest> requests = designRequestRepository
                .findByOwnerAccountIdOrderByCreatedAtDesc(memberAccountId);
        if (requests.isEmpty())
            return Collections.emptyList();

        Map<String, DesignRequest> latest = new LinkedHashMap<>();
        for (DesignRequest r : requests) {
            if ("WITHDRAWN".equalsIgnoreCase(r.getRequestStatus())
                    || "ARCHIVED".equalsIgnoreCase(r.getRequestStatus())) {
                continue;
            }
            DesignRequest ex = latest.get(r.getRequestReference());
            if (ex == null || r.getRequestVersion() > ex.getRequestVersion()) {
                latest.put(r.getRequestReference(), r);
            }
        }

        return latest.values().stream().map(this::toConsultationOption).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DesignRequestConsultationOption> validateMemberRequestForConsultation(
            Long memberAccountId, String requestReference, Integer requestVersion) {
        if (memberAccountId == null || requestReference == null || requestReference.isBlank()) {
            return Optional.empty();
        }
        Optional<DesignRequest> opt = designRequestRepository.findByRequestReference(requestReference.trim());
        if (opt.isEmpty())
            return Optional.empty();
        DesignRequest r = opt.get();
        if ("WITHDRAWN".equalsIgnoreCase(r.getRequestStatus()) || "ARCHIVED".equalsIgnoreCase(r.getRequestStatus())) {
            return Optional.empty();
        }
        if (!memberAccountId.equals(r.getOwnerAccountId())) {
            return Optional.empty();
        }
        if (requestVersion != null && !requestVersion.equals(r.getRequestVersion())) {
            return Optional.empty();
        }
        return Optional.of(toConsultationOption(r));
    }

    private DesignRequestConsultationOption toConsultationOption(DesignRequest r) {
        String code = r.getRequestReference();
        String name = r.getCatalogueReference() != null && !r.getCatalogueReference().isBlank()
                ? "Yêu cầu thiết kế: " + r.getCatalogueReference()
                : "Yêu cầu thiết kế tự do";
        String summary = r.getDescription() != null && !r.getDescription().isBlank()
                ? (r.getDescription().length() > 200 ? r.getDescription().substring(0, 197) + "..."
                        : r.getDescription())
                : "Mô tả yêu cầu thiết kế";
        String imageUrl = null;
        List<DesignRequestImage> images = designRequestImageRepository.findByDesignRequestId(r.getId());
        if (images != null && !images.isEmpty()) {
            imageUrl = "/api/v1/design-requests/images/" + images.get(0).getImageReference();
        }

        return new DesignRequestConsultationOption(
                r.getRequestReference(),
                code,
                name,
                r.getRequestVersion(),
                imageUrl,
                summary);
    }
}
