package edu.fpt.sep490_g22.ppswbs_backend.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "firebase")
@Getter
@Setter
public class FirebaseProperties {

    private Client client = new Client();
    private Admin admin = new Admin();
    private Emulator emulator = new Emulator();

    @Getter
    @Setter
    public static class Client {
        private String apiKey;
        private String authDomain;
        private String projectId;
        private String appId;
    }

    @Getter
    @Setter
    public static class Admin {
        private String credentialsPath;
        private String serviceAccountJson;
    }

    @Getter
    @Setter
    public static class Emulator {
        private String host;
    }
}
