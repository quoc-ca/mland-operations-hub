package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountProfileResponse {
    private String fullName;
    private String phone;
}
