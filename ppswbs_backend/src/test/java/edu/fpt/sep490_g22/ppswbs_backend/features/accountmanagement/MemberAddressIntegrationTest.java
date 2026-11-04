package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.MemberAddressRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AddressCreateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.request.AddressUpdateRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountId;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AddressType;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.MemberAddressSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.TokenVerificationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service.VerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class MemberAddressIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TokenVerificationService tokenVerificationService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountProfileRepository profileRepository;

    @Autowired
    private MemberAddressRepository addressRepository;

    @Autowired
    private AccountFacade accountFacade;

    private Account testMember;
    private String token;

    @BeforeEach
    void setUp() {
        addressRepository.deleteAll();
        profileRepository.deleteAll();
        accountRepository.deleteAll();

        String uid = "uid-" + UUID.randomUUID();
        token = "token-" + uid;

        testMember = accountRepository.save(Account.builder()
            .firebaseUid(uid)
            .loginEmail("member@test.com")
            .role(AccountRole.MEMBER)
            .status(AccountStatus.ACTIVE)
            .build());

        when(tokenVerificationService.verify(eq(token)))
            .thenReturn(new VerifiedIdentity(uid, "member@test.com", true, Instant.now()));
    }

    @Test
    @DisplayName("US6: First address automatically becomes default, phone number normalized")
    void testFirstAddressAutoDefaultAndPhoneNormalization() throws Exception {
        AddressCreateRequest request = new AddressCreateRequest(
            "Nguyen Van A",
            "+84 901 234 567",
            "HCM",
            "Thành phố Hồ Chí Minh",
            "Q1",
            "Quận 1",
            "PBN",
            "Phường Bến Nghé",
            null,
            null,
            "123 Le Duan",
            AddressType.HOME,
            false // Even if false, first address becomes default
        );

        mockMvc.perform(post("/api/v1/accounts/me/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.recipientName").value("Nguyen Van A"))
            .andExpect(jsonPath("$.phoneNumber").value("0901234567"))
            .andExpect(jsonPath("$.provinceName").value("Thành phố Hồ Chí Minh"))
            .andExpect(jsonPath("$.districtName").value("Quận 1"))
            .andExpect(jsonPath("$.wardName").value("Phường Bến Nghé"))
            .andExpect(jsonPath("$.isDefault").value(true))
            .andExpect(jsonPath("$.addressType").value("HOME"));

        assertThat(addressRepository.countByAccountId(testMember.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("US6: Default swapping when a new default address is added")
    void testDefaultSwappingOnAdd() throws Exception {
        // Address 1 (auto default)
        AddressCreateRequest req1 = new AddressCreateRequest(
            "Nguyen Van A", "0901234567", "HCM", "TP HCM", "Q1", "Quận 1", "PBN", "Bến Nghé",
            null, null, "123 Le Duan", AddressType.HOME, false
        );
        mockMvc.perform(post("/api/v1/accounts/me/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isDefault").value(true));

        // Address 2 (set default = true explicitly)
        AddressCreateRequest req2 = new AddressCreateRequest(
            "Nguyen Van B", "0909888777", "HN", "Hà Nội", "HK", "Hoàn Kiếm", "PHB", "Hàng Bạc",
            null, null, "456 Trang Tien", AddressType.OFFICE, true
        );
        mockMvc.perform(post("/api/v1/accounts/me/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isDefault").value(true));

        // Verify address 1 is now isDefault = false, address 2 is true
        mockMvc.perform(get("/api/v1/accounts/me/addresses/default")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipientName").value("Nguyen Van B"))
            .andExpect(jsonPath("$.phoneNumber").value("0909888777"));

        // Verify list returns default first
        mockMvc.perform(get("/api/v1/accounts/me/addresses")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].recipientName").value("Nguyen Van B"))
            .andExpect(jsonPath("$[0].isDefault").value(true))
            .andExpect(jsonPath("$[1].recipientName").value("Nguyen Van A"))
            .andExpect(jsonPath("$[1].isDefault").value(false));
    }

    @Test
    @DisplayName("US6: Quota limit rejection at 10 addresses (409 ADDRESS_LIMIT_EXCEEDED)")
    void testQuotaLimitRejection() throws Exception {
        for (int i = 1; i <= 10; i++) {
            AddressCreateRequest req = new AddressCreateRequest(
                "Recipient " + i, "090123456" + (i % 10), "HCM", "TP HCM", "Q1", "Quận 1", "PBN", "Bến Nghé",
                null, null, "Detail " + i, AddressType.HOME, false
            );
            mockMvc.perform(post("/api/v1/accounts/me/addresses")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
        }

        assertThat(addressRepository.countByAccountId(testMember.getId())).isEqualTo(10);

        // 11th address should fail with 409
        AddressCreateRequest req11 = new AddressCreateRequest(
            "Recipient 11", "0901234567", "HCM", "TP HCM", "Q1", "Quận 1", "PBN", "Bến Nghé",
            null, null, "Detail 11", AddressType.HOME, false
        );
        mockMvc.perform(post("/api/v1/accounts/me/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req11)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("US6: Delete protection: cannot delete default address when others exist")
    void testDeleteProtectionForDefaultAddress() throws Exception {
        // Create 2 addresses
        AddressCreateRequest req1 = new AddressCreateRequest(
            "Address 1", "0901234567", "HCM", "TP HCM", "Q1", "Quận 1", "PBN", "Bến Nghé",
            null, null, "Detail 1", AddressType.HOME, false
        );
        String res1Str = mockMvc.perform(post("/api/v1/accounts/me/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long id1 = objectMapper.readTree(res1Str).get("id").asLong();

        AddressCreateRequest req2 = new AddressCreateRequest(
            "Address 2", "0901234568", "HCM", "TP HCM", "Q1", "Quận 1", "PBN", "Bến Nghé",
            null, null, "Detail 2", AddressType.OFFICE, false
        );
        String res2Str = mockMvc.perform(post("/api/v1/accounts/me/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long id2 = objectMapper.readTree(res2Str).get("id").asLong();

        // Address 1 is default. Try to delete address 1 -> 409 Conflict
        mockMvc.perform(delete("/api/v1/accounts/me/addresses/" + id1)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DEFAULT_ADDRESS_CANNOT_BE_DELETED"));

        // Delete non-default address 2 -> 204 No Content
        mockMvc.perform(delete("/api/v1/accounts/me/addresses/" + id2)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        assertThat(addressRepository.findById(id2)).isEmpty();

        // Now address 1 is the only remaining address (count = 1). Deleting it succeeds!
        mockMvc.perform(delete("/api/v1/accounts/me/addresses/" + id1)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        assertThat(addressRepository.countByAccountId(testMember.getId())).isEqualTo(0);
    }

    @Test
    @DisplayName("US6: Set default address via PATCH /{addressId}/default")
    void testSetDefaultAddressViaPatch() throws Exception {
        AddressCreateRequest req1 = new AddressCreateRequest(
            "Address 1", "0901234567", "HCM", "TP HCM", "Q1", "Quận 1", "PBN", "Bến Nghé",
            null, null, "Detail 1", AddressType.HOME, false
        );
        String res1Str = mockMvc.perform(post("/api/v1/accounts/me/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long id1 = objectMapper.readTree(res1Str).get("id").asLong();

        AddressCreateRequest req2 = new AddressCreateRequest(
            "Address 2", "0901234568", "HCM", "TP HCM", "Q1", "Quận 1", "PBN", "Bến Nghé",
            null, null, "Detail 2", AddressType.OFFICE, false
        );
        String res2Str = mockMvc.perform(post("/api/v1/accounts/me/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long id2 = objectMapper.readTree(res2Str).get("id").asLong();

        // Currently id1 is default. Set id2 as default via PATCH
        mockMvc.perform(patch("/api/v1/accounts/me/addresses/" + id2 + "/default")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id2))
            .andExpect(jsonPath("$.isDefault").value(true));

        // Check id1 is no longer default
        assertThat(addressRepository.findById(id1).get().isDefault()).isFalse();
        assertThat(addressRepository.findById(id2).get().isDefault()).isTrue();
    }

    @Test
    @DisplayName("US6: Public administrative division cascading lookup")
    void testAdministrativeDivisionLookup() throws Exception {
        // Provinces (no auth required)
        mockMvc.perform(get("/api/v1/addresses/provinces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == 'HCM')].name").value("Thành phố Hồ Chí Minh"))
            .andExpect(jsonPath("$[?(@.id == 'HN')].name").value("Thành phố Hà Nội"));

        // Districts for HCM
        mockMvc.perform(get("/api/v1/addresses/districts").param("provinceId", "HCM"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == 'Q1')].name").value("Quận 1"))
            .andExpect(jsonPath("$[?(@.id == 'TD')].name").value("Thành phố Thủ Đức"));

        // Wards for Q1
        mockMvc.perform(get("/api/v1/addresses/wards").param("districtId", "Q1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == 'PBN')].name").value("Phường Bến Nghé"))
            .andExpect(jsonPath("$[?(@.id == 'PBT')].name").value("Phường Bến Thành"));
    }

    @Test
    @DisplayName("US6 / WF-10: AccountFacade shipping address snapshot for FE-008")
    void testAccountFacadeShippingAddressSnapshot() throws Exception {
        AddressCreateRequest req = new AddressCreateRequest(
            "Le Thi C", "0987654321", "HCM", "Thành phố Hồ Chí Minh", "Q1", "Quận 1", "PBN", "Phường Bến Nghé",
            null, null, "789 Dong Khoi", AddressType.OFFICE, true
        );
        String resStr = mockMvc.perform(post("/api/v1/accounts/me/addresses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long addressId = objectMapper.readTree(resStr).get("id").asLong();

        // 1. Facade findDefaultShippingAddress
        Optional<MemberAddressSnapshot> defaultOpt = accountFacade.findDefaultShippingAddress(AccountId.of(testMember.getId()));
        assertThat(defaultOpt).isPresent();
        MemberAddressSnapshot snapshot1 = defaultOpt.get();
        assertThat(snapshot1.recipientName()).isEqualTo("Le Thi C");
        assertThat(snapshot1.phoneNumber()).isEqualTo("0987654321");
        assertThat(snapshot1.detailAddress()).isEqualTo("789 Dong Khoi");
        assertThat(snapshot1.addressType()).isEqualTo(AddressType.OFFICE);

        // 2. Facade findShippingAddressById
        Optional<MemberAddressSnapshot> byIdOpt = accountFacade.findShippingAddressById(AccountId.of(testMember.getId()), addressId);
        assertThat(byIdOpt).isPresent();
        assertThat(byIdOpt.get().recipientName()).isEqualTo("Le Thi C");

        // 3. Immutability verification: mutate the address in DB/API
        AddressUpdateRequest updateReq = new AddressUpdateRequest(
            "Le Thi C Updated", "0987654321", "HCM", "Thành phố Hồ Chí Minh", "Q1", "Quận 1", "PBN", "Phường Bến Nghé",
            null, null, "New Street 999", AddressType.HOME, true
        );
        mockMvc.perform(put("/api/v1/accounts/me/addresses/" + addressId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isOk());

        // Snapshot recorded earlier remains untouched
        assertThat(snapshot1.recipientName()).isEqualTo("Le Thi C");
        assertThat(snapshot1.detailAddress()).isEqualTo("789 Dong Khoi");
        assertThat(snapshot1.addressType()).isEqualTo(AddressType.OFFICE);
    }
}

