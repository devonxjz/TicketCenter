package vn.ticketscenter.event.cache;

/** Validates cached JSON without object deserialization or adding a shared dependency. */
final class CachedJsonValidator {
    private final String source;
    private int index;

    private CachedJsonValidator(String source) {
        this.source = source;
    }

    static boolean isResponse(String source) {
        if (!source.startsWith("{\"data\":")
                || source.length() < 10
                || (source.charAt(8) != '[' && source.charAt(8) != '{')) return false;
        try {
            var reader = new CachedJsonValidator(source);
            reader.value(0);
            reader.space();
            return reader.index == source.length();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void value(int depth) {
        if (depth > 32) throw invalid();
        space();
        if (index >= source.length()) throw invalid();
        switch (source.charAt(index)) {
            case '{' -> object(depth + 1);
            case '[' -> array(depth + 1);
            case '"' -> string();
            case 't' -> literal("true");
            case 'f' -> literal("false");
            case 'n' -> literal("null");
            default -> number();
        }
    }

    private void object(int depth) {
        index++;
        space();
        if (take('}')) return;
        do {
            space();
            string();
            space();
            expect(':');
            value(depth);
            space();
        } while (take(','));
        expect('}');
    }

    private void array(int depth) {
        index++;
        space();
        if (take(']')) return;
        do {
            value(depth);
            space();
        } while (take(','));
        expect(']');
    }

    private void string() {
        expect('"');
        while (index < source.length()) {
            char c = source.charAt(index++);
            if (c == '"') return;
            if (c < 0x20) throw invalid();
            if (c == '\\') {
                if (index >= source.length()) throw invalid();
                char escaped = source.charAt(index++);
                if (escaped == 'u') {
                    for (int i = 0; i < 4; i++) {
                        if (index >= source.length()
                                || "0123456789abcdefABCDEF".indexOf(source.charAt(index++)) < 0)
                            throw invalid();
                    }
                } else if ("\"\\/bfnrt".indexOf(escaped) < 0) throw invalid();
            }
        }
        throw invalid();
    }

    private void number() {
        take('-');
        if (!take('0')) digits();
        if (take('.')) digits();
        if (take('e') || take('E')) {
            if (!take('+')) take('-');
            digits();
        }
    }

    private void digits() {
        int start = index;
        while (index < source.length()
                && source.charAt(index) >= '0'
                && source.charAt(index) <= '9') index++;
        if (start == index) throw invalid();
    }

    private void literal(String text) {
        if (!source.startsWith(text, index)) throw invalid();
        index += text.length();
    }

    private void expect(char c) {
        if (!take(c)) throw invalid();
    }

    private boolean take(char c) {
        if (index < source.length() && source.charAt(index) == c) {
            index++;
            return true;
        }
        return false;
    }

    private void space() {
        while (index < source.length() && " \r\n\t".indexOf(source.charAt(index)) >= 0) index++;
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid cached JSON");
    }
}
