package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Cloudinary implementation of {@link SettlementReceiptStoragePort} (003.3 T154, plan O-1): signed server-side
 * upload as {@code type=authenticated}, deterministic public id, never overwritten; reads use a signed URL built and
 * used only inside the backend. Every provider failure, and a missing configuration, is
 * {@code RECEIPT_STORAGE_UNAVAILABLE}; nothing provider-side (URL, key, message) is logged or returned.
 */
public class CloudinaryReceiptStorageAdapter implements SettlementReceiptStoragePort {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryReceiptStorageAdapter.class);
    private static final String RESOURCE_TYPE = "image";
    private static final String DELIVERY_TYPE = "authenticated";
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final Cloudinary cloudinary;
    private final HttpClient httpClient;
    private final String folder;

    /** {@code cloudinary} is null when the environment is not configured; every operation then fails as unavailable. */
    public CloudinaryReceiptStorageAdapter(Cloudinary cloudinary, HttpClient httpClient, String folder) {
        this.cloudinary = cloudinary;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.folder = trimSlashes(Objects.requireNonNull(folder, "folder"));
    }

    @Override
    public String storageKeyFor(UUID imageId) {
        return folder + "/" + Objects.requireNonNull(imageId, "imageId");
    }

    @Override
    @SuppressWarnings("unchecked")
    public StoredObject put(String storageKey, String contentType, byte[] bytes) {
        Cloudinary client = requireConfigured();
        Map<String, Object> result;
        try {
            result = client.uploader().upload(bytes, ObjectUtils.asMap(
                    "public_id", storageKey,
                    "resource_type", RESOURCE_TYPE,
                    "type", DELIVERY_TYPE,
                    "overwrite", false,
                    "unique_filename", false,
                    "use_filename", false));
        } catch (Exception e) {
            throw unavailable("upload", e);
        }
        if (!storageKey.equals(result.get("public_id")) || !DELIVERY_TYPE.equals(result.get("type"))
                || !RESOURCE_TYPE.equals(result.get("resource_type"))) {
            throw unavailable("upload returned an unexpected object", null);
        }
        if (!String.valueOf(result.get("etag")).equalsIgnoreCase(md5Hex(bytes))) {
            throw unavailable("upload content could not be verified", null);
        }
        Object assetId = result.get("asset_id");
        Object version = result.get("version");
        Object format = result.get("format");
        if (assetId == null || version == null || format == null) {
            throw unavailable("upload metadata is incomplete", null);
        }
        return new StoredObject(storageKey, assetId.toString(), version.toString(), format.toString());
    }

    @Override
    public Optional<InputStream> open(StoredObject object) {
        Cloudinary client = requireConfigured();
        String signedUrl = client.url()
                .resourceType(RESOURCE_TYPE)
                .type(DELIVERY_TYPE)
                .signed(true)
                .secure(true)
                .version(object.providerVersion())
                .format(object.providerFormat())
                .generate(object.storageKey());
        HttpRequest request = HttpRequest.newBuilder(URI.create(signedUrl)).timeout(READ_TIMEOUT).GET().build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("open", e);
        } catch (Exception e) {
            throw unavailable("open", e);
        }
        int status = response.statusCode();
        if (status == 200) {
            return Optional.of(response.body());
        }
        try {
            response.body().close();
        } catch (Exception ignored) {
            // the response is discarded
        }
        if (status == 404) {
            return Optional.empty();
        }
        throw unavailable("open returned HTTP " + status, null);
    }

    @Override
    public void deleteIfExists(String storageKey) {
        Cloudinary client = requireConfigured();
        Map<?, ?> result;
        try {
            result = client.uploader().destroy(storageKey, ObjectUtils.asMap(
                    "resource_type", RESOURCE_TYPE, "type", DELIVERY_TYPE, "invalidate", true));
        } catch (Exception e) {
            throw unavailable("destroy", e);
        }
        Object outcome = result.get("result");
        if (!"ok".equals(outcome) && !"not found".equals(outcome)) {
            throw unavailable("destroy returned an unexpected result", null);
        }
    }

    private Cloudinary requireConfigured() {
        if (cloudinary == null) {
            throw new SettlementReceiptException(SettlementReceiptException.Failure.STORAGE_UNAVAILABLE);
        }
        return cloudinary;
    }

    private static SettlementReceiptException unavailable(String operation, Exception cause) {
        log.warn("Receipt storage unavailable during {}{}", operation,
                cause == null ? "" : " (" + cause.getClass().getSimpleName() + ")");
        return new SettlementReceiptException(SettlementReceiptException.Failure.STORAGE_UNAVAILABLE);
    }

    private static String md5Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }

    private static String trimSlashes(String value) {
        String trimmed = value.strip();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("SETTLEMENT_RECEIPT_CLOUDINARY_FOLDER must not be empty");
        }
        return trimmed;
    }
}
