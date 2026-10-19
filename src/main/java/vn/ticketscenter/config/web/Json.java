package vn.ticketscenter.config.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Json {
    private Json() {}

    public static String object(String... fields) {
        return Stream.of(fields).filter(Objects::nonNull).collect(Collectors.joining(",", "{", "}"));
    }

    public static String field(String name, String json) {
        return "\"" + name + "\":" + json;
    }

    public static String optional(String name, String value) {
        return value == null ? null : field(name, HttpResponses.jsonString(value));
    }

    public static String text(Object value) {
        return HttpResponses.jsonString(value == null ? "" : value.toString());
    }

    public static String nullableText(Object value) {
        return HttpResponses.jsonString(value == null ? null : value.toString());
    }

    public static String number(Object value) {
        return String.valueOf(value);
    }

    public static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).toString();
    }

    public static <T> String array(List<T> values, Function<T, String> encode) {
        return values.stream().map(encode).collect(Collectors.joining(",", "[", "]"));
    }
}
