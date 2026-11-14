package edu.fpt.sep490_g22.ppswbs_backend.configuration;

import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
public class TimezoneConfiguration {

    public static final String TIMEZONE_VIETNAM = "Asia/Ho_Chi_Minh";
    public static final ZoneId ZONE_ID_VIETNAM = ZoneId.of(TIMEZONE_VIETNAM);

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone(TIMEZONE_VIETNAM));
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonTimezoneCustomizer() {
        return builder -> {
            builder.timeZone(TimeZone.getTimeZone(TIMEZONE_VIETNAM));
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}

