package vn.ticketscenter.report.csv;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CsvSanitizerTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("ordinaryValues")
    void quotesOrdinaryValues(String reason, String input, String expected) {
        assertEquals(expected, CsvSanitizer.escapeCsvCell(input));
    }

    static Stream<Arguments> ordinaryValues() {
        return Stream.of(
                Arguments.of("null", null, "\"\""),
                Arguments.of("empty", "", "\"\""),
                Arguments.of("Vietnamese and emoji", "Đêm nhạc 🎵", "\"Đêm nhạc 🎵\""),
                Arguments.of("edge spaces", "  Hà Nội  ", "\"  Hà Nội  \""),
                Arguments.of("spaces only", "   ", "\"   \""),
                Arguments.of("internal equals", "note=1+1", "\"note=1+1\""),
                Arguments.of("literal apostrophe", "'=1+1", "\"'=1+1\""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("formulaPrefixes")
    void neutralizesFormulaPrefixes(String reason, String input, String expected) {
        assertEquals(expected, CsvSanitizer.escapeCsvCell(input));
    }

    static Stream<Arguments> formulaPrefixes() {
        return Stream.of(
                Arguments.of("equals", "=1+1", "\"\t=1+1\""),
                Arguments.of("plus", "+1+1", "\"\t+1+1\""),
                Arguments.of("minus", "-1+1", "\"\t-1+1\""),
                Arguments.of("at", "@SUM(A1)", "\"\t@SUM(A1)\""),
                Arguments.of("full-width equals", "＝1+1", "\"\t＝1+1\""),
                Arguments.of("full-width plus", "＋1+1", "\"\t＋1+1\""),
                Arguments.of("full-width minus", "－1+1", "\"\t－1+1\""),
                Arguments.of("full-width at", "＠SUM(A1)", "\"\t＠SUM(A1)\""),
                Arguments.of("spaces before formula", "  =1+1 ", "\"\t  =1+1 \""),
                Arguments.of("NBSP before formula", "\u00A0=1+1", "\"\t\u00A0=1+1\""),
                Arguments.of("zero-width before formula", "\u200B=1+1", "\"\t\u200B=1+1\""),
                Arguments.of("BOM before formula", "\uFEFF=1+1", "\"\t\uFEFF=1+1\""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("controlPrefixes")
    void neutralizesControlPrefixes(String reason, String input, String expected) {
        assertEquals(expected, CsvSanitizer.escapeCsvCell(input));
    }

    static Stream<Arguments> controlPrefixes() {
        return Stream.of(
                Arguments.of("tab", "\t=1+1", "\"\t\t=1+1\""),
                Arguments.of("CR", "\r=1+1", "\"\t\r=1+1\""),
                Arguments.of("LF", "\n=1+1", "\"\t\n=1+1\""),
                Arguments.of("space then tab and ordinary text", " \tabc", "\"\t \tabc\""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("separatorsAndQuotes")
    void escapesSeparatorsQuotesAndMultiline(String reason, String input, String expected) {
        assertEquals(expected, CsvSanitizer.escapeCsvCell(input));
    }

    static Stream<Arguments> separatorsAndQuotes() {
        return Stream.of(
                Arguments.of("comma and quote", "a,b\"c", "\"a,b\"\"c\""),
                Arguments.of("semicolon", "a;b", "\"a;b\""),
                Arguments.of("LF", "a\nb", "\"a\nb\""),
                Arguments.of("CR", "a\rb", "\"a\rb\""),
                Arguments.of("CRLF", "a\r\nb", "\"a\r\nb\""),
                Arguments.of("formula with cell separators", "=1+1\";,=1+1", "\"\t=1+1\"\";,=1+1\""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidValues")
    void rejectsInvalidUnicodeOrNul(String reason, String input) {
        var error = assertThrows(IllegalArgumentException.class, () -> CsvSanitizer.escapeCsvCell(input));
        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().contains(input), "Error must not echo invalid cell data");
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("lone high surrogate", "\uD83C"),
                Arguments.of("lone low surrogate", "\uDFB5"),
                Arguments.of("NUL at start", "\u0000abc"),
                Arguments.of("NUL in middle", "a\u0000b"),
                Arguments.of("NUL at end", "abc\u0000"));
    }
}
