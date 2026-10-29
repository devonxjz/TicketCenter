package vn.ticketscenter.storage;

import java.nio.charset.StandardCharsets;

/** Strict non-animated RIFF/WebP framing and dimension checks. */
final class WebpContentValidator {
    private WebpContentValidator() {}

    static boolean isWebp(byte[] b) {
        return b.length >= 12 && tag(b, 0).equals("RIFF") && tag(b, 8).equals("WEBP");
    }

    static void inspect(byte[] b, long maxPixels) {
        if (!isWebp(b) || integer(b, 4, 4) + 8 != b.length) {
            throw invalid();
        }
        boolean frame = false, extended = false;
        long width = 0, height = 0;
        for (int offset = 12; offset < b.length; ) {
            if (offset + 8 > b.length) {
                throw invalid();
            }
            String kind = tag(b, offset);
            long length = integer(b, offset + 4, 4);
            int start = offset + 8;
            if (length > Integer.MAX_VALUE || start + length > b.length) {
                throw invalid();
            }
            int size = (int) length;
            switch (kind) {
                case "VP8X" -> {
                    if (extended || frame || size != 10 || (b[start] & 2) != 0) {
                        throw invalid();
                    }
                    extended = true;
                    width = integer(b, start + 4, 3) + 1;
                    height = integer(b, start + 7, 3) + 1;
                }
                case "VP8 " -> {
                    if (frame
                            || size < 10
                            || (b[start] & 1) != 0
                            || (b[start + 3] & 255) != 157
                            || (b[start + 4] & 255) != 1
                            || (b[start + 5] & 255) != 42) {
                        throw invalid();
                    }
                    long w = integer(b, start + 6, 2) & 16383, h = integer(b, start + 8, 2) & 16383;
                    if (extended && (width != w || height != h)) {
                        throw invalid();
                    }
                    width = w;
                    height = h;
                    frame = true;
                }
                case "VP8L" -> {
                    if (frame || size < 5 || (b[start] & 255) != 47) {
                        throw invalid();
                    }
                    long bits = integer(b, start + 1, 4);
                    if ((bits >>> 29) != 0) {
                        throw invalid();
                    }
                    long w = (bits & 16383) + 1, h = ((bits >>> 14) & 16383) + 1;
                    if (extended && (width != w || height != h)) {
                        throw invalid();
                    }
                    width = w;
                    height = h;
                    frame = true;
                }
                case "ALPH", "ICCP", "EXIF", "XMP " -> {
                    if (!extended) {
                        throw invalid();
                    }
                }
                // Reject animation, unknown chunks and appended non-image data.
                default -> throw invalid();
            }
            long next = (long) start + size + (size & 1);
            if (next > b.length) {
                throw invalid();
            }
            offset = (int) next;
        }
        if (!frame || width < 1 || height < 1 || width * height > maxPixels) {
            throw invalid();
        }
    }

    private static String tag(byte[] b, int offset) {
        return new String(b, offset, 4, StandardCharsets.US_ASCII);
    }

    private static long integer(byte[] b, int offset, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) {
            value |= (long) (b[offset + i] & 255) << (8 * i);
        }
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("WEBP format, framing or dimensions are invalid");
    }
}
