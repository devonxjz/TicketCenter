package vn.ticketscenter.settlement.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Escapes the small server-rendered finance option lists without a JSTL dependency. */
public final class FinanceHtml {
    private FinanceHtml() {}
    public static String escape(Object value) {
        if(value==null)return "";
        return value.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
    public static String options(Object value,String labelKey) {
        if(!(value instanceof List<?> rows))throw new IllegalArgumentException("Expected option list");
        StringBuilder html=new StringBuilder();
        for(Object row:rows) {
            if(!(row instanceof Map<?,?> option))throw new IllegalArgumentException("Expected option row");
            Object rawId=option.get("id");
            String id=rawId==null?"":rawId.toString();
            if(id.length()!=36 || !UUID.fromString(id).toString().equalsIgnoreCase(id))throw new IllegalArgumentException("Expected UUID option");
            html.append("<option value=\"").append(id).append("\">").append(escape(option.get(labelKey))).append("</option>");
        }
        return html.toString();
    }
}
