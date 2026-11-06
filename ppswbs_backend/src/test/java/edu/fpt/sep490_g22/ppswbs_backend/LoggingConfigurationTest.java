package edu.fpt.sep490_g22.ppswbs_backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingConfigurationTest {

    private static final Logger log = LoggerFactory.getLogger(LoggingConfigurationTest.class);

    @Test
    @DisplayName("Verify Logback multi-file logging by level, date folder partitioning, and stack trace line limit")
    void verifyLoggingConfiguration() throws IOException {
        String uniqueId = "test-" + System.currentTimeMillis();
        String infoMsg = "Unique info message " + uniqueId;
        String warnMsg = "Unique warn message " + uniqueId;
        String errorMsg = "Unique error message " + uniqueId;

        // Generate exception with deep call stack (> 50 frames)
        Exception deepException = createDeepCallStack(50, "Root cause error message");

        log.info(infoMsg);
        log.warn(warnMsg);
        log.error(errorMsg, deepException);

        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path logDirPath = Path.of("logs", dateFolder);

        assertThat(Files.exists(logDirPath))
                .as("Logs folder for today should exist: %s", logDirPath.toAbsolutePath())
                .isTrue();

        Path infoFile = logDirPath.resolve("info.log");
        Path warnFile = logDirPath.resolve("warning.log");
        Path errorFile = logDirPath.resolve("error.log");

        assertThat(Files.exists(infoFile)).as("info.log must exist").isTrue();
        assertThat(Files.exists(warnFile)).as("warning.log must exist").isTrue();
        assertThat(Files.exists(errorFile)).as("error.log must exist").isTrue();

        List<String> infoLines = Files.readAllLines(infoFile);
        List<String> warnLines = Files.readAllLines(warnFile);
        List<String> errorLines = Files.readAllLines(errorFile);

        // Verify info.log: contains INFO, does NOT contain WARN or ERROR
        assertThat(infoLines).anyMatch(line -> line.contains(infoMsg));
        assertThat(infoLines).noneMatch(line -> line.contains(warnMsg));
        assertThat(infoLines).noneMatch(line -> line.contains(errorMsg));

        // Verify warning.log: contains WARN, does NOT contain INFO or ERROR
        assertThat(warnLines).anyMatch(line -> line.contains(warnMsg));
        assertThat(warnLines).noneMatch(line -> line.contains(infoMsg));
        assertThat(warnLines).noneMatch(line -> line.contains(errorMsg));

        // Verify error.log: contains ERROR, does NOT contain INFO or WARN
        assertThat(errorLines).anyMatch(line -> line.contains(errorMsg));
        assertThat(errorLines).noneMatch(line -> line.contains(infoMsg));
        assertThat(errorLines).noneMatch(line -> line.contains(warnMsg));

        // Verify log format matches HH:mm:ss regex on log lines
        assertThat(infoLines.stream().filter(l -> l.contains(infoMsg)).findFirst().orElse(""))
                .matches("^\\d{2}:\\d{2}:\\d{2}\\s+.*");

        // Verify stack trace max 15 lines
        List<String> traceLines = new ArrayList<>();
        boolean capture = false;
        for (String line : errorLines) {
            if (line.contains(errorMsg)) {
                capture = true;
                continue;
            }
            if (capture) {
                if (line.matches("^\\d{2}:\\d{2}:\\d{2}\\s+.*")) {
                    // Next log entry reached
                    break;
                }
                if (line.startsWith("\tat ") || line.startsWith("at ")) {
                    traceLines.add(line);
                }
            }
        }

        // Deep call stack had 50+ frames, but %rEx{15} must limit to exactly 15 frames
        assertThat(traceLines.size())
                .as("Stack trace frames must be capped at 15 lines")
                .isEqualTo(15);
    }

    private Exception createDeepCallStack(int depth, String message) {
        if (depth <= 1) {
            return new RuntimeException(message);
        }
        return createDeepCallStack(depth - 1, message);
    }
}
