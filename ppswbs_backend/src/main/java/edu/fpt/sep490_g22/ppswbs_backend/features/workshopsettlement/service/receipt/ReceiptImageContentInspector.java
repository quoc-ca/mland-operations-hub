package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.receipt;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * 003.3 T154 content check, the same rule as 002's image inspector (FR-039): the declared content type, the file
 * signature and a readable image header must agree, whatever the file extension says. JPEG, PNG and WebP only; a PDF
 * is refused. JPEG and PNG headers are read with ImageIO (dimensions only); WebP is checked structurally.
 */
public final class ReceiptImageContentInspector {

    public static final int MAX_BYTES = 5_000_000;

    private ReceiptImageContentInspector() {
    }

    /** Returns the verified content type, or throws {@code TOO_LARGE} (413) / {@code TYPE_UNSUPPORTED} (415). */
    public static String verify(String declaredContentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new SettlementReceiptException(SettlementReceiptException.Failure.TYPE_UNSUPPORTED);
        }
        if (bytes.length > MAX_BYTES) {
            throw new SettlementReceiptException(SettlementReceiptException.Failure.TOO_LARGE);
        }
        String detected = detect(bytes);
        if (detected == null || !detected.equals(normalize(declaredContentType))) {
            throw new SettlementReceiptException(SettlementReceiptException.Failure.TYPE_UNSUPPORTED);
        }
        boolean readable = switch (detected) {
            case "image/jpeg" -> hasReadableHeader(bytes, "jpeg");
            case "image/png" -> hasReadableHeader(bytes, "png");
            case "image/webp" -> isStructurallyValidWebp(bytes);
            default -> false;
        };
        if (!readable) {
            throw new SettlementReceiptException(SettlementReceiptException.Failure.TYPE_UNSUPPORTED);
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
                .toLowerCase(Locale.ROOT);
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
