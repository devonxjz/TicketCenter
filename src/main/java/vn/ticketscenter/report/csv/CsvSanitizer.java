package vn.ticketscenter.report.csv;

/** Serializes a raw logical cell using the report's Excel-oriented CSV profile. */
public final class CsvSanitizer {
    private static final String FORMULA_STARTERS = "=+-@＝＋－＠";

    private CsvSanitizer() {}

    /**
     * Returns a fully quoted field. Risky prefixes receive an actual leading tab;
     * that tab is part of the exported value. Callers must pass unescaped input.
     */
    public static String escapeCsvCell(String value) {
        String raw = value == null ? "" : value;
        validateUnicode(raw);
        String prefix = needsNeutralizing(raw) ? "\t" : "";
        return "\"" + prefix + raw.replace("\"", "\"\"") + "\"";
    }

    private static void validateUnicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == 0 || Character.isLowSurrogate(current)) {
                throw invalidCell();
            }
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw invalidCell();
                }
                i++;
            }
        }
    }

    private static boolean needsNeutralizing(String value) {
        boolean controlPrefix = false;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            boolean controlOrFormat = type == Character.CONTROL || type == Character.FORMAT;
            if (controlOrFormat
                    || Character.isWhitespace(codePoint)
                    || Character.isSpaceChar(codePoint)) {
                controlPrefix |= controlOrFormat;
                offset += Character.charCount(codePoint);
            } else {
                return controlPrefix || FORMULA_STARTERS.indexOf(codePoint) >= 0;
            }
        }
        return controlPrefix;
    }

    private static IllegalArgumentException invalidCell() {
        return new IllegalArgumentException("CSV cell must contain valid Unicode without NUL.");
    }
}
