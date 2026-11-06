package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.exceptions.NotFound;
import com.cloudinary.utils.ObjectUtils;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort;
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
 * Cloudinary implementation of {@link MediaStoragePort} (research D-06).
 *
 * <ul>
 *   <li>Signed server-side upload, {@code resource_type=image}, {@code type=authenticated}, deterministic
 *       {@code public_id} and {@code overwrite=false}. The provider's MD5 {@code etag} is compared with the
 *       content so a retry is recognised and different content under the same key is a conflict.</li>
 *   <li>Objects stay authenticated after publication. Reads use a signed delivery URL generated and used
 *       only inside the backend; the URL, API secret and response bodies are never logged or returned.</li>
 *   <li>Delete is a signed destroy of exactly this public_id/resource_type/type; "not found" is success.</li>
 * </ul>
 * Every provider failure that is not a definite result maps to {@code MEDIA_DEPENDENCY_UNAVAILABLE}.
 */
public class CloudinaryMediaStorageAdapter implements MediaStoragePort {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryMediaStorageAdapter.class);

    private static final String RESOURCE_TYPE = "image";
    private static final String DELIVERY_TYPE = "authenticated";
    private static final Map<String, String> FORMAT_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final Cloudinary cloudinary;
    private final HttpClient httpClient;
    private final String folder;

    /** {@code cloudinary} is null when the environment is not configured; operations then fail as unavailable. */
    public CloudinaryMediaStorageAdapter(Cloudinary cloudinary, HttpClient httpClient, String folder) {
        this.cloudinary = cloudinary;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.folder = trimSlashes(Objects.requireNonNull(folder, "folder"));
    }

    @Override
    public String storageKeyFor(UUID mediaId) {
        return folder + "/" + Objects.requireNonNull(mediaId, "mediaId");
    }

    @Override
    @SuppressWarnings("unchecked")
    public StoredObject putIfAbsent(String storageKey, VerifiedContent content) {
        Cloudinary client = requireConfigured();
        String format = FORMAT_BY_CONTENT_TYPE.get(content.contentType());
        if (format == null) {
            throw new CatalogueException(CatalogueErrorCode.MEDIA_TYPE_UNSUPPORTED);
        }
        Map<String, Object> result;
        try {
            result = client.uploader().upload(content.bytes(), ObjectUtils.asMap(
                    "public_id", storageKey,
                    "resource_type", RESOURCE_TYPE,
                    "type", DELIVERY_TYPE,
                    "overwrite", false,
                    "unique_filename", false,
                    "use_filename", false));
        } catch (Exception e) {
            throw unavailable("upload", e);
        }
        if (!storageKey.equals(result.get("public_id"))
                || !DELIVERY_TYPE.equals(result.get("type"))
                || !RESOURCE_TYPE.equals(result.get("resource_type"))) {
            throw unavailable("upload returned an unexpected object", null);
        }
        String etag = String.valueOf(result.get("etag"));
        if (!etag.equalsIgnoreCase(md5Hex(content.bytes()))) {
            if (Boolean.TRUE.equals(result.get("existing"))) {
                throw new CatalogueException(CatalogueErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            throw unavailable("upload content could not be verified", null);
        }
        Object assetId = result.get("asset_id");
        Object version = result.get("version");
        Object returnedFormat = result.get("format");
        if (assetId == null || version == null || returnedFormat == null) {
            throw unavailable("upload metadata is incomplete", null);
        }
        return new StoredObject(storageKey, assetId.toString(), version.toString(), returnedFormat.toString());
    }

    @Override
    public Availability verifyReadable(StoredObject object) {
        Cloudinary client = requireConfigured();
        try {
            client.api().resource(object.storageKey(), ObjectUtils.asMap(
                    "resource_type", RESOURCE_TYPE,
                    "type", DELIVERY_TYPE));
            return Availability.READABLE;
        } catch (NotFound missing) {
            return Availability.MISSING;
        } catch (Exception e) {
            throw unavailable("verify", e);
        }
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
        closeQuietly(response.body());
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
                    "resource_type", RESOURCE_TYPE,
                    "type", DELIVERY_TYPE,
                    "invalidate", true));
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
            throw new CatalogueException(CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE);
        }
        return cloudinary;
    }

    /** Logs only the operation and exception type: never the URL, key, secret or provider message. */
    private static CatalogueException unavailable(String operation, Exception cause) {
        log.warn("Cloudinary unavailable during {}{}", operation,
                cause == null ? "" : " (" + cause.getClass().getSimpleName() + ")");
        return new CatalogueException(CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE);
    }

    private static String md5Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (Exception ignored) {
            // nothing to recover; the response is discarded
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
            throw new IllegalArgumentException("CLOUDINARY_FOLDER must not be empty");
        }
        return trimmed;
    }
}
