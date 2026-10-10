package vn.ticketscenter.event.controller;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.*;

import org.junit.jupiter.api.Test;

import vn.ticketscenter.event.EventException;
import vn.ticketscenter.event.dto.*;

import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

class EventMultipartParserTest {
    private static Part part(String name, String value) {
        return part(name, value.getBytes(StandardCharsets.UTF_8), null);
    }

    private static Part part(String name, byte[] value, String filename) {
        return (Part)
                Proxy.newProxyInstance(
                        Part.class.getClassLoader(),
                        new Class[] {Part.class},
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getName" -> name;
                                    case "getSubmittedFileName" -> filename;
                                    case "getSize" -> (long) value.length;
                                    case "getInputStream" -> new ByteArrayInputStream(value);
                                    default -> null;
                                });
    }

    private static HttpServletRequest request(List<Part> parts, String key) {
        return (HttpServletRequest)
                Proxy.newProxyInstance(
                        HttpServletRequest.class.getClassLoader(),
                        new Class[] {HttpServletRequest.class},
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getContentType" ->
                                            "multipart/form-data; boundary=fixture";
                                    case "getHeader" -> key;
                                    case "getParts" -> parts;
                                    default -> null;
                                });
    }

    private static List<Part> fields() {
        return new ArrayList<>(
                List.of(
                        part("title", "My Event"),
                        part("description", "Description"),
                        part("categoryId", UUID.randomUUID().toString()),
                        part("venueName", "Venue"),
                        part("venueAddress", "Address"),
                        part("saleStart", "2027-01-01T00:00:00Z"),
                        part("saleEnd", "2027-01-02T00:00:00Z"),
                        part("startTime", "2027-01-03T00:00:00Z"),
                        part("endTime", "2027-01-04T00:00:00Z")));
    }

    @Test
    void emptyBrowserFilePartKeepsCover() throws Exception {
        List<Part> p = fields();
        p.add(part("file", new byte[0], ""));
        p.add(part("expectedVersion", "2"));
        EventSave command =
                EventMultipartParser.parse(
                        request(p, UUID.randomUUID().toString()),
                        EventSave.Kind.EDIT,
                        UUID.randomUUID());
        assertNull(command.image());
        assertEquals(2, command.expectedVersion());
    }

    @Test
    void duplicateFieldIsRejected() {
        List<Part> p = fields();
        p.add(part("title", "Other"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EventMultipartParser.parse(
                                request(p, UUID.randomUUID().toString()),
                                EventSave.Kind.CREATE,
                                UUID.randomUUID()));
    }

    @Test
    void unknownFieldIsRejected() {
        List<Part> p = fields();
        p.add(part("status", "PUBLISHED"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EventMultipartParser.parse(
                                request(p, UUID.randomUUID().toString()),
                                EventSave.Kind.CREATE,
                                UUID.randomUUID()));
    }

    @Test
    void missingKeyIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EventMultipartParser.parse(
                                request(fields(), null), EventSave.Kind.CREATE, UUID.randomUUID()));
    }

    @Test
    void malformedEventTimeIsRejectedAsInvalidInput() {
        List<Part> parts = fields();
        parts.removeIf(part -> part.getName().equals("saleStart"));
        parts.add(part("saleStart", "not-a-time"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EventMultipartParser.parse(
                                request(parts, UUID.randomUUID().toString()),
                                EventSave.Kind.CREATE,
                                UUID.randomUUID()));
    }

    @Test
    void spoofedImageBytesAreRejected() {
        List<Part> p = fields();
        p.add(
                part(
                        "file",
                        "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8),
                        "image.png"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EventMultipartParser.parse(
                                request(p, UUID.randomUUID().toString()),
                                EventSave.Kind.CREATE,
                                UUID.randomUUID()));
    }

    @Test
    void oversizedImageReturns413() {
        List<Part> p = fields();
        p.add(part("file", new byte[5_242_881], "image.png"));
        assertEquals(
                413,
                assertThrows(
                                EventException.class,
                                () ->
                                        EventMultipartParser.parse(
                                                request(p, UUID.randomUUID().toString()),
                                                EventSave.Kind.CREATE,
                                                UUID.randomUUID()))
                        .status());
    }

    @Test
    void selectedEmptyImageIsRejectedInsteadOfKeepingPreviousCover() {
        List<Part> parts = fields();
        parts.add(part("file", new byte[0], "empty.png"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EventMultipartParser.parse(
                                request(parts, UUID.randomUUID().toString()),
                                EventSave.Kind.CREATE,
                                UUID.randomUUID()));
    }

    @Test
    void compactUuidAndNegativeVersionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EventMultipartParser.id("1-1-1-1-1"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EventMultipartParser.parse(
                                request(
                                        List.of(part("expectedVersion", "-1")),
                                        UUID.randomUUID().toString()),
                                EventSave.Kind.COVER,
                                UUID.randomUUID()));
    }

    @Test
    void categoryOrScheduleCannotBeSmuggledIntoCover() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EventMultipartParser.parse(
                                request(
                                        List.of(
                                                part("expectedVersion", "0"),
                                                part("title", "Changed")),
                                        UUID.randomUUID().toString()),
                                EventSave.Kind.COVER,
                                UUID.randomUUID()));
    }
}
