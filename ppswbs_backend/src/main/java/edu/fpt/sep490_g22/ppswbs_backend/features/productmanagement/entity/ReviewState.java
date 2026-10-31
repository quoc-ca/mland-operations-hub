package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity;

/** States shared by product reviews and price proposals: Nháp, Chờ duyệt, Đã duyệt, Bị từ chối. */
public enum ReviewState {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED
}
