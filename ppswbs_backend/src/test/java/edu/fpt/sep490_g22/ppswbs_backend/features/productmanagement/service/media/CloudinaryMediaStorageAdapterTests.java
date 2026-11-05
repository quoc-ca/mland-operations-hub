package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.media;

import com.cloudinary.Api;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.api.exceptions.NotFound;
import com.cloudinary.utils.ObjectUtils;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort.Availability;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort.StoredObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort.VerifiedContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T015: Cloudinary adapter behaviour with the SDK and HTTP client replaced by test doubles.
 * This is not provider evidence; the real authenticated upload/open/destroy check is T051.
 */
class CloudinaryMediaStorageAdapterTests {

    private static final byte[] BYTES = "fake-webp-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String KEY = "ppswbs/test/9d5f0f2e-1111-4a6e-9a3a-2b2f0f0f0f0f";
    private static final String TEST_SECRET = "test-secret-not-real";

    private Cloudinary cloudinary;
    private Uploader uploader;
    private Api api;
    private HttpClient httpClient;
    private CloudinaryMediaStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        api = mock(Api.class);
        httpClient = mock(HttpClient.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(cloudinary.api()).thenReturn(api);
        Cloudinary urlSigner = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "demo-test", "api_key", "123", "api_secret", TEST_SECRET, "secure", true));
        when(cloudinary.url()).thenAnswer(invocation -> urlSigner.url());
        adapter = new CloudinaryMediaStorageAdapter(cloudinary, httpClient, "/ppswbs/test/");
    }

    @Test
    void storageKeyIsDeterministicUnderTheConfiguredFolder() {
        UUID id = UUID.fromString("9d5f0f2e-1111-4a6e-9a3a-2b2f0f0f0f0f");
        assertThat(adapter.storageKeyFor(id)).isEqualTo(KEY).isEqualTo(adapter.storageKeyFor(id));
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadIsSignedAuthenticatedWithoutOverwrite() throws Exception {
        when(uploader.upload(any(), anyMap())).thenReturn(uploadResult(md5(BYTES), false));

        StoredObject stored = adapter.putIfAbsent(KEY, content());

        ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(uploader).upload(eq(BYTES), options.capture());
        assertThat(options.getValue())
                .containsEntry("public_id", KEY)
                .containsEntry("resource_type", "image")
                .containsEntry("type", "authenticated")
                .containsEntry("overwrite", false);
        assertThat(stored).isEqualTo(new StoredObject(KEY, "asset-1", "1700000000", "webp"));
    }

    @Test
    void retryWithSameContentReturnsTheExistingObject() throws Exception {
        when(uploader.upload(any(), anyMap())).thenReturn(uploadResult(md5(BYTES), true));
        assertThat(adapter.putIfAbsent(KEY, content()).providerAssetId()).isEqualTo("asset-1");
    }

    @Test
    void sameKeyWithDifferentContentIsAConflict() throws Exception {
        when(uploader.upload(any(), anyMap())).thenReturn(uploadResult("00000000000000000000000000000000", true));
        assertCode(() -> adapter.putIfAbsent(KEY, content()), CatalogueErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void providerFailureIsUnavailable() throws Exception {
        when(uploader.upload(any(), anyMap())).thenThrow(new IOException("timeout"));
        assertCode(() -> adapter.putIfAbsent(KEY, content()), CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void unsupportedContentTypeIsRejected() {
        assertCode(() -> adapter.putIfAbsent(KEY, new VerifiedContent(BYTES, "image/gif", "x")),
                CatalogueErrorCode.MEDIA_TYPE_UNSUPPORTED);
    }

    @Test
    void verifyDistinguishesMissingFromUnavailable() throws Exception {
        StoredObject stored = new StoredObject(KEY, "asset-1", "1", "webp");
        when(api.resource(eq(KEY), anyMap())).thenThrow(new NotFound("missing"));
        assertThat(adapter.verifyReadable(stored)).isEqualTo(Availability.MISSING);

        when(api.resource(eq(KEY), anyMap())).thenThrow(new RuntimeException("503"));
        assertCode(() -> adapter.verifyReadable(stored), CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void openUsesASignedAuthenticatedUrlInsideTheBackend() throws Exception {
        HttpResponse<InputStream> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        when(ok.body()).thenReturn(new ByteArrayInputStream(BYTES));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(ok);

        try (InputStream stream = adapter.open(new StoredObject(KEY, "asset-1", "17", "webp")).orElseThrow()) {
            assertThat(stream.readAllBytes()).isEqualTo(BYTES);
        }

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
        String url = request.getValue().uri().toString();
        assertThat(url).startsWith("https://").contains("/image/authenticated/s--").contains(KEY)
                .doesNotContain(TEST_SECRET);
    }

    @Test
    @SuppressWarnings("unchecked")
    void openMissingIsEmptyAndServerErrorIsUnavailable() throws Exception {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        StoredObject stored = new StoredObject(KEY, "asset-1", "17", "webp");

        when(response.statusCode()).thenReturn(404);
        assertThat(adapter.open(stored)).isEmpty();

        when(response.statusCode()).thenReturn(500);
        assertCode(() -> adapter.open(stored), CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void deleteTreatsNotFoundAsDone() throws Exception {
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "not found"));
        adapter.deleteIfExists(KEY);

        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "error"));
        assertCode(() -> adapter.deleteIfExists(KEY), CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void missingConfigurationMakesEveryOperationUnavailable() {
        CloudinaryMediaStorageAdapter unconfigured = new CloudinaryMediaStorageAdapter(null, httpClient, "ppswbs");
        assertCode(() -> unconfigured.putIfAbsent(KEY, content()), CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE);
        assertCode(() -> unconfigured.deleteIfExists(KEY), CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE);
        assertThat(unconfigured.storageKeyFor(UUID.randomUUID())).startsWith("ppswbs/");
    }

    private static VerifiedContent content() {
        return new VerifiedContent(BYTES, "image/webp", "sha");
    }

    private static Map<String, Object> uploadResult(String etag, boolean existing) {
        Map<String, Object> result = new HashMap<>();
        result.put("public_id", KEY);
        result.put("type", "authenticated");
        result.put("resource_type", "image");
        result.put("asset_id", "asset-1");
        result.put("version", 1700000000);
        result.put("format", "webp");
        result.put("etag", etag);
        if (existing) {
            result.put("existing", true);
        }
        return result;
    }

    private static String md5(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
    }

    private static void assertCode(Runnable action, CatalogueErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CatalogueException.class, error -> assertThat(error.code()).isEqualTo(code));
    }
}
