package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;

/** Local contract validation; does not change Jackson settings for other features. */
public final class OtaRequestScalars {
    private OtaRequestScalars() {}
    public static final class Text extends JsonDeserializer<String> {
        @Override public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (String) context.handleUnexpectedToken(String.class, parser);
            }
            return parser.getText();
        }
    }
    public static final class Version extends JsonDeserializer<Long> {
        @Override public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Long) context.handleUnexpectedToken(Long.class, parser);
            }
            return parser.getLongValue();
        }
    }
    public static final class Timestamp extends JsonDeserializer<Instant> {
        @Override public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (Instant) context.handleUnexpectedToken(Instant.class, parser);
            }
            try { return Instant.parse(parser.getText()); }
            catch (java.time.format.DateTimeParseException exception) {
                return (Instant) context.handleWeirdStringValue(Instant.class, parser.getText(), "Expected an ISO date-time");
            }
        }
    }
}
