package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service.MediaStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Binds the Cloudinary adapter from the environment (T004/T015). Credentials are read only from
 * {@code CLOUDINARY_CLOUD_NAME}, {@code CLOUDINARY_API_KEY} and {@code CLOUDINARY_API_SECRET}; there are
 * no defaults in committed files and the values are never logged. When any of them is missing the
 * adapter still starts but every operation fails with {@code MEDIA_DEPENDENCY_UNAVAILABLE}.
 * {@code CLOUDINARY_FOLDER} separates namespaces (for example a test namespace for T051).
 */
@Configuration(proxyBeanMethods = false)
class CloudinaryMediaConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryMediaConfiguration.class);
    private static final int PROVIDER_TIMEOUT_SECONDS = 30;

    @Bean
    MediaStoragePort mediaStoragePort(@Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
                                      @Value("${CLOUDINARY_API_KEY:}") String apiKey,
                                      @Value("${CLOUDINARY_API_SECRET:}") String apiSecret,
                                      @Value("${CLOUDINARY_FOLDER:ppswbs/catalogue}") String folder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            log.warn("Cloudinary is not configured; catalogue media operations are unavailable");
            return new CloudinaryMediaStorageAdapter(null, httpClient, folder);
        }
        Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true,
                "timeout", PROVIDER_TIMEOUT_SECONDS));
        return new CloudinaryMediaStorageAdapter(cloudinary, httpClient, folder);
    }
}
