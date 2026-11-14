package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AdministrativeProvince;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdministrativeProvinceRepository extends JpaRepository<AdministrativeProvince, Long> {

    List<AdministrativeProvince> findAllByDirectoryVersionOrderByNameAsc(String directoryVersion);

    Optional<AdministrativeProvince> findByDirectoryVersionAndCode(String directoryVersion, String code);
}
