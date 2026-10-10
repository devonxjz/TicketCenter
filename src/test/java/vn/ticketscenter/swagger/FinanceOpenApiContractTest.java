package vn.ticketscenter.swagger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class FinanceOpenApiContractTest {
    JSONObject document() throws Exception {
        try(var source=getClass().getResourceAsStream("/openapi.json")) { return new JSONObject(new String(source.readAllBytes(),StandardCharsets.UTF_8)); }
    }
    @Test void actualMutationRoutesAreEnabledAndDocumentExactDtos() throws Exception {
        var doc=document(); var paths=doc.getJSONObject("paths");
        assertEquals("implemented",paths.getJSONObject("/api/admin/events/{eventId}/settlement").getJSONObject("get").getString("x-implementation-status"));
        assertEquals("implemented",paths.getJSONObject("/api/admin/commission-rules/{ruleId}/edit").getJSONObject("post").getString("x-implementation-status"));
        assertTrue(paths.has("/api/admin/settlements/{settlementId}/payouts/{payoutId}/reconcile"));
        var schemas=doc.getJSONObject("components").getJSONObject("schemas");
        assertEquals("#/components/schemas/Money",schemas.getJSONObject("CommissionRuleRequest").getJSONObject("properties").getJSONObject("fixedFee").getString("$ref"));
        assertEquals("#/components/schemas/Money",schemas.getJSONObject("PayoutRequest").getJSONObject("properties").getJSONObject("amount").getString("$ref"));
        assertEquals("string",schemas.getJSONObject("Money").getString("type"));
        assertTrue(schemas.getJSONObject("SettlementSummary").getJSONObject("properties").getJSONObject("settlementId").getBoolean("nullable"));
    }
    @Test void absentOwnerBusinessRoutesRemainPlanned() throws Exception {
        assertEquals("planned",document().getJSONObject("paths").getJSONObject("/api/events").getJSONObject("get").getString("x-implementation-status"));
    }
    @Test void deliveredAdminReadsideRoutesMatchSourceAndOrganizationOverviewStaysPlanned() throws Exception {
        var paths=document().getJSONObject("paths");
        for(String path:java.util.List.of("/api/admin/reports","/api/organizations/{organizationId}/reports","/api/reports/export","/api/reports/options","/api/admin/audit-logs","/api/admin/audit-logs/{auditId}","/api/admin/overview","/api/organizations/{organizationId}/overview","/api/admin/commission-rules"))
            assertEquals(path.equals("/api/organizations/{organizationId}/overview")?"planned":"implemented",paths.getJSONObject(path).getJSONObject("get").getString("x-implementation-status"),path);
        for(String path:java.util.List.of("/api/admin/overview","/api/admin/audit-logs","/api/admin/audit-logs/{auditId}"))
            assertEquals("matched-to-source",paths.getJSONObject(path).getJSONObject("get").getString("x-contract-status"));
        var export=paths.getJSONObject("/api/reports/export").getJSONObject("get");
        assertTrue(export.getJSONObject("responses").getJSONObject("200").getJSONObject("content").has("text/csv"));
        assertTrue(export.getString("description").contains("TAB"));
        var reconcile=paths.getJSONObject("/api/admin/settlements/{settlementId}/payouts/{payoutId}/reconcile").getJSONObject("post");
        assertTrue(reconcile.getString("description").contains("{}"));
    }
}
