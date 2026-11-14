package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReferenceImageStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class LocalStorageAdapter implements ReferenceImageStoragePort {

    // Store in-memory for fast temporary processing and deterministic cleanup
    private final Map<String, byte[]> temporaryStorage = new ConcurrentHashMap<>();

    @Override
    public String storeTemporary(String designRequestId, String fileName, String contentType, byte[] data) {
        String key = "temp/design-requests/" + designRequestId + "/" + UUID.randomUUID() + "_" + fileName;
        temporaryStorage.put(key, data);
        log.info("Stored temporary reference image: key={}, size={} bytes", key, data.length);
        return key;
    }

    @Override
    public byte[] loadTemporary(String storageKey) {
        return temporaryStorage.get(storageKey);
    }

    @Override
    public boolean deleteTemporary(String storageKey) {
        byte[] removed = temporaryStorage.remove(storageKey);
        boolean success = removed != null;
        log.info("Deleted temporary reference image: key={}, success={}", storageKey, success);
        return success;
    }
}
