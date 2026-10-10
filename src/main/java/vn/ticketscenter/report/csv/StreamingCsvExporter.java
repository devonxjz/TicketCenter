package vn.ticketscenter.report.csv;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Writes raw logical cells as UTF-8 CSV without closing the caller's stream. */
public final class StreamingCsvExporter {
    private StreamingCsvExporter() {
    }

    /**
     * Writes a BOM, quoted header and rows with CRLF record endings.
     * The caller owns the output and row source, including cleanup on failure.
     * A failure after the header can leave partial output and is propagated.
     */
    public static void write(OutputStream output, List<String> headers,
                             Iterable<? extends List<String>> rows) throws IOException {
        if (output == null || headers == null || headers.isEmpty() || rows == null) {
            throw new IllegalArgumentException("CSV output, nonempty headers and rows are required.");
        }
        var escapedHeaders = new ArrayList<String>(headers.size());
        for (String header : headers) {
            if (header == null) {
                throw new IllegalArgumentException("CSV headers must not contain null cells.");
            }
            escapedHeaders.add(CsvSanitizer.escapeCsvCell(header));
        }

        var writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        writer.write('\uFEFF');
        writeFields(writer, escapedHeaders);
        writer.flush();

        for (List<String> row : rows) {
            if (row == null || row.size() != escapedHeaders.size()) {
                throw new IllegalArgumentException("CSV rows must match the header width.");
            }
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    writer.write(',');
                }
                writer.write(CsvSanitizer.escapeCsvCell(row.get(i)));
            }
            writer.write("\r\n");
        }
        writer.flush();
    }

    private static void writeFields(BufferedWriter writer, List<String> fields) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(fields.get(i));
        }
        writer.write("\r\n");
    }
}
