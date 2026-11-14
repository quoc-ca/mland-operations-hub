package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> {

    Optional<TemplateVersion> findByTemplateDefinitionIdAndStatus(Long templateDefinitionId, String status);

    Optional<TemplateVersion> findByTemplateDefinitionCodeAndStatus(String code, String status);

    Optional<TemplateVersion> findByTemplateDefinitionIdAndVersionNumber(Long templateDefinitionId, Integer versionNumber);

    Optional<TemplateVersion> findByTemplateDefinitionCodeAndVersionNumber(String code, Integer versionNumber);

    List<TemplateVersion> findByTemplateDefinitionIdOrderByVersionNumberDesc(Long templateDefinitionId);

    List<TemplateVersion> findByTemplateDefinitionCodeOrderByVersionNumberDesc(String code);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM TemplateVersion v WHERE v.templateDefinition.id = :definitionId")
    int findMaxVersionNumber(@Param("definitionId") Long definitionId);
}

