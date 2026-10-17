package vn.ticketscenter.event.dto;

import vn.ticketscenter.config.web.JsonObjectParser;
import java.io.StringReader;
import java.util.Optional;
import java.util.regex.Pattern;

/** Decode a scalar string in an internal JSON projection (including SQL Server's escaped slashes). */
public final class EventResponseValues {
    private EventResponseValues() {}

    public static Optional<String> string(String json, String field) {
        var match =
                Pattern.compile(
                                "\""
                                        + Pattern.quote(field)
                                        + "\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\")")
                        .matcher(json);
        if (!match.find()) return Optional.empty();
        try {
            return Optional.ofNullable(
                    JsonObjectParser.parse(new StringReader("{\"value\":" + match.group(1) + "}"))
                            .get("value"));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid internal Event projection");
        }
    }
}
