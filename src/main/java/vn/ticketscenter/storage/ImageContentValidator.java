package vn.ticketscenter.storage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

public final class ImageContentValidator {
    public static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;
    public static final long DEFAULT_MAX_PIXELS = 12_000_000;
    private static final Set<String> FORMATS = Set.of("png", "jpeg");

    private ImageContentValidator() {
    }

    public static byte[] readAndValidate(InputStream content) throws IOException {
        return readAndValidate(content, DEFAULT_MAX_BYTES, DEFAULT_MAX_PIXELS);
    }

    public static byte[] readAndValidate(InputStream content, long maxBytes, long maxPixels) throws IOException {
        if (content == null) throw new IllegalArgumentException("image is required");
        if (maxBytes <= 0 || maxPixels <= 0) throw new IllegalArgumentException("image limits must be positive");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        for (int count; (count = content.read(buffer)) >= 0; ) {
            total += count;
            if (total > maxBytes) throw new IllegalArgumentException("image exceeds size limit");
            output.write(buffer, 0, count);
        }
        if (total == 0) throw new IllegalArgumentException("image is empty");

        byte[] bytes = output.toByteArray();
        inspect(bytes, maxPixels);
        return bytes;
    }

    private static void inspect(byte[] bytes, long maxPixels) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) throw new IllegalArgumentException("image format is invalid");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("image format is invalid");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!FORMATS.contains(format)) throw new IllegalArgumentException("image must be PNG or JPEG");
                long pixels = Math.multiplyExact((long) reader.getWidth(0), (long) reader.getHeight(0));
                if (pixels <= 0 || pixels > maxPixels) throw new IllegalArgumentException("image dimensions exceed limit");
                if (reader.read(0) == null) throw new IllegalArgumentException("image cannot be decoded");
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("image dimensions exceed limit", exception);
            } finally {
                reader.dispose();
            }
        }
    }
}
