package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Binds the receipt storage from the same {@code CLOUDINARY_CLOUD_NAME}, {@code CLOUDINARY_API_KEY} and
 * {@code CLOUDINARY_API_SECRET} as 002, in 003.3's own folder {@code SETTLEMENT_RECEIPT_CLOUDINARY_FOLDER}
 * (default {@code ppswbs/settlement-receipts}). When a credential is missing the application still starts and
 * every storage operation answers {@code RECEIPT_STORAGE_UNAVAILABLE}.
 */
@Configuration(proxyBeanMethods = false)
class SettlementReceiptStorageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SettlementReceiptStorageConfiguration.class);

    @Bean
    SettlementReceiptStoragePort settlementReceiptStoragePort(
            @Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${CLOUDINARY_API_SECRET:}") String apiSecret,
            @Value("${SETTLEMENT_RECEIPT_CLOUDINARY_FOLDER:ppswbs/settlement-receipts}") String folder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            log.warn("Cloudinary is not configured; refund receipt images are unavailable");
            return new CloudinaryReceiptStorageAdapter(null, httpClient, folder);
        }
        Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret,
                "secure", true, "timeout", 30));
        return new CloudinaryReceiptStorageAdapter(cloudinary, httpClient, folder);
    }
}
