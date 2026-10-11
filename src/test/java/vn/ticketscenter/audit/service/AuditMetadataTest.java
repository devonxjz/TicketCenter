package vn.ticketscenter.audit.service;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AuditMetadataTest {
    @Test void commissionRatesRetainFourDecimalsWithoutTreatingRatesAsVnd() {
        assertEquals(Map.of("ratePercent","4.5000","fixedFee","8000"),
                AuditMetadata.project("{\"ratePercent\":\"4.5000\",\"fixedFee\":\"8000\"}"));
        assertEquals(Map.of("ratePercent","0.1254"), AuditMetadata.project("{\"ratePercent\":0.1254}"));
        for(String rate : java.util.List.of("-1","100.0001","4.50001","secret"))
            assertFalse(AuditMetadata.project("{\"ratePercent\":\""+rate+"\"}").containsKey("ratePercent"));
    }
    @Test void keepsOnlyTypedApprovedFieldsNeverSecretsOrNestedPayload() {
        var id="00000000-0000-0000-0000-000000000001";
        var result=AuditMetadata.project("{\"eventId\":\""+id+"\",\"previousStatus\":\"DRAFT\",\"amount\":\"9999999999999999999\",\"otp\":\"123456\",\"token\":\"secret\",\"passwordHash\":\"hash\",\"providerPayload\":{\"key\":\"secret\"}}");
        assertEquals(Map.of("eventId",id,"previousStatus","DRAFT","amount","9999999999999999999"),result);
    }
    @Test void malformedAndWrongTypesFailClosed() {
        assertEquals(Map.of(),AuditMetadata.project("{broken"));
        assertEquals(Map.of(),AuditMetadata.project("{\"eventId\":\"<script>\",\"amount\":-1,\"newStatus\":\"secret text\"}"));
        assertEquals(Map.of(),AuditMetadata.project(null));
    }
    @Test void duplicateKeysAndExcessiveInputFailClosed() {
        assertEquals(Map.of(),AuditMetadata.project("{\"amount\":\"1\",\"amount\":\"2\"}"));
        assertEquals(Map.of(),AuditMetadata.project(" ".repeat(8001)));
    }
}
