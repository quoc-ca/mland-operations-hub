package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.MemberAddressRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AddressCreateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.AddressResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.MemberAddress;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AddressType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberAddressServiceTest {

    @Mock
    private MemberAddressRepository addressRepository;

    @InjectMocks
    private MemberAddressService memberAddressService;

    @Test
    @DisplayName("UTC-001-004: normalizePhoneNumber handles international patterns and enforces VN mobile regex boundary")
    void testPhoneNumberNormalization_BoundaryPatterns() {
        // Valid Vietnamese mobile numbers with delimiters and prefixes
        assertThat(memberAddressService.normalizePhoneNumber("+84 912 345 678")).isEqualTo("0912345678");
        assertThat(memberAddressService.normalizePhoneNumber("84912345678")).isEqualTo("0912345678");
        assertThat(memberAddressService.normalizePhoneNumber("0912-345-678")).isEqualTo("0912345678");
        assertThat(memberAddressService.normalizePhoneNumber("0912.345.678")).isEqualTo("0912345678");
        assertThat(memberAddressService.normalizePhoneNumber("  0912345678  ")).isEqualTo("0912345678");

        // Invalid patterns: landline, short, long, non-mobile prefix, alpha chars, blank/null
        assertThatThrownBy(() -> memberAddressService.normalizePhoneNumber("0241234567"))
            .isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> memberAddressService.normalizePhoneNumber("091234567"))
            .isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> memberAddressService.normalizePhoneNumber("09123456789"))
            .isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> memberAddressService.normalizePhoneNumber("0412345678"))
            .isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> memberAddressService.normalizePhoneNumber("091234567a"))
            .isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> memberAddressService.normalizePhoneNumber(null))
            .isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> memberAddressService.normalizePhoneNumber("   "))
            .isInstanceOf(InvalidPhoneNumberException.class);
    }

    @Test
    @DisplayName("UTC-001-005: Enforce default address invariants, quota max 10, and delete default protection")
    void testAddressInvariants_DefaultAndQuota() {
        Long accountId = 99L;
        AddressCreateRequest createReq = new AddressCreateRequest(
            "Nguyen Van A",
            "0912345678",
            "HN",
            "Hà Nội",
            "CG",
            "Cầu Giấy",
            "DV",
            "Dịch Vọng",
            null,
            null,
            "123 Cau Giay",
            AddressType.HOME,
            false // requests isDefault=false, but first address must auto-default
        );

        // Action 1: First address auto-becomes default
        when(addressRepository.countByAccountId(accountId)).thenReturn(0L);
        when(addressRepository.save(any(MemberAddress.class))).thenAnswer(inv -> {
            MemberAddress a = inv.getArgument(0);
            return MemberAddress.builder()
                .id(1L)
                .accountId(a.getAccountId())
                .recipientName(a.getRecipientName())
                .phoneNumber(a.getPhoneNumber())
                .provinceId(a.getProvinceId())
                .provinceName(a.getProvinceName())
                .districtId(a.getDistrictId())
                .districtName(a.getDistrictName())
                .wardId(a.getWardId())
                .wardName(a.getWardName())
                .detailAddress(a.getDetailAddress())
                .addressType(a.getAddressType())
                .isDefault(a.isDefault())
                .build();
        });

        AddressResponse firstResponse = memberAddressService.createAddress(accountId, createReq);
        assertThat(firstResponse.isDefault()).isTrue();

        // Action 2: Exceeding max quota (10 addresses) throws AddressLimitExceededException
        when(addressRepository.countByAccountId(accountId)).thenReturn(10L);
        assertThatThrownBy(() -> memberAddressService.createAddress(accountId, createReq))
            .isInstanceOf(AddressLimitExceededException.class);

        // Action 3: Deleting default address when count > 1 throws DefaultAddressDeletionException
        MemberAddress defaultAddress = MemberAddress.builder()
            .id(2L)
            .accountId(accountId)
            .isDefault(true)
            .build();
        when(addressRepository.findByAccountIdAndId(accountId, 2L)).thenReturn(Optional.of(defaultAddress));
        when(addressRepository.countByAccountId(accountId)).thenReturn(2L);

        assertThatThrownBy(() -> memberAddressService.deleteAddress(accountId, 2L))
            .isInstanceOf(DefaultAddressDeletionException.class);

        // Action 4: Deleting default address when it is the sole remaining address (count == 1) succeeds
        when(addressRepository.countByAccountId(accountId)).thenReturn(1L);
        assertDoesNotThrow(() -> memberAddressService.deleteAddress(accountId, 2L));
        verify(addressRepository).delete(defaultAddress);
    }
}
