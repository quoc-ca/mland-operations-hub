package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

public interface ReferenceImageStoragePort {

    String storeTemporary(String designRequestId, String fileName, String contentType, byte[] data);

    byte[] loadTemporary(String storageKey);

    boolean deleteTemporary(String storageKey);
}
