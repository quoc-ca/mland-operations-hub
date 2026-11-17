package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProfileResponse extends AccountResponse {
    private String fullName;
    private String phone;
}
