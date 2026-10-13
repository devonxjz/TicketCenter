package vn.ticketscenter.swagger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInEventSummaryDto;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInWindowStatusDto;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CheckInOpenApiContractTest {
    private JSONObject spec() throws Exception {
        try (var source = getClass().getResourceAsStream("/openapi.json")) {
            assertNotNull(source);
            return new JSONObject(new String(source.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private JSONObject get(JSONObject spec, String path) {
        return spec.getJSONObject("paths").getJSONObject(path).getJSONObject("get");
    }

    private JSONObject data(JSONObject operation) {
        return operation.getJSONObject("responses").getJSONObject("200")
                .getJSONObject("content").getJSONObject("application/json").getJSONObject("schema")
                .getJSONObject("properties").getJSONObject("data");
    }

    @Test void deliveredReadRoutesScanningAndAdminRefundAreEnabled() throws Exception {
        var spec = spec();
        for (String path : Set.of("/api/check-ins/events", "/api/events/{eventId}/check-in-window")) {
            var operation = get(spec, path);
            assertEquals("implemented", operation.getString("x-implementation-status"), path);
            assertEquals("matched-to-source", operation.getString("x-contract-status"));
            assertEquals("khanh", operation.getString("x-owner"));
            assertTrue(operation.getJSONArray("security").getJSONObject(0).has("cookieAuth"));
            assertTrue(operation.getJSONObject("responses").keySet().containsAll(Set.of("200", "400", "401", "403", "503")));
        }
        assertEquals("implemented", spec.getJSONObject("paths").getJSONObject("/api/check-ins").getJSONObject("post").getString("x-implementation-status"));
        assertEquals("implemented", get(spec, "/api/admin/refund-requests").getString("x-implementation-status"));
    }

    @Test void eventArrayAndScopeMatchTheActualSerializedProjection() throws Exception {
        var spec = spec();
        var operation = get(spec, "/api/check-ins/events");
        assertEquals("array", data(operation).getString("type"));
        var schema = (JSONObject) spec.query(data(operation).getJSONObject("items").getString("$ref").substring(1));
        var actual = new JSONObject(new CheckInEventSummaryDto(UUID.randomUUID(), "Nhạc \"Việt\"", "Nhà hát",
                Instant.EPOCH, Instant.EPOCH.plusSeconds(7200), Instant.EPOCH.minusSeconds(3600), true, 5, 2, "OPEN").toJson());
        assertProjection(schema, actual);
        var properties = schema.getJSONObject("properties");
        for (String counter : Set.of("totalTicketsIssued", "totalCheckedIn")) {
            assertEquals("integer", properties.getJSONObject(counter).getString("type"));
            assertEquals("int64", properties.getJSONObject(counter).getString("format"));
        }
        var parameter = operation.getJSONArray("parameters").getJSONObject(0);
        assertEquals("organizationId", parameter.getString("name"));
        assertEquals("query", parameter.getString("in"));
        assertFalse(parameter.getBoolean("required"));
        assertEquals("uuid", parameter.getJSONObject("schema").getString("format"));
        assertFalse(properties.keySet().stream().anyMatch(k -> Set.of("paidAmount", "revenue", "price").contains(k)));
    }

    @Test void windowProjectionAndCancelledErrorMatchActualBehavior() throws Exception {
        var spec = spec();
        var operation = get(spec, "/api/events/{eventId}/check-in-window");
        var schema = (JSONObject) spec.query(data(operation).getString("$ref").substring(1));
        var actual = new JSONObject(new CheckInWindowStatusDto(UUID.randomUUID(), true, "OPEN",
                Instant.EPOCH.minusSeconds(3600), Instant.EPOCH.plusSeconds(7200), Instant.EPOCH, "Đang mở").toJson());
        assertProjection(schema, actual);
        var statuses = new HashSet<>(schema.getJSONObject("properties").getJSONObject("status").getJSONArray("enum").toList());
        assertEquals(Set.of("OPEN", "NOT_YET_OPEN", "ENDED", "UNAVAILABLE"), statuses);
        assertTrue(operation.getJSONObject("responses").getJSONObject("400").getString("description").contains("EVENT_CANCELLED"));
        assertTrue(operation.getJSONObject("responses").has("404"));
    }

    private void assertProjection(JSONObject schema, JSONObject actual) {
        var properties = schema.getJSONObject("properties");
        assertEquals(actual.keySet(), properties.keySet(), "Consumer schema must match the real DTO JSON keys");
        assertEquals(actual.keySet(), new HashSet<>(schema.getJSONArray("required").toList()));
        for (String key : actual.keySet()) {
            var field = properties.getJSONObject(key);
            Object value = actual.get(key);
            switch (field.getString("type")) {
                case "string" -> assertInstanceOf(String.class, value, key);
                case "boolean" -> assertInstanceOf(Boolean.class, value, key);
                case "integer" -> assertInstanceOf(Number.class, value, key);
                default -> fail("Unexpected projection type for " + key);
            }
            if ("uuid".equals(field.optString("format"))) UUID.fromString(actual.getString(key));
            if ("date-time".equals(field.optString("format"))) Instant.parse(actual.getString(key));
            if (field.has("enum")) assertTrue(field.getJSONArray("enum").toList().contains(value), key);
        }
    }
}
