package vn.ticketscenter.fulfillment.refund.controller;

import vn.ticketscenter.fulfillment.refund.service.RefundException;

/** Reads bounded JSON strings without coercing other JSON types. */
final class RefundJsonReader {
    private final String source;
    private final int maxStringLength;
    private int position;

    RefundJsonReader(String source, int maxStringLength) {
        this.source = source;
        this.maxStringLength = maxStringLength;
    }

    String string() {
        expect('"');
        var result = new StringBuilder();
        while (position < source.length()) {
            char value = source.charAt(position++);
            if (value == '"') return result.toString();
            if (value < 0x20) throw RefundException.invalid();
            if (value == '\\') value = escaped();
            result.append(value);
            if (result.length() > maxStringLength) throw RefundException.invalid();
        }
        throw RefundException.invalid();
    }

    boolean takeNull() {
        space();
        if (!source.startsWith("null", position)) return false;
        position += 4;
        return true;
    }

    boolean take(char token) {
        space();
        if (position >= source.length() || source.charAt(position) != token) return false;
        position++;
        return true;
    }

    void expect(char token) {
        if (!take(token)) throw RefundException.invalid();
    }

    void finish() {
        space();
        if (position != source.length()) throw RefundException.invalid();
    }

    private char escaped() {
        if (position == source.length()) throw RefundException.invalid();
        return switch (source.charAt(position++)) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> unicode();
            default -> throw RefundException.invalid();
        };
    }

    private char unicode() {
        if (position + 4 > source.length()) throw RefundException.invalid();
        int value = 0;
        for (int i = 0; i < 4; i++) {
            char c = source.charAt(position++);
            int digit =
                    c >= '0' && c <= '9'
                            ? c - '0'
                            : c >= 'a' && c <= 'f'
                                    ? c - 'a' + 10
                                    : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
            if (digit < 0) throw RefundException.invalid();
            value = value * 16 + digit;
        }
        return (char) value;
    }

    private void space() {
        while (position < source.length() && " \t\r\n".indexOf(source.charAt(position)) >= 0) {
            position++;
        }
    }
}
