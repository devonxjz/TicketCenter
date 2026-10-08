package vn.ticketscenter.config.web;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonObjectParser {

    private final String source;
    private int index;

    private JsonObjectParser(String source) {
        this.source = source;
    }

    public static Map<String, String> parse(Reader reader) throws IOException {
        StringBuilder source = new StringBuilder();
        char[] buffer = new char[1024];
        for (int count; (count = reader.read(buffer)) >= 0; ) source.append(buffer, 0, count);
        return new JsonObjectParser(source.toString()).object();
    }

    private Map<String, String> object() {
        Map<String, String> values = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (take('}')) return values;
        do {
            String key = string();
            expect(':');
            String value = value();
            if (values.putIfAbsent(key, value) != null) throw invalid();
            skipWhitespace();
        } while (take(','));
        expect('}');
        skipWhitespace();
        if (index != source.length()) throw invalid();
        return values;
    }

    private String value() {
        skipWhitespace();
        if (index < source.length() && source.charAt(index) == '"') return string();
        int start = index;
        char open = index < source.length() ? source.charAt(index) : 0;
        if (open != '[') throw invalid();
        char close = ']';
        int depth = 0;
        boolean quoted = false;
        while (index < source.length()) {
            char current = source.charAt(index++);
            if (current == '"' && (index < 2 || source.charAt(index - 2) != '\\')) quoted = !quoted;
            if (!quoted) {
                if (current == open) depth++;
                if (current == close && --depth == 0) return source.substring(start, index);
            }
        }
        throw invalid();
    }

    private String string() {
        skipWhitespace();
        expectRaw('"');
        StringBuilder value = new StringBuilder();
        while (index < source.length()) {
            char current = source.charAt(index++);
            if (current == '"') return value.toString();
            if (current < 0x20) throw invalid();
            if (current != '\\') {
                value.append(current);
                continue;
            }
            if (index >= source.length()) throw invalid();
            char escaped = source.charAt(index++);
            value.append(switch (escaped) {
                case '"', '\\', '/' -> escaped;
                case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                case 'u' -> unicode();
                default -> throw invalid();
            });
        }
        throw invalid();
    }

    private char unicode() {
        if (index + 4 > source.length()) throw invalid();
        try {
            char value = (char) Integer.parseInt(source.substring(index, index + 4), 16);
            index += 4;
            return value;
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private void expect(char expected) { skipWhitespace(); expectRaw(expected); }
    private void expectRaw(char expected) { if (index >= source.length() || source.charAt(index++) != expected) throw invalid(); }
    private boolean take(char expected) { skipWhitespace(); if (index < source.length() && source.charAt(index) == expected) { index++; return true; } return false; }
    private void skipWhitespace() { while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++; }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("invalid JSON object"); }
}
