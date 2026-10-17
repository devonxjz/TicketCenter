package vn.ticketscenter.event.dto;

import vn.ticketscenter.event.service.EventProblem;
import java.io.*;
import java.util.*;

/** Bounded flat command JSON. Accepts strings/numbers; duplicate/nested/unknown fields are rejected. */
public final class EventRequestParser {
    private final String source;
    private int index;

    private EventRequestParser(String source) {
        this.source = source;
    }

    public static Map<String, String> parse(Reader reader) throws IOException {
        StringBuilder data = new StringBuilder();
        char[] buffer = new char[2048];
        for (int n; (n = reader.read(buffer)) != -1; ) {
            if (data.length() + n > 65536)
                throw new EventProblem(413, "EVENT_INPUT_TOO_LARGE", "Dữ liệu sự kiện quá lớn.");
            data.append(buffer, 0, n);
        }
        return data.toString().isBlank()
                ? Map.of()
                : new EventRequestParser(data.toString()).object();
    }

    private Map<String, String> object() {
        Map<String, String> result = new LinkedHashMap<>();
        expect('{');
        space();
        if (!take('}')) {
            do {
                String key = string();
                expect(':');
                space();
                String value;
                if (index < source.length() && source.charAt(index) == '"') value = string();
                else {
                    int start = index;
                    while (index < source.length()
                            && "0123456789-eE+.".indexOf(source.charAt(index)) >= 0) index++;
                    value = source.substring(start, index);
                    if (!value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))
                        throw invalid();
                }
                if (result.putIfAbsent(key, value) != null) throw invalid();
                space();
            } while (take(','));
            expect('}');
        }
        space();
        if (index != source.length()) throw invalid();
        return Map.copyOf(result);
    }

    private String string() {
        space();
        expectRaw('"');
        StringBuilder result = new StringBuilder();
        while (index < source.length()) {
            char c = source.charAt(index++);
            if (c == '"') return result.toString();
            if (c < 0x20) throw invalid();
            if (c != '\\') {
                result.append(c);
                continue;
            }
            if (index == source.length()) throw invalid();
            char escape = source.charAt(index++);
            switch (escape) {
                case '"', '\\', '/' -> result.append(escape);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (index + 4 > source.length()) throw invalid();
                    int value = 0;
                    for (int i = 0; i < 4; i++) {
                        char hex = source.charAt(index++);
                        if ("0123456789abcdefABCDEF".indexOf(hex) < 0) throw invalid();
                        value = value * 16 + Character.digit(hex, 16);
                    }
                    result.append((char) value);
                }
                default -> throw invalid();
            }
        }
        throw invalid();
    }

    private void expect(char c) {
        space();
        expectRaw(c);
    }

    private void expectRaw(char c) {
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
        while (index < source.length() && " \t\r\n".indexOf(source.charAt(index)) >= 0) index++;
    }

    private EventProblem invalid() {
        return EventProblem.invalid("JSON không hợp lệ hoặc có trường lặp.");
    }
}
