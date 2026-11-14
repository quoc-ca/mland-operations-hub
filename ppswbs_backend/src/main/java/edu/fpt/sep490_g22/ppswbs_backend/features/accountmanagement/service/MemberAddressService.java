package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.MemberAddressRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AddressCreateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AddressUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.AddressResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AdministrativeCommune;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AdministrativeProvince;
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
    private final AdministrativeDivisionService divisionService;

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

        validateNoIsoControl(request.recipientName(), "Recipient name");
        validateNoIsoControl(request.detailAddress(), "Detail address");
        String normalizedPhone = normalizePhoneNumber(request.phoneNumber());

        String activeVersion = divisionService != null ? divisionService.getActiveDirectoryVersion() : AdministrativeDivisionService.DEFAULT_DIRECTORY_VERSION;
        AdministrativeProvince province = divisionService != null
            ? divisionService.resolveProvince(activeVersion, request.provinceCode())
            : AdministrativeProvince.builder().code(request.provinceCode()).name(request.provinceCode()).build();
        AdministrativeCommune commune = divisionService != null
            ? divisionService.resolveCommune(activeVersion, request.provinceCode(), request.communeCode())
            : AdministrativeCommune.builder().code(request.communeCode()).name(request.communeCode()).type(edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CommuneType.WARD).build();

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
            .provinceCode(province.getCode())
            .provinceName(province.getName())
            .communeCode(commune.getCode())
            .communeName(commune.getName())
            .communeType(commune.getType())
            .detailAddress(request.detailAddress().trim())
            .directoryVersion(activeVersion)
            .addressType(request.resolvedAddressType())
            .isDefault(makeDefault)
            .version(0L)
            .build();

        MemberAddress saved = addressRepository.save(newAddress);
        log.info("Created shipping address id={} for accountId={}, isDefault={}", saved.getId(), accountId, saved.isDefault());
        return toResponse(saved);
    }

    @Transactional
    public AddressResponse updateAddress(Long accountId, Long addressId, AddressUpdateRequest request) {
        MemberAddress address = addressRepository.findByAccountIdAndId(accountId, addressId)
            .orElseThrow(() -> new AddressNotFoundException("Shipping address not found: " + addressId));

        if (request.version() != null && !request.version().equals(address.getVersion())) {
            throw new OptimisticLockConflictException("Stale optimistic lock version: expected " + request.version() + " but was " + address.getVersion());
        }

        validateNoIsoControl(request.recipientName(), "Recipient name");
        validateNoIsoControl(request.detailAddress(), "Detail address");
        String normalizedPhone = normalizePhoneNumber(request.phoneNumber());

        String activeVersion = divisionService != null ? divisionService.getActiveDirectoryVersion() : address.getDirectoryVersion();
        AdministrativeProvince province = divisionService != null
            ? divisionService.resolveProvince(activeVersion, request.provinceCode())
            : AdministrativeProvince.builder().code(request.provinceCode()).name(request.provinceCode()).build();
        AdministrativeCommune commune = divisionService != null
            ? divisionService.resolveCommune(activeVersion, request.provinceCode(), request.communeCode())
            : AdministrativeCommune.builder().code(request.communeCode()).name(request.communeCode()).type(address.getCommuneType()).build();

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
        address.setProvinceCode(province.getCode());
        address.setProvinceName(province.getName());
        address.setCommuneCode(commune.getCode());
        address.setCommuneName(commune.getName());
        address.setCommuneType(commune.getType());
        address.setDetailAddress(request.detailAddress().trim());
        address.setDirectoryVersion(activeVersion);
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

    private void validateNoIsoControl(String value, String fieldName) {
        if (value != null) {
            for (char c : value.toCharArray()) {
                if (Character.isISOControl(c)) {
                    throw new IllegalArgumentException(fieldName + " must not contain ISO control characters");
                }
            }
        }
    }

    private AddressResponse toResponse(MemberAddress entity) {
        return new AddressResponse(
            entity.getId(),
            entity.getRecipientName(),
            entity.getPhoneNumber(),
            entity.getProvinceCode(),
            entity.getProvinceName(),
            entity.getCommuneCode(),
            entity.getCommuneName(),
            entity.getCommuneType(),
            entity.getDetailAddress(),
            entity.getDirectoryVersion(),
            entity.getAddressType(),
            entity.isDefault(),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
