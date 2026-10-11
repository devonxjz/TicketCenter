package vn.ticketscenter.report.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StreamingCsvExporterTest {
    @Test
    void writesUtf8BomHeaderAndExactRows() throws IOException {
        var output = new ProbeOutput(true);
        StreamingCsvExporter.write(output, List.of("eventTitle", "grossRevenue", "paidAt"), List.of(
                List.of("Đêm \"Mùa Thu\", Hà Nội", "9223372036854775807", "2026-10-09T00:00:00Z")));

        String expected = "\uFEFF\"eventTitle\",\"grossRevenue\",\"paidAt\"\r\n"
                + "\"Đêm \"\"Mùa Thu\"\", Hà Nội\",\"9223372036854775807\",\"2026-10-09T00:00:00Z\"\r\n";
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), output.captured.toByteArray());
        assertFalse(output.closed);
    }

    @Test
    void sanitizesHeadersAndRows() throws IOException {
        var output = new ProbeOutput(true);
        StreamingCsvExporter.write(output, List.of("=1+1", "text"), List.of(
                List.of("\u00A0=1+1", "a,b\"c\nd")));

        String expected = "\uFEFF\"\t=1+1\",\"text\"\r\n"
                + "\"\t\u00A0=1+1\",\"a,b\"\"c\nd\"\r\n";
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), output.captured.toByteArray());
        assertFalse(output.closed);
    }

    @Test
    void writesHeaderForEmptyRows() throws IOException {
        var output = new ProbeOutput(true);
        StreamingCsvExporter.write(output, List.of("name"), List.of());

        assertArrayEquals("\uFEFF\"name\"\r\n".getBytes(StandardCharsets.UTF_8), output.captured.toByteArray());
        assertFalse(output.closed);
    }

    @Test
    void preservesNullCell() throws IOException {
        var output = new ProbeOutput(true);
        StreamingCsvExporter.write(output, List.of("name", "note"),
                List.of(Arrays.asList("Hà Nội", null)));

        assertArrayEquals("\uFEFF\"name\",\"note\"\r\n\"Hà Nội\",\"\"\r\n"
                .getBytes(StandardCharsets.UTF_8), output.captured.toByteArray());
        assertFalse(output.closed);
    }

    @Test
    void rejectsNullOutputBeforeReadingRows() {
        Iterable<List<String>> rows = () -> fail("Must validate output before reading rows");
        assertThrows(IllegalArgumentException.class, () -> StreamingCsvExporter.write(null, List.of("name"), rows));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidArguments")
    void rejectsArgumentsBeforeWriting(String reason, List<String> headers, Iterable<List<String>> rows) {
        var output = new ProbeOutput(true);
        assertThrows(IllegalArgumentException.class, () -> StreamingCsvExporter.write(output, headers, rows));
        assertEquals(0L, output.bytes);
        assertFalse(output.closed);
    }

    static Stream<Arguments> invalidArguments() {
        return Stream.of(
                Arguments.of("null headers", null, List.of()),
                Arguments.of("empty headers", List.of(), List.of()),
                Arguments.of("null header cell", Arrays.asList("name", null), List.of()),
                Arguments.of("NUL header", List.of("a\u0000b"), List.of()),
                Arguments.of("malformed Unicode header", List.of("\uD83C"), List.of()),
                Arguments.of("null rows", List.of("name"), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedRows")
    void rejectsMalformedRows(String reason, List<String> row) {
        var output = new ProbeOutput(true);
        assertThrows(IllegalArgumentException.class, () -> StreamingCsvExporter.write(
                output, List.of("name", "note"), Arrays.asList(row)));
        assertArrayEquals("\uFEFF\"name\",\"note\"\r\n".getBytes(StandardCharsets.UTF_8),
                output.captured.toByteArray());
        assertFalse(output.closed);
    }

    static Stream<Arguments> malformedRows() {
        return Stream.of(
                Arguments.of("null row", (Object) null),
                Arguments.of("too few cells", List.of("invalid-marker")),
                Arguments.of("too many cells", List.of("invalid-marker", "b", "c")));
    }

    @Test
    void snapshotsHeader() throws IOException {
        var output = new ProbeOutput(true);
        var headers = new ArrayList<>(List.of("name", "note"));
        Iterable<List<String>> rows = () -> {
            assertArrayEquals("\uFEFF\"name\",\"note\"\r\n".getBytes(StandardCharsets.UTF_8),
                    output.captured.toByteArray(), "Header must be flushed before iterator acquisition");
            headers.add("late mutation");
            return List.of(List.of("Hà Nội", "ok")).iterator();
        };
        StreamingCsvExporter.write(output, headers, rows);

        assertArrayEquals("\uFEFF\"name\",\"note\"\r\n\"Hà Nội\",\"ok\"\r\n"
                .getBytes(StandardCharsets.UTF_8), output.captured.toByteArray());
        assertFalse(output.closed);
    }

    @Test
    void usesOneIterator() throws IOException {
        var output = new ProbeOutput(true);
        var acquisitions = new AtomicInteger();
        Iterable<List<String>> rows = () -> {
            assertEquals(1, acquisitions.incrementAndGet(), "Source is single-use");
            return List.of(List.of("value")).iterator();
        };
        StreamingCsvExporter.write(output, List.of("name"), rows);

        assertArrayEquals("\uFEFF\"name\"\r\n\"value\"\r\n".getBytes(StandardCharsets.UTF_8),
                output.captured.toByteArray());
        assertFalse(output.closed);
    }

    @Test
    void streamsTenThousandRowsWithoutMaterializing() throws IOException {
        var output = new ProbeOutput(false);
        var rows = new LazyRows(output, true);
        StreamingCsvExporter.write(output, List.of("n", "name"), rows);

        assertEquals(10_000, rows.nextCalls);
        assertEquals(10_348_905L, output.bytes);
        assertFalse(output.closed);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 3L, 15L})
    void propagatesOutputFailureAndStopsSource(long failAt) {
        var output = new ProbeOutput(false);
        output.failAt = failAt;
        var rows = new LazyRows(output, false);

        assertSame(output.failure, assertThrows(IOException.class,
                () -> StreamingCsvExporter.write(output, List.of("n", "name"), rows)));
        assertTrue(rows.nextCalls < 10_000, "Output failure must stop source consumption");
        if (failAt < 15L) {
            assertEquals(0, rows.nextCalls, "Failed header must not acquire data");
        }
        assertFalse(output.closed);
    }

    @Test
    void propagatesFinalFlushFailure() {
        var output = new ProbeOutput(true);
        output.failFlush = 2;

        assertSame(output.failure, assertThrows(IOException.class,
                () -> StreamingCsvExporter.write(output, List.of("name"), List.of(List.of("value")))));
        assertFalse(output.closed);
    }

    @Test
    void propagatesIteratorFailure() {
        var output = new ProbeOutput(true);
        var failure = new IllegalStateException("Controlled source failure");
        Iterable<List<String>> rows = () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public List<String> next() {
                throw failure;
            }
        };

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> StreamingCsvExporter.write(output, List.of("name"), rows)));
        assertFalse(output.closed);
    }

    private static final class LazyRows implements Iterable<List<String>> {
        private final ProbeOutput output;
        private final boolean checkProgress;
        private int nextCalls;

        private LazyRows(ProbeOutput output, boolean checkProgress) {
            this.output = output;
            this.checkProgress = checkProgress;
        }

        @Override
        public Iterator<List<String>> iterator() {
            return new Iterator<>() {
                private int index;
                private long checkpoint = output.bytes;
                private final String name = "X".repeat(1024);

                @Override
                public boolean hasNext() {
                    return index < 10_000;
                }

                @Override
                public List<String> next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    if (checkProgress && index > 0 && index % 64 == 0) {
                        assertTrue(output.bytes > checkpoint, "Source advanced without streaming output");
                        checkpoint = output.bytes;
                    }
                    nextCalls++;
                    return List.of(Integer.toString(index++), name);
                }
            };
        }
    }

    /** A measured sink with controlled IO failures; it contains no CSV logic. */
    private static final class ProbeOutput extends OutputStream {
        private final ByteArrayOutputStream captured;
        private final IOException failure = new IOException("Controlled output failure");
        private long bytes;
        private long failAt = Long.MAX_VALUE;
        private int flushCalls;
        private int failFlush = -1;
        private boolean closed;

        private ProbeOutput(boolean capture) {
            captured = capture ? new ByteArrayOutputStream() : null;
        }

        @Override
        public void write(int value) throws IOException {
            if (bytes + 1 > failAt) {
                throw failure;
            }
            bytes++;
            if (captured != null) {
                captured.write(value);
            }
        }

        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            if (bytes + length > failAt) {
                throw failure;
            }
            bytes += length;
            if (captured != null) {
                captured.write(value, offset, length);
            }
        }

        @Override
        public void flush() throws IOException {
            if (++flushCalls == failFlush) {
                throw failure;
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
