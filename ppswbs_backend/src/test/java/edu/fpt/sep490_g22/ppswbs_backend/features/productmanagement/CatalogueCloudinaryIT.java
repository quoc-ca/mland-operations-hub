package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueException;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueMediaCleanupService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CatalogueMediaService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.CreateProductService.ImageInput;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.ProductImageService;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.media.CloudinaryMediaStorageAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueFlowFixture;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * T051 against the REAL Cloudinary account given by {@code CLOUDINARY_CLOUD_NAME/API_KEY/API_SECRET}
 * (environment only, never logged). Everything is written under the throw-away folder {@code ppswbs-it/<run>} and
 * deleted at the end. Without the variables the class fails as NOT RUN; a mocked port is never used as evidence.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
@Import(CatalogueFlowFixture.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueCloudinaryIT {

    private static final String RUN_FOLDER = "ppswbs-it/" + UUID.randomUUID().toString().substring(0, 8);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @DynamicPropertySource
    static void testFolder(DynamicPropertyRegistry registry) {
        registry.add("CLOUDINARY_FOLDER", () -> RUN_FOLDER);
    }

    @Autowired private MediaStoragePort storage;
    @Autowired private CatalogueMediaService mediaService;
    @Autowired private CatalogueMediaCleanupService cleanup;
    @Autowired private ProductImageService imageService;
    @Autowired private CatalogueFlowFixture fixture;

    private final List<String> written = new ArrayList<>();
    private String cloudName;
    private String apiKey;
    private String apiSecret;

    @BeforeAll
    void requireAccount() {
        cloudName = System.getenv("CLOUDINARY_CLOUD_NAME");
        apiKey = System.getenv("CLOUDINARY_API_KEY");
        apiSecret = System.getenv("CLOUDINARY_API_SECRET");
        if (blank(cloudName) || blank(apiKey) || blank(apiSecret)) {
            fail("NOT RUN: CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET must be set");
        }
        assertThat(storage).isInstanceOf(CloudinaryMediaStorageAdapter.class);
    }

    @AfterAll
    void deleteEverythingWritten() {
        for (String key : written) {
            storage.deleteIfExists(key);
        }
    }

    @Test
    void signedAuthenticatedUploadCanBeVerifiedReadBackAndDeleted() throws IOException {
        byte[] png = png();
        String key = newKey();

        MediaStoragePort.StoredObject stored = storage.putIfAbsent(key, content(png));

        assertThat(stored.storageKey()).isEqualTo(key).startsWith(RUN_FOLDER + "/");
        assertThat(stored.providerAssetId()).isNotBlank();
        assertThat(stored.providerVersion()).isNotBlank();
        assertThat(stored.providerFormat()).isEqualTo("png");
        assertThat(storage.verifyReadable(stored)).isEqualTo(MediaStoragePort.Availability.READABLE);
        Optional<InputStream> opened = storage.open(stored);
        assertThat(opened).isPresent();
        try (InputStream in = opened.get()) {
            assertThat(in.readAllBytes()).isEqualTo(png);
        }

        storage.deleteIfExists(key);
        // The Admin API is authoritative at once. A signed delivery URL may still be answered from the CDN cache
        // for a while after destroy (invalidation is asynchronous), so open() is not asserted here: the backend
        // decides access from its own database before serving bytes and never relies on CDN invalidation
        // (contracts/interfaces.md, MediaStoragePort).
        assertThat(storage.verifyReadable(stored)).isEqualTo(MediaStoragePort.Availability.MISSING);
        storage.deleteIfExists(key); // already gone: still a success
    }

    @Test
    void aRetryWithTheSameContentReturnsTheSameObjectAndDifferentContentIsRefused() throws IOException {
        byte[] png = png();
        String key = newKey();
        MediaStoragePort.StoredObject first = storage.putIfAbsent(key, content(png));

        MediaStoragePort.StoredObject retry = storage.putIfAbsent(key, content(png));

        assertThat(retry.providerAssetId()).isEqualTo(first.providerAssetId());
        assertThat(retry.providerVersion()).isEqualTo(first.providerVersion());
        assertThatThrownBy(() -> storage.putIfAbsent(key, content(png())))
                .isInstanceOfSatisfying(CatalogueException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogueErrorCode.IDEMPOTENCY_KEY_REUSED));
        try (InputStream in = storage.open(first).orElseThrow()) {
            assertThat(in.readAllBytes()).as("never overwritten").isEqualTo(png);
        }
    }

    @Test
    void unsignedOriginalAndDerivedUrlsAreRefusedByTheProvider() throws Exception {
        String key = newKey();
        MediaStoragePort.StoredObject stored = storage.putIfAbsent(key, content(png()));
        Cloudinary client = new Cloudinary(ObjectUtils.asMap("cloud_name", cloudName, "secure", true));

        String unsignedOriginal = client.url().resourceType("image").type("authenticated")
                .version(stored.providerVersion()).format(stored.providerFormat()).generate(key);
        String unsignedDerived = client.url().resourceType("image").type("authenticated")
                .transformation(new Transformation<>().width(32).crop("scale"))
                .format(stored.providerFormat()).generate(key);
        String publicType = client.url().resourceType("image").type("upload")
                .format(stored.providerFormat()).generate(key);

        assertThat(status(unsignedOriginal)).as("unsigned original").isNotEqualTo(200);
        assertThat(status(unsignedDerived)).as("unsigned derived").isNotEqualTo(200);
        assertThat(status(publicType)).as("public delivery type").isNotEqualTo(200);
    }

    @Test
    void failuresAndNormalOperationsNeverLogTheSecretTheKeyOrASignedUrl() throws IOException {
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.addAppender(logs);
        try {
            String key = newKey();
            MediaStoragePort.StoredObject stored = storage.putIfAbsent(key, content(png()));
            storage.open(stored).orElseThrow().close();

            Cloudinary wrongSecret = new Cloudinary(ObjectUtils.asMap("cloud_name", cloudName, "api_key", apiKey,
                    "api_secret", "wrong-" + UUID.randomUUID(), "secure", true));
            MediaStoragePort broken = new CloudinaryMediaStorageAdapter(wrongSecret, HTTP, RUN_FOLDER);
            assertThatThrownBy(() -> broken.putIfAbsent(newKey(), content(png())))
                    .isInstanceOfSatisfying(CatalogueException.class, error -> {
                        assertThat(error.code()).isEqualTo(CatalogueErrorCode.MEDIA_DEPENDENCY_UNAVAILABLE);
                        assertThat(String.valueOf(error.getMessage())).doesNotContain(apiKey).doesNotContain(apiSecret);
                    });
        } finally {
            root.detachAppender(logs);
        }
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).allSatisfy(message -> assertThat(message)
                .doesNotContain(apiSecret).doesNotContain(apiKey).doesNotContain("/s--").doesNotContain("res.cloudinary.com"));
        assertThat(loggedUnavailable(logs)).as("the failure itself is logged, without details").isTrue();
    }

    @Test
    void uploadThroughTheMediaServiceThenCleanupDeletesOnlyUnreferencedObjects() throws IOException {
        CatalogueMediaService.UploadResult kept = mediaService.upload(STAFF, UUID.randomUUID(), "image/png", png());
        CatalogueMediaService.UploadResult orphan = mediaService.upload(STAFF, UUID.randomUUID(), "image/png", png());
        CatalogueMediaObject keptRow = fixture.media(kept.mediaId());
        CatalogueMediaObject orphanRow = fixture.media(orphan.mediaId());
        written.add(keptRow.getStorageKey());
        written.add(orphanRow.getStorageKey());
        assertThat(keptRow.getState()).isEqualTo(CatalogueMediaObject.State.READY);
        assertThat(keptRow.getStorageKey()).startsWith(RUN_FOLDER + "/");

        long productId = fixture.draftProduct(STAFF);
        imageService.replace(STAFF, UUID.randomUUID(), Long.toString(productId), fixture.productVersion(productId),
                List.of(new ImageInput(kept.mediaId(), "Ảnh thật", 0)));
        Instant later = Instant.now().plus(Duration.ofDays(40));

        assertThat(cleanup.clean(kept.mediaId(), later)).isEqualTo(CatalogueMediaCleanupService.Result.SKIPPED);
        assertThat(cleanup.clean(orphan.mediaId(), later)).isEqualTo(CatalogueMediaCleanupService.Result.DELETED);

        assertThat(storage.verifyReadable(stored(keptRow))).isEqualTo(MediaStoragePort.Availability.READABLE);
        assertThat(storage.verifyReadable(stored(orphanRow))).isEqualTo(MediaStoragePort.Availability.MISSING);
        assertThat(fixture.media(orphan.mediaId()).getState()).isEqualTo(CatalogueMediaObject.State.DELETED);
    }

    private static boolean loggedUnavailable(ListAppender<ILoggingEvent> logs) {
        return logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains("Cloudinary unavailable"));
    }

    private static MediaStoragePort.StoredObject stored(CatalogueMediaObject row) {
        return new MediaStoragePort.StoredObject(row.getStorageKey(), row.getProviderAssetId(), row.getProviderVersion(),
                row.getProviderFormat());
    }

    private String newKey() {
        String key = storage.storageKeyFor(UUID.randomUUID());
        written.add(key);
        return key;
    }

    private static MediaStoragePort.VerifiedContent content(byte[] png) {
        try {
            return new MediaStoragePort.VerifiedContent(png, "image/png",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A small, unique, decodable PNG. */
    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                image.setRGB(x, y, new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)).getRGB());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static int status(String url) throws Exception {
        HttpResponse<Void> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET()
                .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
