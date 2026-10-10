package vn.ticketscenter.settlement.controller;

import java.io.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.settlement.commission.dto.CommissionRuleCreateDto;
import vn.ticketscenter.settlement.infrastructure.FinanceException;
import vn.ticketscenter.settlement.payout.PayoutRequestDto;

public final class FinanceRequest {
    private FinanceRequest() {}
    public static UUID id(String value) {
        if(value==null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw invalid();
        return UUID.fromString(value);
    }
    public static Instant instant(String value) {
        try {
            if(value==null || value.length()>30 || !value.endsWith("Z")) throw invalid();
            Instant result=Instant.parse(value); if(result.getNano()%1_000_000!=0) throw invalid(); return result;
        } catch(RuntimeException failure) { throw invalid(); }
    }
    public static Map<String,String> body(Reader reader) throws IOException {
        var source=new StringBuilder(); char[] chunk=new char[1024];
        for(int count;(count=reader.read(chunk))!=-1;) {
            if(source.length()+count>65536) throw new FinanceException("PAYLOAD_TOO_LARGE",413);
            source.append(chunk,0,count);
        }
        try { return JsonObjectParser.parse(new StringReader(source.toString())); }
        catch(IllegalArgumentException failure) { throw invalid(); }
    }
    public static CommissionRuleCreateDto commission(Map<String,String> values) {
        fields(values,Set.of("ratePercent","fixedFee","effectiveFrom","effectiveTo","organizationId"));
        try {
            String fixed=values.get("fixedFee"); if(fixed==null || !fixed.matches("[0-9]{1,19}")) throw invalid();
            return new CommissionRuleCreateDto(decimal(values.get("ratePercent")),Long.parseLong(fixed),instant(values.get("effectiveFrom")),instant(values.get("effectiveTo")));
        } catch(RuntimeException failure) { throw new FinanceException("COMMISSION_RULE_INVALID",400); }
    }
    public static PayoutRequestDto payout(Map<String,String> values) {
        fields(values,Set.of("amount","bankAccountNumber","bankName","accountHolderName","note"));
        String raw=values.get("amount"); if(raw==null || !raw.matches("[0-9]{1,19}")) throw new FinanceException("PAYOUT_INVALID",400);
        return new PayoutRequestDto(decimal(raw),values.get("bankAccountNumber"),values.get("bankName"),values.get("accountHolderName"),values.get("note"));
    }
    private static BigDecimal decimal(String value) {
        if(value==null || !value.matches("[0-9]{1,19}(\\.[0-9]{1,4})?")) throw invalid();
        return new BigDecimal(value);
    }
    private static void fields(Map<String,String> values,Set<String> allowed) { if(!allowed.containsAll(values.keySet())) throw invalid(); }
    private static FinanceException invalid() { return new FinanceException("REQUEST_INVALID",400); }
}
