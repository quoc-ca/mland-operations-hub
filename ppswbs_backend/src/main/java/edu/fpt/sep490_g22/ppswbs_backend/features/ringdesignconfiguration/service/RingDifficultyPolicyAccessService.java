package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingDifficultyEffectivePolicyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingDifficultyOptionScoreRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao.RingDifficultyPolicyVersionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDifficultyEffectivePolicy;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDifficultyOptionScore;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingDifficultyPolicyVersion;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.RingDifficultyPolicyEvaluator.OptionKey;
import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.rules.RingDifficultyPolicyEvaluator.PolicySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RingDifficultyPolicyAccessService {

    private final RingDifficultyEffectivePolicyRepository effectivePolicyRepository;
    private final RingDifficultyPolicyVersionRepository policyVersionRepository;
    private final RingDifficultyOptionScoreRepository optionScoreRepository;

    @Transactional(readOnly = true)
    public LoadedPolicy loadEffectivePolicy() {
        RingDifficultyEffectivePolicy pointer = effectivePolicyRepository
                .findById(RingDifficultyEffectivePolicy.DEFAULT_SCOPE)
                .orElseThrow(() -> RingDesignException.rulesUnavailable(
                        "Chính sách độ khó thiết kế nhẫn hiện chưa khả dụng."));

        RingDifficultyPolicyVersion version = policyVersionRepository
                .findById(pointer.getPolicyVersionId())
                .orElseThrow(() -> RingDesignException.rulesUnavailable(
                        "Phiên bản chính sách độ khó hiện tại không tồn tại."));

        List<RingDifficultyOptionScore> scores = optionScoreRepository.findAllByPolicyVersionId(version.getId());
        return new LoadedPolicy(version, toSnapshot(version, scores));
    }

    public PolicySnapshot toSnapshot(RingDifficultyPolicyVersion version, List<RingDifficultyOptionScore> scores) {
        Map<OptionKey, BigDecimal> scoreMap = new HashMap<>();
        for (RingDifficultyOptionScore s : scores) {
            scoreMap.put(new OptionKey(s.getOptionKind(), s.getOptionReference()), s.getScore());
        }
        return new PolicySnapshot(
                version.getRulesVersion(),
                version.getEngravingBonus(),
                version.getCustomImageBonusPerImage(),
                version.getEasyMax(),
                version.getMediumMax(),
                version.getHardMax(),
                version.getVeryHardMax(),
                version.getWaxAbove(),
                version.getSilverClayGemstonesAbove(),
                Map.copyOf(scoreMap)
        );
    }

    public record LoadedPolicy(RingDifficultyPolicyVersion version, PolicySnapshot snapshot) {}
}
