package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.MemberAddressRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AddressCreateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AddressUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.AddressResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.MemberAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberAddressService {

    public static final int MAX_ADDRESS_LIMIT = 10;

    private final MemberAddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<AddressResponse> getOwnAddresses(Long accountId) {
        return addressRepository.findAllByAccountIdOrderByIsDefaultDescUpdatedAtDesc(accountId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public AddressResponse getDefaultAddress(Long accountId) {
        return addressRepository.findByAccountIdAndIsDefaultTrue(accountId)
            .map(this::toResponse)
            .orElseThrow(() -> new AddressNotFoundException("Default shipping address not found"));
    }

    @Transactional
    public AddressResponse createAddress(Long accountId, AddressCreateRequest request) {
        long currentCount = addressRepository.countByAccountId(accountId);
        if (currentCount >= MAX_ADDRESS_LIMIT) {
            throw new AddressLimitExceededException("Cannot exceed maximum quota of " + MAX_ADDRESS_LIMIT + " shipping addresses");
        }

        String normalizedPhone = normalizePhoneNumber(request.phoneNumber());
        boolean makeDefault = currentCount == 0 || request.resolvedIsDefault();

        if (makeDefault) {
            addressRepository.findByAccountIdAndIsDefaultTrue(accountId).ifPresent(existingDefault -> {
                existingDefault.setDefault(false);
                addressRepository.save(existingDefault);
            });
        }

        MemberAddress newAddress = MemberAddress.builder()
            .accountId(accountId)
            .recipientName(request.recipientName().trim())
            .phoneNumber(normalizedPhone)
            .provinceId(request.provinceId() != null ? request.provinceId().trim() : null)
            .provinceName(request.provinceName().trim())
            .districtId(request.districtId() != null ? request.districtId().trim() : null)
            .districtName(request.districtName().trim())
            .wardId(request.wardId() != null ? request.wardId().trim() : null)
            .wardName(request.wardName().trim())
            .hamletId(request.hamletId() != null && !request.hamletId().isBlank() ? request.hamletId().trim() : null)
            .hamletName(request.hamletName() != null && !request.hamletName().isBlank() ? request.hamletName().trim() : null)
            .detailAddress(request.detailAddress().trim())
            .addressType(request.resolvedAddressType())
            .isDefault(makeDefault)
            .build();

        MemberAddress saved = addressRepository.save(newAddress);
        log.info("Created shipping address id={} for accountId={}, isDefault={}", saved.getId(), accountId, saved.isDefault());
        return toResponse(saved);
    }

    @Transactional
    public AddressResponse updateAddress(Long accountId, Long addressId, AddressUpdateRequest request) {
        MemberAddress address = addressRepository.findByAccountIdAndId(accountId, addressId)
            .orElseThrow(() -> new AddressNotFoundException("Shipping address not found: " + addressId));

        String normalizedPhone = normalizePhoneNumber(request.phoneNumber());
        long currentCount = addressRepository.countByAccountId(accountId);

        boolean wantsDefault = request.resolvedIsDefault();
        if (wantsDefault && !address.isDefault()) {
            addressRepository.findByAccountIdAndIsDefaultTrue(accountId).ifPresent(existingDefault -> {
                if (!existingDefault.getId().equals(address.getId())) {
                    existingDefault.setDefault(false);
                    addressRepository.save(existingDefault);
                }
            });
            address.setDefault(true);
        } else if (!wantsDefault && address.isDefault() && currentCount == 1) {
            // Exactly one address must remain default
            address.setDefault(true);
        }

        address.setRecipientName(request.recipientName().trim());
        address.setPhoneNumber(normalizedPhone);
        address.setProvinceId(request.provinceId() != null ? request.provinceId().trim() : null);
        address.setProvinceName(request.provinceName().trim());
        address.setDistrictId(request.districtId() != null ? request.districtId().trim() : null);
        address.setDistrictName(request.districtName().trim());
        address.setWardId(request.wardId() != null ? request.wardId().trim() : null);
        address.setWardName(request.wardName().trim());
        address.setHamletId(request.hamletId() != null && !request.hamletId().isBlank() ? request.hamletId().trim() : null);
        address.setHamletName(request.hamletName() != null && !request.hamletName().isBlank() ? request.hamletName().trim() : null);
        address.setDetailAddress(request.detailAddress().trim());
        if (request.addressType() != null) {
            address.setAddressType(request.addressType());
        }

        MemberAddress saved = addressRepository.save(address);
        log.info("Updated shipping address id={} for accountId={}", saved.getId(), accountId);
        return toResponse(saved);
    }

    @Transactional
    public void deleteAddress(Long accountId, Long addressId) {
        MemberAddress address = addressRepository.findByAccountIdAndId(accountId, addressId)
            .orElseThrow(() -> new AddressNotFoundException("Shipping address not found: " + addressId));

        long currentCount = addressRepository.countByAccountId(accountId);
        if (address.isDefault() && currentCount > 1) {
            throw new DefaultAddressDeletionException("Cannot delete default address while other addresses exist. Please set another default address first.");
        }

        addressRepository.delete(address);
        log.info("Hard deleted shipping address id={} for accountId={}", addressId, accountId);
    }

    @Transactional
    public AddressResponse setDefaultAddress(Long accountId, Long addressId) {
        MemberAddress address = addressRepository.findByAccountIdAndId(accountId, addressId)
            .orElseThrow(() -> new AddressNotFoundException("Shipping address not found: " + addressId));

        if (!address.isDefault()) {
            addressRepository.findByAccountIdAndIsDefaultTrue(accountId).ifPresent(existingDefault -> {
                existingDefault.setDefault(false);
                addressRepository.save(existingDefault);
            });
            address.setDefault(true);
            address = addressRepository.save(address);
            log.info("Swapped default shipping address to id={} for accountId={}", address.getId(), accountId);
        }

        return toResponse(address);
    }

    public String normalizePhoneNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidPhoneNumberException("Phone number is required");
        }
        String cleaned = raw.replaceAll("[\\s\\-\\.\\(\\)]+", "");
        if (cleaned.startsWith("+84")) {
            cleaned = "0" + cleaned.substring(3);
        } else if (cleaned.startsWith("84") && cleaned.length() == 11) {
            cleaned = "0" + cleaned.substring(2);
        }
        if (!cleaned.matches("^0[35789]\\d{8}$")) {
            throw new InvalidPhoneNumberException("Phone number must be a valid 10-digit Vietnamese mobile number (e.g. 0901234567)");
        }
        return cleaned;
    }

    private AddressResponse toResponse(MemberAddress entity) {
        return new AddressResponse(
            entity.getId(),
            entity.getRecipientName(),
            entity.getPhoneNumber(),
            entity.getProvinceId(),
            entity.getProvinceName(),
            entity.getDistrictId(),
            entity.getDistrictName(),
            entity.getWardId(),
            entity.getWardName(),
            entity.getHamletId(),
            entity.getHamletName(),
            entity.getDetailAddress(),
            entity.getAddressType(),
            entity.isDefault(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}

