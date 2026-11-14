package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.TemplateDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateDefinitionRepository extends JpaRepository<TemplateDefinition, Long> {

    Optional<TemplateDefinition> findByCode(String code);

    List<TemplateDefinition> findAllByOrderByCreatedAtDesc();
}

