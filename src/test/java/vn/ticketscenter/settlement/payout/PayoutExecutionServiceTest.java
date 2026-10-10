package vn.ticketscenter.settlement.payout;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.settlement.infrastructure.FinanceException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PayoutExecutionServiceTest {
    final UUID settlement=UUID.randomUUID(), key=UUID.randomUUID();
    final UserPrincipal actor=new UserPrincipal(UUID.randomUUID(),PlatformRole.ADMIN,0);
    PayoutRequestDto request(String amount) { return new PayoutRequestDto(new BigDecimal(amount),"123456789","Test Bank","Test Holder","fixture"); }
    @Test void successfulReplayMakesOneProviderCall() throws Exception {
        var store=new Store(); int[] calls={0};
        PayoutAdapter adapter=(id,terms) -> { calls[0]++; return new PayoutAdapter.Result("SUCCEEDED","SIM-"+id); };
        var service=new PayoutExecutionService(store,adapter);
        assertEquals("SUCCEEDED",service.execute(actor,settlement,key,request("100")).get("status"));
        assertEquals("SUCCEEDED",service.execute(actor,settlement,key,request("100")).get("status"));
        assertEquals(1,calls[0]);
    }
    @Test void timeoutKeepsReservationAndReplayDoesNotTransferAgain() throws Exception {
        var store=new Store(); int[] calls={0};
        var service=new PayoutExecutionService(store,(id,terms) -> { calls[0]++; throw new java.io.IOException("bank secret"); });
        assertEquals("PENDING",service.execute(actor,settlement,key,request("100")).get("status"));
        assertEquals("UNKNOWN",store.state.get("verificationState"));
        service.execute(actor,settlement,key,request("100")); assertEquals(1,calls[0]);
    }
    @Test void differentTermsConflictBeforeProvider() throws Exception {
        var service=new PayoutExecutionService(new Store(),(id,t) -> new PayoutAdapter.Result("FAILED",null));
        service.execute(actor,settlement,key,request("100"));
        assertEquals(409,assertThrows(FinanceException.class,()->service.execute(actor,settlement,key,request("101"))).status());
    }
    @Test void unavailableProductionProviderDoesNotReserve() {
        var store=new Store(); var service=new PayoutExecutionService(store,PayoutAdapter.unavailable());
        assertEquals(503,assertThrows(FinanceException.class,()->service.execute(actor,settlement,key,request("100"))).status());
        assertNull(store.state);
    }
    @Test void storedReplayRemainsReadableWhenProviderIsUnavailable() throws Exception {
        var store=new Store(); new PayoutExecutionService(store,(id,t)->new PayoutAdapter.Result("SUCCEEDED","SIM_OK")).execute(actor,settlement,key,request("100"));
        assertEquals("SUCCEEDED",new PayoutExecutionService(store,PayoutAdapter.unavailable()).execute(actor,settlement,key,request("100")).get("status"));
    }
    @Test void wholePositiveVndAndBankTermsAreRequired() {
        for(String value:List.of("0","-1","1.1","10000000000000000000")) assertThrows(FinanceException.class,()->request(value));
        assertThrows(FinanceException.class,()->new PayoutRequestDto(BigDecimal.ONE,"","Bank","Holder",null));
        assertArrayEquals(request("100").fingerprint(settlement), request("100.0").fingerprint(settlement));
        assertFalse(Arrays.equals(request("100").fingerprint(settlement), request("101").fingerprint(settlement)));
    }
    // Models durable repository responses, not SQL acceptance or bank success.
    static class Store implements PayoutStore {
        Map<String,Object> state; byte[] hash;
        public Map<String,Object> existing(UserPrincipal actor,UUID sid,UUID id,PayoutRequestDto terms) {
            if(state==null) return null;
            if(!Arrays.equals(hash,terms.fingerprint(sid))) throw new FinanceException("IDEMPOTENCY_CONFLICT",409);
            var replay=new LinkedHashMap<>(state); replay.put("isReplay",true); return replay;
        }
        public Map<String,Object> reserve(UserPrincipal actor,UUID sid,UUID id,PayoutRequestDto terms) {
            byte[] next=terms.fingerprint(sid);
            if(state!=null) {
                if(!Arrays.equals(hash,next)) throw new FinanceException("IDEMPOTENCY_CONFLICT",409);
                return new LinkedHashMap<>(state) {{ put("isReplay",true); }};
            }
            hash=next; state=new LinkedHashMap<>(); state.put("payoutId",id.toString()); state.put("status","PENDING"); state.put("isReplay",false);
            return new LinkedHashMap<>(state);
        }
        public Map<String,Object> record(UserPrincipal actor,UUID sid,UUID id,PayoutRequestDto terms,PayoutAdapter.Result result) {
            state.put("status",result.status().equals("UNKNOWN")?"PENDING":result.status());
            state.put("verificationState",result.status()); return new LinkedHashMap<>(state);
        }
    }
}
