package vn.ticketscenter.identity.controller.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonObjectBuilderTest {

    @Test
    void buildsFlatObjectInInsertionOrder() {
        String json = JsonObjectBuilder.object().put("registered", true).put("otpSent", false).build();
        assertEquals("{\"registered\":true,\"otpSent\":false}", json);
    }

    @Test
    void escapesStringValues() {
        String json = JsonObjectBuilder.object().put("message", "a \"quoted\"\nline").build();
        assertEquals("{\"message\":\"a \\\"quoted\\\"\\nline\"}", json);
    }

    @Test
    void emptyObjectAndNullValue() {
        assertEquals("{}", JsonObjectBuilder.object().build());
        assertEquals("{\"x\":null}", JsonObjectBuilder.object().put("x", (String) null).build());
    }
}
