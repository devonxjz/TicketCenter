package vn.ticketscenter.fulfillment.refund.service;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.refund.cache.RefundRequestCache;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.dao.RefundRequestStore;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.model.PlatformRole;
import static org.junit.jupiter.api.Assertions.*;

class RefundRequestServiceTest {
    final UserPrincipal actor=new UserPrincipal(UUID.randomUUID(),PlatformRole.CUSTOMER,0);
    final UUID order=UUID.randomUUID(),ticket=UUID.randomUUID();
    final RefundSummaryDto result=new RefundSummaryDto(UUID.randomUUID(),order,"123456","REQUESTED",1,Instant.EPOCH,"Sự kiện","Không thể tham dự",null);
    final List<String> calls=new ArrayList<>();boolean active=true;RuntimeException failure;
    final RefundRequestStore store=new RefundRequestStore(){
        public boolean active(UserPrincipal a){calls.add("auth");return active;}
        public List<RefundTicketOptionDto> tickets(UserPrincipal a,UUID o){if(failure!=null)throw failure;return List.of(new RefundTicketOptionDto(ticket,"code","A1","123456","ACTIVE",true,null));}
        public List<OrderOption> orders(UserPrincipal a){return List.of();}
        public RefundSummaryDto create(UserPrincipal a,CreateRefundRequestDto r,UUID k){calls.add("commit");if(failure!=null)throw failure;return result;}
        public RefundPageDto list(UserPrincipal a,int p,int size){return new RefundPageDto(List.of(result),1,p,size);}
        public RefundDetailDto detail(UserPrincipal a,UUID id){return new RefundDetailDto(result,null,null,List.of(),List.of());}
    };
    final RefundRequestCache cache=new RefundRequestCache(){
        public Lease lock(List<UUID> ids){calls.add("lock");return ()->calls.add("release");}
        public void committed(UUID a,UUID k,List<UUID> ids,RefundSummaryDto r){assertEquals(result,r);calls.add("invalidate");}
    };
    RefundRequestService service(RefundRequestCache c){return new RefundRequestServiceImpl(store,c);}
    CreateRefundRequestDto request(){return new CreateRefundRequestDto(order,List.of(ticket),"Không thể tham dự sự kiện.");}
    @Test void invalidationIsStrictlyAfterStoreCommit(){assertEquals(result,service(cache).create(actor,request(),UUID.randomUUID()));assertEquals(List.of("auth","lock","commit","invalidate","release"),calls);}
    @Test void failedMutationDoesNotInvalidateOrCache(){failure=new RefundException(400,"REFUND_NOT_ELIGIBLE","Vé đã được sử dụng.");assertEquals(400,assertThrows(RefundException.class,()->service(cache).create(actor,request(),UUID.randomUUID())).status());assertEquals(List.of("auth","lock","commit","release"),calls);}
    @Test void foreignOrderErrorFromDatabaseRemainsForbidden(){failure=new RefundException(403,"ORDER_FORBIDDEN","Không có quyền.");assertEquals(403,assertThrows(RefundException.class,()->service(cache).create(actor,request(),UUID.randomUUID())).status());}
    @Test void sessionIsCheckedBeforeAcquiringLocks(){active=false;assertEquals(401,assertThrows(RefundException.class,()->service(cache).create(actor,request(),UUID.randomUUID())).status());assertEquals(List.of("auth"),calls);}
    @Test void missingSessionIsRejected(){assertEquals(401,assertThrows(RefundException.class,()->service(cache).list(null,1,20)).status());assertTrue(calls.isEmpty());}
    @Test void invalidPagesAreRejected(){for(int[] p:new int[][]{{0,20},{1,0},{1,101},{100001,20}})assertEquals(400,assertThrows(RefundException.class,()->service(cache).list(actor,p[0],p[1])).status());}
    @Test void unavailableCacheCannotMaskSuccessfulCommit(){var broken=new RefundRequestCache(){public Lease lock(List<UUID> ids){throw new IllegalStateException();}public void committed(UUID a,UUID k,List<UUID> ids,RefundSummaryDto r){throw new IllegalStateException();}};assertEquals(result,service(broken).create(actor,request(),UUID.randomUUID()));}
    @Test void createValidationRejectsEmptyDuplicateForeignFormatsAndBadReason(){assertThrows(RefundException.class,()->new CreateRefundRequestDto(order,List.of(),"Valid reason"));assertThrows(RefundException.class,()->new CreateRefundRequestDto(order,List.of(ticket,ticket),"Valid reason"));assertThrows(RefundException.class,()->new CreateRefundRequestDto(order,List.of(ticket),"short"));assertThrows(RefundException.class,()->new CreateRefundRequestDto(order,List.of(ticket),"x".repeat(501)));assertThrows(RefundException.class,()->new CreateRefundRequestDto(order,Collections.nCopies(9,ticket),"Valid reason"));}
    @Test void dtoCopiesSelectionAndSortsItForStableLockKeys(){var ids=new ArrayList<>(List.of(UUID.randomUUID(),ticket));var dto=new CreateRefundRequestDto(order,ids,"  Valid reason  ");ids.clear();assertEquals(2,dto.ticketIds().size());assertEquals("Valid reason",dto.reason());assertEquals(dto.ticketIds().stream().sorted(Comparator.comparing(UUID::toString)).toList(),dto.ticketIds());assertThrows(UnsupportedOperationException.class,()->dto.ticketIds().clear());}
}
