package vn.ticketscenter.swagger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundSummaryDto;
import static org.junit.jupiter.api.Assertions.*;

class AdminRefundOpenApiContractTest {
    private JSONObject spec()throws Exception{try(var source=getClass().getResourceAsStream("/openapi.json")){assertNotNull(source);return new JSONObject(new String(source.readAllBytes(),StandardCharsets.UTF_8));}}
    @Test void allFourAdminOperationsDescribeTypedDataEnvelopeAndSecurityErrors()throws Exception{
        var paths=spec().getJSONObject("paths");
        for(String suffix:List.of("","/{refundId}","/{refundId}/decision","/{refundId}/retry")){
            var op=paths.getJSONObject("/api/admin/refund-requests"+suffix).getJSONObject(suffix.endsWith("decision")||suffix.endsWith("retry")?"post":"get");
            assertEquals("implemented",op.getString("x-implementation-status"));assertEquals("khanh",op.getString("x-owner"));
            var responses=op.getJSONObject("responses");assertTrue(responses.has("401")&&responses.has("403"));
            var envelope=responses.getJSONObject("200").getJSONObject("content").getJSONObject("application/json").getJSONObject("schema");
            assertEquals(Set.of("data"),new HashSet<>(envelope.getJSONArray("required").toList()));
            if(suffix.endsWith("decision")||suffix.endsWith("retry")){assertTrue(responses.has("409"));assertTrue(op.has("requestBody"));}
            if(suffix.endsWith("retry"))assertFalse(op.getJSONObject("requestBody").getBoolean("required"),"Explicit retry can use an empty POST body");
        }
    }
    @Test void actualSummaryMatchesDocumentedNamesAndPreservesUuidIntegerMoneyUtc()throws Exception{
        UUID id=UUID.randomUUID();var dto=new AdminRefundSummaryDto(id,id,id,"Khánh","a@example.test","Sự kiện","9007199254740993",1,"Lý do","REQUESTED",Instant.parse("2026-10-12T00:00:00Z"),null,true,false);
        var json=new JSONObject(dto.json());var props=spec().getJSONObject("components").getJSONObject("schemas").getJSONObject("AdminRefundSummary").getJSONObject("properties");
        assertEquals(props.keySet(),json.keySet());assertEquals(id.toString(),json.getString("refundId"));assertEquals("9007199254740993",json.get("totalAmount"));assertEquals("2026-10-12T00:00:00Z",json.getString("createdAt"));
    }
}
