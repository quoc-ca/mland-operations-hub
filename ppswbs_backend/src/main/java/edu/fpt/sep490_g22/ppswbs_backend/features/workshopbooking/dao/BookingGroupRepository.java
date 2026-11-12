package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingGroupRepository extends JpaRepository<BookingGroup, Long> {
    Optional<BookingGroup> findByGroupCode(String groupCode);
}
