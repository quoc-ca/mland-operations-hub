package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingDesignSnapshotDto {

    private String designReference;

    private Integer designVersion;

    private String packageReference;

    private Map<String, Object> snapshot;
}

