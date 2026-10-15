package vn.ticketscenter.identity.controller.support;

import vn.ticketscenter.config.web.HttpResponses;

/** Builds a flat JSON object with every string value escaped. */
public final class JsonObjectBuilder {

    private final StringBuilder json = new StringBuilder("{");
    private boolean empty = true;

    public static JsonObjectBuilder object() {
        return new JsonObjectBuilder();
    }

    public JsonObjectBuilder put(String name, String value) {
        return raw(name, HttpResponses.jsonString(value));
    }

    public JsonObjectBuilder put(String name, boolean value) {
        return raw(name, Boolean.toString(value));
    }

    public JsonObjectBuilder put(String name, Object value) {
        return put(name, value == null ? null : value.toString());
    }

    private JsonObjectBuilder raw(String name, String encodedValue) {
        if (!empty) {
            json.append(',');
        }
        empty = false;
        json.append(HttpResponses.jsonString(name)).append(':').append(encodedValue);
        return this;
    }

    public String build() {
        return json + "}";
    }
}
