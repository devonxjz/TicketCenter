package vn.ticketscenter.settlement.controller;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
}
