package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.application.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationListDto {
    private List<ConversationDto> items;
    private String nextCursor;
    private boolean hasMore;
}
