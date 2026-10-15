package vn.ticketscenter.swagger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiContractTest {
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

    private JSONObject specification() throws Exception {
        try (InputStream source = getClass().getResourceAsStream("/openapi.json")) {
            assertNotNull(source);
            // JSONObject rejects duplicate keys, including duplicate paths in the source.
            return new JSONObject(new String(source.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyOperationHasUniqueIdOwnerAndImplementationStatus() throws Exception {
        JSONObject spec = specification();
        assertEquals("3.0.3", spec.getString("openapi"));
        Set<String> ids = new HashSet<>();
        JSONObject paths = spec.getJSONObject("paths");
        for (String path : paths.keySet()) {
            JSONObject item = paths.getJSONObject(path);
            for (String method : item.keySet()) {
                if (!METHODS.contains(method)) continue;
                JSONObject operation = item.getJSONObject(method);
                String id = operation.optString("operationId");
                assertFalse(id.isBlank(), method + " " + path + " has no operationId");
                assertTrue(ids.add(id), "Duplicate operationId: " + id);
                assertFalse(operation.optString("x-owner").isBlank(), "Missing owner: " + id);
                assertTrue(Set.of("implemented", "planned", "legacy").contains(operation.optString("x-implementation-status")), id);
                assertFalse(operation.getJSONObject("responses").isEmpty(), id);
            }
        }
        assertTrue(ids.size() >= 38, "Existing contracts must remain represented");
    }

    @Test
    void allLocalReferencesAndPathParametersResolve() throws Exception {
        JSONObject spec = specification();
        verifyReferences(spec, spec);
        JSONObject paths = spec.getJSONObject("paths");
        for (String path : paths.keySet()) {
            JSONObject item = paths.getJSONObject(path);
            for (String method : item.keySet()) {
                if (!METHODS.contains(method)) continue;
                JSONObject operation = item.getJSONObject(method);
                Set<String> names = new HashSet<>();
                JSONArray parameters = operation.optJSONArray("parameters");
                if (parameters != null) for (Object value : parameters) {
                    JSONObject parameter = (JSONObject) value;
                    if (parameter.has("$ref")) parameter = (JSONObject) spec.query(parameter.getString("$ref").substring(1));
                    if ("path".equals(parameter.optString("in"))) {
                        assertTrue(parameter.getBoolean("required"));
                        names.add(parameter.getString("name"));
                    }
                }
                var placeholders = java.util.regex.Pattern.compile("\\{([^}]+)}").matcher(path);
                while (placeholders.find()) assertTrue(names.contains(placeholders.group(1)), method + " " + path);
            }
        }
    }

    private void verifyReferences(Object node, JSONObject root) {
        if (node instanceof JSONObject object) {
            if (object.has("$ref")) {
                String reference = object.getString("$ref");
                assertTrue(reference.startsWith("#/"), "External references are not used: " + reference);
                assertNotNull(root.query(reference.substring(1)), reference);
            }
            for (String key : object.keySet()) verifyReferences(object.get(key), root);
        } else if (node instanceof JSONArray array) {
            for (Object value : array) verifyReferences(value, root);
        }
    }

    @Test
    void identityAndErrorSchemasMatchMergedServletContracts() throws Exception {
        JSONObject spec = specification();
        JSONObject schemas = spec.getJSONObject("components").getJSONObject("schemas");
        JSONObject register = schemas.getJSONObject("RegisterRequest");
        assertEquals(Set.of("userName", "fullName", "email", "password"), new HashSet<>(register.getJSONArray("required").toList()));
        JSONObject error = schemas.getJSONObject("ErrorResponse").getJSONObject("properties").getJSONObject("error").getJSONObject("properties");
        assertTrue(error.has("correlationId"));
        assertFalse(error.has("timestamp"), "HttpResponses emits correlationId, not timestamp");
        assertFalse(error.getJSONObject("code").optBoolean("writeOnly"), "Error code must remain visible in response documentation");
        JSONObject me = schemas.getJSONObject("CurrentUser").getJSONObject("properties");
        assertTrue(me.keySet().containsAll(Set.of("id", "email", "userName", "fullName", "platformRole", "admin")));
        JSONObject schemes = spec.getJSONObject("components").getJSONObject("securitySchemes");
        assertEquals("JSESSIONID", schemes.getJSONObject("cookieAuth").getString("name"));
        assertEquals("X-CSRF-Token", schemes.getJSONObject("csrfToken").getString("name"));
        assertEquals("Idempotency-Key", schemes.getJSONObject("idempotencyKey").getString("name"));
        JSONObject otpSend = spec.getJSONObject("paths").getJSONObject("/api/auth/otp/send").getJSONObject("post")
                .getJSONObject("responses").getJSONObject("200").getJSONObject("content").getJSONObject("application/json")
                .getJSONObject("schema").getJSONObject("properties").getJSONObject("data").getJSONObject("properties");
        assertTrue(otpSend.has("sent"), "OtpSendServlet emits data.sent, not data.message");
        assertEquals(Set.of("token", "newPassword"), new HashSet<>(schemas.getJSONObject("ResetPasswordRequest").getJSONArray("required").toList()));
    }

    @Test
    void examplesDoNotPrepopulateCredentialsOrTokens() throws Exception {
        verifySensitiveExamples(specification(), "");
    }

    @Test
    void publicContractsAndReadinessMatchTheirBoundaries() throws Exception {
        JSONObject paths = specification().getJSONObject("paths");
        for (String path : Set.of("/api/events", "/api/events/{eventId}", "/api/events/{eventId}/zones", "/api/zones/{zoneId}/seats", "/api/payments/payos/return", "/api/payments/payos/cancel")) {
            assertTrue(paths.getJSONObject(path).getJSONObject("get").getJSONArray("security").isEmpty(), path);
        }
        assertTrue(paths.getJSONObject("/api/payments/payos/webhook").getJSONObject("post").getJSONArray("security").isEmpty());
        JSONObject ready = paths.getJSONObject("/health/ready").getJSONObject("get").getJSONObject("responses");
        JSONObject ok = ready.getJSONObject("200").getJSONObject("content").getJSONObject("application/json");
        assertEquals("UP", ok.getJSONObject("example").getJSONObject("data").getString("status"));
        JSONObject unavailable = ready.getJSONObject("503").getJSONObject("content").getJSONObject("application/json").getJSONObject("schema");
        assertFalse(unavailable.has("$ref"));
        assertEquals(Set.of("code"), new HashSet<>(unavailable.getJSONObject("properties").getJSONObject("error").getJSONArray("required").toList()));
    }

    private void verifySensitiveExamples(Object node, String field) {
        if (node instanceof JSONObject object) {
            if (Set.of("password", "newPassword", "resetToken", "token", "Set-Cookie", "X-CSRF-Token").contains(field)) {
                assertFalse(object.has("example") || object.has("default") || object.has("examples"), "Sensitive example: " + field);
            }
            for (String key : object.keySet()) verifySensitiveExamples(object.get(key), key);
        } else if (node instanceof JSONArray array) {
            for (Object value : array) verifySensitiveExamples(value, field);
        }
    }
}
