package vn.ticketscenter.ticketing.controller;

import vn.ticketscenter.config.error.AppException;

import vn.ticketscenter.ticketing.dto.CreateHoldRequest;
import vn.ticketscenter.ticketing.dto.HoldSelection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

public final class HoldRequestParser {
    public static final int MAX_BODY_BYTES = 64 * 1024;

    public enum Failure {
        INVALID_BODY,
        BODY_TOO_LARGE
    }

    public static final class ParseException extends AppException {
        private final Failure failure;

        private ParseException(Failure failure) {
            super(
                    failure == Failure.BODY_TOO_LARGE ? 413 : 400,
                    failure == Failure.BODY_TOO_LARGE ? "BODY_TOO_LARGE" : "INVALID_BODY",
                    failure == Failure.BODY_TOO_LARGE
                            ? "Hold request body is too large"
                            : "Invalid hold request body");
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }

    public static CreateHoldRequest parseCreate(InputStream input) {
        Cursor cursor = read(input);
        try {
            cursor.expect('{');
            UUID event = null;
            ArrayList<HoldSelection> selections = null;
            var keys = new HashSet<String>();
            if (!cursor.consume('}')) {
                do {
                    String key = cursor.string();
                    if (!keys.add(key)) throw invalid();
                    cursor.expect(':');
                    switch (key) {
                        case "eventId" -> event = cursor.uuid();
                        case "selections" -> selections = cursor.selections();
                        default -> throw invalid();
                    }
                } while (cursor.consume(','));
                cursor.expect('}');
            }
            cursor.end();
            return new CreateHoldRequest(event, selections);
        } catch (IllegalArgumentException invalidDto) {
            throw invalid();
        }
    }

    public static void parseCancel(InputStream input) {
        Cursor cursor = read(input);
        cursor.expect('{');
        cursor.expect('}');
        cursor.end();
    }

    private static Cursor read(InputStream input) {
        if (input == null) throw invalid();
        try {
            byte[] body = input.readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) throw new ParseException(Failure.BODY_TOO_LARGE);
            String text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(body))
                            .toString();
            return new Cursor(text);
        } catch (CharacterCodingException malformed) {
            throw invalid();
        } catch (IOException unavailable) {
            throw invalid();
        }
    }

    private static ParseException invalid() {
        return new ParseException(Failure.INVALID_BODY);
    }

    private static final class Cursor {
        private final String text;
        private int position;

        private Cursor(String text) {
            this.text = text;
        }

        private void whitespace() {
            while (position < text.length() && " \t\r\n".indexOf(text.charAt(position)) >= 0)
                position++;
        }

        private boolean consume(char token) {
            whitespace();
            if (position < text.length() && text.charAt(position) == token) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char token) {
            if (!consume(token)) throw invalid();
        }

        private void end() {
            whitespace();
            if (position != text.length()) throw invalid();
        }

        private String string() {
            expect('"');
            var value = new StringBuilder();
            boolean closed = false;
            while (position < text.length()) {
                char character = text.charAt(position++);
                if (character == '"') {
                    closed = true;
                    break;
                }
                if (character < 32) throw invalid();
                if (character == '\\') {
                    if (position == text.length()) throw invalid();
                    char escaped = text.charAt(position++);
                    character =
                            switch (escaped) {
                                case '"', '\\', '/' -> escaped;
                                case 'b' -> '\b';
                                case 'f' -> '\f';
                                case 'n' -> '\n';
                                case 'r' -> '\r';
                                case 't' -> '\t';
                                case 'u' -> unicode();
                                default -> throw invalid();
                            };
                }
                value.append(character);
            }
            if (!closed) throw invalid();
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (Character.isHighSurrogate(character)) {
                    if (++index == value.length() || !Character.isLowSurrogate(value.charAt(index)))
                        throw invalid();
                } else if (Character.isLowSurrogate(character)) throw invalid();
            }
            return value.toString();
        }

        private char unicode() {
            if (text.length() - position < 4) throw invalid();
            int value = 0;
            for (int index = 0; index < 4; index++) {
                char digit = text.charAt(position++);
                int hex =
                        digit >= '0' && digit <= '9'
                                ? digit - '0'
                                : digit >= 'a' && digit <= 'f'
                                        ? digit - 'a' + 10
                                        : digit >= 'A' && digit <= 'F' ? digit - 'A' + 10 : -1;
                if (hex < 0) throw invalid();
                value = value * 16 + hex;
            }
            return (char) value;
        }

        private UUID uuid() {
            String value = string();
            if (!value.matches(
                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                throw invalid();
            return UUID.fromString(value);
        }

        private int quantity() {
            whitespace();
            if (position == text.length()
                    || text.charAt(position) < '1'
                    || text.charAt(position) > '8') throw invalid();
            return text.charAt(position++) - '0';
        }

        private ArrayList<HoldSelection> selections() {
            expect('[');
            var selections = new ArrayList<HoldSelection>();
            if (!consume(']')) {
                do {
                    if (selections.size() == 8) throw invalid();
                    expect('{');
                    UUID zone = null;
                    UUID seat = null;
                    int quantity = 0;
                    var keys = new HashSet<String>();
                    if (!consume('}')) {
                        do {
                            String key = string();
                            if (!keys.add(key)) throw invalid();
                            expect(':');
                            switch (key) {
                                case "zoneId" -> zone = uuid();
                                case "seatId" -> {
                                    whitespace();
                                    if (text.startsWith("null", position)) position += 4;
                                    else seat = uuid();
                                }
                                case "quantity" -> quantity = quantity();
                                default -> throw invalid();
                            }
                        } while (consume(','));
                        expect('}');
                    }
                    selections.add(new HoldSelection(zone, seat, quantity));
                } while (consume(','));
                expect(']');
            }
            return selections;
        }
    }
}
