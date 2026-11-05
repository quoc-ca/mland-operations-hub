package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.importing;

import java.util.List;

/** The seven template columns, in their required order (FR-040, interfaces "Quy tắc import Excel"). */
public enum ImportColumn {
    GROUP_KEY("Mã nhóm"),
    NAME("Tên sản phẩm"),
    DESCRIPTION("Mô tả"),
    CATEGORY("Danh mục"),
    SIZE("Size"),
    PROPOSED_PRICE("Giá đề xuất (VND)"),
    QUANTITY("Số lượng");

    public static final List<ImportColumn> ORDER = List.of(values());

    private final String header;

    ImportColumn(String header) {
        this.header = header;
    }

    public String header() {
        return header;
    }
}
