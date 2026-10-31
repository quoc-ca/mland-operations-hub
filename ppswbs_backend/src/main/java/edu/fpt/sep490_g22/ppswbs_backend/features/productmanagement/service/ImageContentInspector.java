package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Checks that an uploaded image is really the declared JPEG, PNG or WebP (FR-008): the declared MIME type,
 * the file signature and a readable image header must all agree.
 *
 * <p>JPEG and PNG headers are read with {@link ImageIO} (dimensions only, so a huge image is never fully
 * decoded). The JDK has no WebP decoder, so WebP is checked structurally: RIFF/WEBP container, consistent
 * RIFF size and a VP8, VP8L or VP8X chunk with positive dimensions.
 */
public final class ImageContentInspector {

    private ImageContentInspector() {
    }

    /** Returns the verified content type, or throws {@code MEDIA_TOO_LARGE} / {@code MEDIA_TYPE_UNSUPPORTED}. */
    public static String verify(String declaredContentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new CatalogueException(CatalogueErrorCode.MEDIA_TYPE_UNSUPPORTED);
        }
        if (bytes.length > 5_000_000) {
            throw new CatalogueException(CatalogueErrorCode.MEDIA_TOO_LARGE);
        }
        String detected = detect(bytes);
        if (detected == null || !detected.equals(normalize(declaredContentType))) {
            throw new CatalogueException(CatalogueErrorCode.MEDIA_TYPE_UNSUPPORTED);
        }
        boolean readable = switch (detected) {
            case "image/jpeg" -> hasReadableHeader(bytes, "jpeg");
            case "image/png" -> hasReadableHeader(bytes, "png");
            case "image/webp" -> isStructurallyValidWebp(bytes);
            default -> false;
        };
        if (!readable) {
            throw new CatalogueException(CatalogueErrorCode.MEDIA_TYPE_UNSUPPORTED);
        }
        return detected;
    }

    static String detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String normalize(String contentType) {
        if (contentType == null) {
            return null;
        }
        int parameters = contentType.indexOf(';');
        String base = (parameters >= 0 ? contentType.substring(0, parameters) : contentType).strip()
                .toLowerCase(java.util.Locale.ROOT);
        return "image/jpg".equals(base) ? "image/jpeg" : base;
    }

    private static boolean hasReadableHeader(byte[] bytes, String formatName) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(formatName);
        if (!readers.hasNext()) {
            return false;
        }
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(input, true, true);
            return reader.getWidth(0) > 0 && reader.getHeight(0) > 0;
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            reader.dispose();
        }
    }

    private static boolean isStructurallyValidWebp(byte[] b) {
        if (b.length < 30) {
            return false;
        }
        long riffSize = littleEndian(b, 4, 4);
        if (riffSize + 8 > b.length || riffSize < 22) {
            return false;
        }
        String chunk = new String(b, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return switch (chunk) {
            case "VP8 " -> (b[23] & 0xFF) == 0x9D && (b[24] & 0xFF) == 0x01 && (b[25] & 0xFF) == 0x2A
                    && (littleEndian(b, 26, 2) & 0x3FFF) > 0 && (littleEndian(b, 28, 2) & 0x3FFF) > 0;
            case "VP8L" -> (b[20] & 0xFF) == 0x2F;
            // Extended format: canvas width/height are stored minus one, so any value is a positive size;
            // the chunk must declare the expected 10-byte payload.
            case "VP8X" -> littleEndian(b, 16, 4) == 10;
            default -> false;
        };
    }

    private static long littleEndian(byte[] b, int offset, int length) {
        long value = 0;
        for (int i = length - 1; i >= 0; i--) {
            value = (value << 8) | (b[offset + i] & 0xFF);
        }
        return value;
    }
}
