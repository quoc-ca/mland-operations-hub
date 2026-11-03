package edu.fpt.sep490_g22.ppswbs_backend.configuration;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Configuration(proxyBeanMethods = false)
public class FirebaseConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfiguration.class);

    private final String projectId;
    private final String credentialsPath;
    private final String serviceAccountJson;
    private final String emulatorHost;
    private final ResourceLoader resourceLoader;

    public FirebaseConfiguration(
            @Value("${firebase.client.project-id:${firebase.project-id:}}") String projectId,
            @Value("${firebase.admin.credentials-path:}") String credentialsPath,
            @Value("${firebase.admin.service-account-json:}") String serviceAccountJson,
            @Value("${firebase.emulator.host:}") String emulatorHost,
            ResourceLoader resourceLoader
    ) {
        this.projectId = projectId;
        this.credentialsPath = credentialsPath;
        this.serviceAccountJson = serviceAccountJson;
        this.emulatorHost = emulatorHost;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public FirebaseApp firebaseApp() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder();
        boolean credentialsSet = false;

        // 1. Service account JSON via direct property/env
        if (StringUtils.hasText(serviceAccountJson)) {
            try (InputStream stream = new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))) {
                optionsBuilder.setCredentials(GoogleCredentials.fromStream(stream));
                credentialsSet = true;
                log.info("Firebase Admin initialized using inline service account JSON");
            } catch (Exception e) {
                log.error("Failed to parse inline service account JSON: {}", e.getMessage(), e);
            }
        }

        // 2. Service account credentials path (file or classpath)
        if (!credentialsSet && StringUtils.hasText(credentialsPath)) {
            try {
                InputStream stream = resolveCredentialsStream(credentialsPath);
                if (stream != null) {
                    try (stream) {
                        optionsBuilder.setCredentials(GoogleCredentials.fromStream(stream));
                        credentialsSet = true;
                        log.info("Firebase Admin initialized using credentials file: {}", credentialsPath);
                    }
                } else {
                    log.warn("Firebase credentials path '{}' not found on filesystem or classpath", credentialsPath);
                }
            } catch (Exception e) {
                log.error("Failed to read Firebase credentials from '{}': {}", credentialsPath, e.getMessage(), e);
            }
        }

        // 3. Firebase Auth Emulator
        String activeEmulatorHost = StringUtils.hasText(emulatorHost)
                ? emulatorHost
                : System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
        if (StringUtils.hasText(activeEmulatorHost)) {
            System.setProperty("FIREBASE_AUTH_EMULATOR_HOST", activeEmulatorHost);
            log.info("Firebase Auth running with emulator host: {}", activeEmulatorHost);
            if (!credentialsSet) {
                optionsBuilder.setCredentials(GoogleCredentials.create(
                        new AccessToken("emulator-token", Date.from(Instant.now().plus(365, ChronoUnit.DAYS)))
                ));
                credentialsSet = true;
            }
        }

        // 4. Application Default Credentials (ADC)
        if (!credentialsSet) {
            try {
                GoogleCredentials defaultCredentials = GoogleCredentials.getApplicationDefault();
                optionsBuilder.setCredentials(defaultCredentials);
                credentialsSet = true;
                log.info("Firebase Admin initialized using Google Application Default Credentials (ADC)");
            } catch (Exception e) {
                log.debug("Google Application Default Credentials not available: {}", e.getMessage());
            }
        }

        // 5. Fallback for local development if no credentials found
        if (!credentialsSet) {
            log.warn("Firebase Admin private key not provided. "
                    + "Initialized with mock development credentials. "
                    + "To verify real Firebase tokens, set FIREBASE_ADMIN_CREDENTIALS_PATH or FIREBASE_ADMIN_SERVICE_ACCOUNT in .env");
            optionsBuilder.setCredentials(GoogleCredentials.create(
                    new AccessToken("dev-placeholder-token", Date.from(Instant.now().plus(30, ChronoUnit.DAYS)))
            ));
        }

        if (StringUtils.hasText(projectId)) {
            optionsBuilder.setProjectId(projectId);
        }

        return FirebaseApp.initializeApp(optionsBuilder.build());
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    private InputStream resolveCredentialsStream(String path) {
        try {
            Resource resource = resourceLoader.getResource(path);
            if (resource.exists()) {
                return resource.getInputStream();
            }
        } catch (Exception ignored) {
        }

        File file = new File(path);
        if (file.exists() && file.isFile()) {
            try {
                return new FileInputStream(file);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}

