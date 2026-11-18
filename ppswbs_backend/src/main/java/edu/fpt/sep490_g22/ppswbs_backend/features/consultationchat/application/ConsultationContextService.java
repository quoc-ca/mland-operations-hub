package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto.ContextOptionDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.ConsultationContextType;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.IndependentDesignRequestContextPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.WorkshopDesignContextPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConsultationContextService {

    private final WorkshopDesignContextPort workshopDesignPort;
    private final IndependentDesignRequestContextPort independentDesignPort;

    public List<ContextOptionDto> getMemberContextOptions(Long memberAccountId) {
        if (memberAccountId == null) return List.of();

        List<ContextOptionDto> options = new ArrayList<>();
        options.addAll(workshopDesignPort.findMemberWorkshopDesigns(memberAccountId));
        options.addAll(independentDesignPort.findMemberIndependentDesignRequests(memberAccountId));
        return options;
    }

    public ContextOptionDto validateAndGetSnapshot(
            Long memberAccountId,
            ConsultationContextType contextType,
            String contextReference,
            Integer contextVersion
    ) {
        if (contextType == null || contextReference == null || contextReference.isBlank() || contextVersion == null) {
            throw ConsultationException.badRequest("CHAT_CONTEXT_INVALID", "Thông tin ngữ cảnh thiết kế không hợp lệ.");
        }

        Optional<ContextOptionDto> opt;
        if (contextType == ConsultationContextType.WORKSHOP_RING_DESIGN) {
            opt = workshopDesignPort.validateWorkshopDesign(memberAccountId, contextReference.trim(), contextVersion);
        } else if (contextType == ConsultationContextType.INDEPENDENT_DESIGN_REQUEST) {
            opt = independentDesignPort.validateIndependentDesignRequest(memberAccountId, contextReference.trim(), contextVersion);
        } else {
            throw ConsultationException.badRequest("CHAT_CONTEXT_INVALID", "Loại ngữ cảnh không được hỗ trợ.");
        }

        return opt.orElseThrow(() -> ConsultationException.badRequest(
                "CHAT_CONTEXT_INVALID",
                "Ngữ cảnh thiết kế không tồn tại, đã bị lưu trữ/hủy hoặc không thuộc quyền sở hữu của bạn."
        ));
    }
}
