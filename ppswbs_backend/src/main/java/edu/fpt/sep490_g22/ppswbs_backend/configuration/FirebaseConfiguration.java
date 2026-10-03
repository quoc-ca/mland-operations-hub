package edu.fpt.sep490_g22.ppswbs_backend.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class FirebaseConfiguration {

    private final FirebaseProperties firebaseProperties;
    private final Environment environment;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProd = false;
        for (String profile : activeProfiles) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                isProd = true;
                break;
            }
        }

        String emulatorHost = firebaseProperties.getEmulator().getHost();
        if (StringUtils.hasText(emulatorHost)) {
            if (isProd) {
                throw new IllegalStateException("Production environment MUST NOT use Firebase Auth Emulator host configuration!");
            }
            log.info("Using Firebase Auth Emulator at: {}", emulatorHost);
            System.setProperty("FIREBASE_AUTH_EMULATOR_HOST", emulatorHost);
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials;
        if (StringUtils.hasText(firebaseProperties.getAdmin().getCredentialsPath())) {
            try (FileInputStream fis = new FileInputStream(firebaseProperties.getAdmin().getCredentialsPath())) {
                credentials = GoogleCredentials.fromStream(fis);
            }
        } else if (StringUtils.hasText(firebaseProperties.getAdmin().getServiceAccountJson())) {
            credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(firebaseProperties.getAdmin().getServiceAccountJson().getBytes()));
        } else {
            log.warn("No Firebase Admin credentials provided. Using Mock/Default credentials.");
            credentials = GoogleCredentials.newBuilder().build();
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(StringUtils.hasText(firebaseProperties.getClient().getProjectId()) 
                        ? firebaseProperties.getClient().getProjectId() 
                        : "ppswbs-dev")
                .build();

        return FirebaseApp.initializeApp(options);
    }
}
