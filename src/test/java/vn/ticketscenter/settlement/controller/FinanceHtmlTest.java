package vn.ticketscenter.settlement.controller;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class FinanceHtmlTest {
    @Test void escapesLabelsAndAttributeDelimiters() {
        var id=UUID.randomUUID();
        String result=FinanceHtml.options(List.of(Map.of("id",id,"title","A & <script>\"'")),"title");
        assertEquals("<option value=\""+id+"\">A &amp; &lt;script&gt;&quot;&#39;</option>",result);
        assertEquals("",FinanceHtml.escape(null));
    }
    @Test void rejectsMalformedIdsInsteadOfRenderingInjectedMarkup() {
        assertThrows(IllegalArgumentException.class,()->FinanceHtml.options(List.of(Map.of("id","\" onmouseover=\"bad","title","label")),"title"));
    }
    @Test void readyEventsEscapeTitlesAndUseVietnamTime() {
        var id=UUID.randomUUID();
        String html=FinanceHtml.readySettlementRows(List.of(Map.of("id",id,"title","A <script>\"'", "endTime",Instant.parse("2026-10-14T01:00:00Z"))));
        assertTrue(html.contains("14/10/2026 08:00"));
        assertTrue(html.contains("A &lt;script&gt;&quot;&#39;"));
        assertTrue(html.contains("data-ready-event=\""+id+"\""));
        assertFalse(html.contains("<script>"));
        assertTrue(FinanceHtml.readySettlementRows(List.of()).contains("Chưa có sự kiện đã kết thúc đủ điều kiện quyết toán."));
    }
}
