package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.RoleCatalogueResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.RoleEntryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class RoleCatalogueController {

    @GetMapping
    public ResponseEntity<RoleCatalogueResponse> listRoles() {
        List<RoleEntryResponse> fixedRoles = List.of(
            new RoleEntryResponse(AccountRole.MEMBER, "Khách hàng thành viên / Member"),
            new RoleEntryResponse(AccountRole.STAFF, "Nhân viên cơ sở / Staff"),
            new RoleEntryResponse(AccountRole.MANAGER, "Quản lý cơ sở / Manager"),
            new RoleEntryResponse(AccountRole.ADMIN, "Quản trị viên / Admin")
        );

        return ResponseEntity.ok(new RoleCatalogueResponse(fixedRoles));
    }
}
