package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dto.response.AdministrativeDivisionResponse;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AdministrativeDivisionService {

    private record Ward(String id, String name, String code) {}
    private record District(String id, String name, String code, List<Ward> wards) {}
    private record Province(String id, String name, String code, List<District> districts) {}

    private final Map<String, Province> provinces = new LinkedHashMap<>();

    public AdministrativeDivisionService() {
        initData();
    }

    public List<AdministrativeDivisionResponse> listProvinces() {
        return provinces.values().stream()
            .map(p -> new AdministrativeDivisionResponse(p.id(), p.name(), p.code()))
            .toList();
    }

    public List<AdministrativeDivisionResponse> listDistricts(String provinceId) {
        if (provinceId == null || provinceId.isBlank()) {
            return List.of();
        }
        Province province = provinces.get(provinceId.trim().toUpperCase());
        if (province == null) {
            // Also try matching by name or id case-insensitively
            province = provinces.values().stream()
                .filter(p -> p.id().equalsIgnoreCase(provinceId.trim()) || p.name().equalsIgnoreCase(provinceId.trim()))
                .findFirst()
                .orElse(null);
        }
        if (province == null) {
            return List.of();
        }
        return province.districts().stream()
            .map(d -> new AdministrativeDivisionResponse(d.id(), d.name(), d.code()))
            .toList();
    }

    public List<AdministrativeDivisionResponse> listWards(String districtId) {
        if (districtId == null || districtId.isBlank()) {
            return List.of();
        }
        String cleanDistrictId = districtId.trim();
        for (Province p : provinces.values()) {
            for (District d : p.districts()) {
                if (d.id().equalsIgnoreCase(cleanDistrictId) || d.name().equalsIgnoreCase(cleanDistrictId)) {
                    return d.wards().stream()
                        .map(w -> new AdministrativeDivisionResponse(w.id(), w.name(), w.code()))
                        .toList();
                }
            }
        }
        return List.of();
    }

    private void initData() {
        // TP. Hồ Chí Minh
        List<District> hcmDistricts = new ArrayList<>();
        hcmDistricts.add(new District("Q1", "Quận 1", "01", List.of(
            new Ward("PBN", "Phường Bến Nghé", "001"),
            new Ward("PBT", "Phường Bến Thành", "002"),
            new Ward("PDK", "Phường Đa Kao", "003"),
            new Ward("PTD", "Phường Tân Định", "004"),
            new Ward("PCOL", "Phường Cầu Ông Lãnh", "005")
        )));
        hcmDistricts.add(new District("Q3", "Quận 3", "03", List.of(
            new Ward("PVTS", "Phường Võ Thị Sáu", "010"),
            new Ward("PQ3P1", "Phường 1", "011"),
            new Ward("PQ3P2", "Phường 2", "012"),
            new Ward("PQ3P3", "Phường 3", "013")
        )));
        hcmDistricts.add(new District("Q7", "Quận 7", "07", List.of(
            new Ward("PTP", "Phường Tân Phong", "020"),
            new Ward("PTPH", "Phường Tân Phú", "021"),
            new Ward("PPM", "Phường Phú Mỹ", "022"),
            new Ward("PTQ", "Phường Tân Quy", "023")
        )));
        hcmDistricts.add(new District("QBT", "Quận Bình Thạnh", "BT", List.of(
            new Ward("PBTP1", "Phường 1", "030"),
            new Ward("PBTP2", "Phường 2", "031"),
            new Ward("PBTP15", "Phường 15", "032"),
            new Ward("PBTP25", "Phường 25", "033")
        )));
        hcmDistricts.add(new District("TD", "Thành phố Thủ Đức", "TD", List.of(
            new Ward("PTD1", "Phường Thảo Điền", "040"),
            new Ward("PAP", "Phường An Phú", "041"),
            new Ward("PLT", "Phường Linh Trung", "042"),
            new Ward("PHP", "Phường Hiệp Phú", "043")
        )));
        provinces.put("HCM", new Province("HCM", "Thành phố Hồ Chí Minh", "SG", hcmDistricts));

        // Hà Nội
        List<District> hnDistricts = new ArrayList<>();
        hnDistricts.add(new District("HK", "Quận Hoàn Kiếm", "HK", List.of(
            new Ward("PHB", "Phường Hàng Bạc", "101"),
            new Ward("PHG", "Phường Hàng Gai", "102"),
            new Ward("PTT", "Phường Tràng Tiền", "103")
        )));
        hnDistricts.add(new District("BD", "Quận Ba Đình", "BD", List.of(
            new Ward("PDB", "Phường Điện Bàn", "110"),
            new Ward("PDC", "Phường Đội Cấn", "111"),
            new Ward("PGV", "Phường Giảng Võ", "112")
        )));
        hnDistricts.add(new District("CG", "Quận Cầu Giấy", "CG", List.of(
            new Ward("PDV", "Phường Dịch Vọng", "120"),
            new Ward("PYH", "Phường Yên Hòa", "121"),
            new Ward("PTH", "Phường Trung Hòa", "122")
        )));
        hnDistricts.add(new District("DD", "Quận Đống Đa", "DD", List.of(
            new Ward("PCL", "Phường Cát Linh", "130"),
            new Ward("PLH", "Phường Láng Hạ", "131"),
            new Ward("POCD", "Phường Ô Chợ Dừa", "132")
        )));
        provinces.put("HN", new Province("HN", "Thành phố Hà Nội", "HN", hnDistricts));

        // Đà Nẵng
        List<District> dnDistricts = new ArrayList<>();
        dnDistricts.add(new District("HC", "Quận Hải Châu", "HC", List.of(
            new Ward("PTT2", "Phường Thạch Thang", "201"),
            new Ward("PHC1", "Phường Hải Châu 1", "202"),
            new Ward("PBH", "Phường Bình Hiên", "203")
        )));
        dnDistricts.add(new District("TK", "Quận Thanh Khê", "TK", List.of(
            new Ward("PVT", "Phường Vĩnh Trung", "210"),
            new Ward("PTC", "Phường Tân Chính", "211")
        )));
        dnDistricts.add(new District("ST", "Quận Sơn Trà", "ST", List.of(
            new Ward("PAHB", "Phường An Hải Bắc", "220"),
            new Ward("PPM2", "Phường Phước Mỹ", "221")
        )));
        provinces.put("DN", new Province("DN", "Thành phố Đà Nẵng", "DN", dnDistricts));

        // Bình Dương
        List<District> bdgDistricts = new ArrayList<>();
        bdgDistricts.add(new District("TDM", "Thành phố Thủ Dầu Một", "TDM", List.of(
            new Ward("PPC", "Phường Phú Cường", "301"),
            new Ward("PHT", "Phường Hiệp Thành", "302")
        )));
        bdgDistricts.add(new District("TA", "Thành phố Thuận An", "TA", List.of(
            new Ward("PLTH", "Phường Lái Thiêu", "310"),
            new Ward("PAP2", "Phường An Phú", "311")
        )));
        provinces.put("BDG", new Province("BDG", "Tỉnh Bình Dương", "BD", bdgDistricts));

        // Đồng Nai
        List<District> dnaDistricts = new ArrayList<>();
        dnaDistricts.add(new District("BH", "Thành phố Biên Hòa", "BH", List.of(
            new Ward("PTD3", "Phường Trảng Dài", "401"),
            new Ward("PTP2", "Phường Tân Phong", "402"),
            new Ward("PQT", "Phường Quyết Thắng", "403")
        )));
        provinces.put("DNA", new Province("DNA", "Tỉnh Đồng Nai", "DNA", dnaDistricts));

        // Hải Phòng
        List<District> hpDistricts = new ArrayList<>();
        hpDistricts.add(new District("HB", "Quận Hồng Bàng", "HB", List.of(
            new Ward("PHVT", "Phường Hoàng Văn Thụ", "501"),
            new Ward("PPBC", "Phường Phan Bội Châu", "502")
        )));
        hpDistricts.add(new District("NQ", "Quận Ngô Quyền", "NQ", List.of(
            new Ward("PMT", "Phường Máy Tơ", "510"),
            new Ward("PCD", "Phường Cầu Đất", "511")
        )));
        provinces.put("HP", new Province("HP", "Thành phố Hải Phòng", "HP", hpDistricts));

        // Cần Thơ
        List<District> ctDistricts = new ArrayList<>();
        ctDistricts.add(new District("NK", "Quận Ninh Kiều", "NK", List.of(
            new Ward("PTA", "Phường Tân An", "601"),
            new Ward("PAC", "Phường An Cư", "602"),
            new Ward("PXK", "Phường Xuân Khánh", "603")
        )));
        provinces.put("CT", new Province("CT", "Thành phố Cần Thơ", "CT", ctDistricts));
    }
}

