package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingGroupLineRepository extends JpaRepository<BookingGroupLine, Long> {

    List<BookingGroupLine> findByGroupIdOrderByLineIndexAsc(Long groupId);
}
