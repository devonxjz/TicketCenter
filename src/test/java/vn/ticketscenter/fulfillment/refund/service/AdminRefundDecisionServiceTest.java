package vn.ticketscenter.fulfillment.refund.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.dao.AdminRefundStore;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import static org.junit.jupiter.api.Assertions.*;

class AdminRefundDecisionServiceTest {
    private final UUID id=UUID.randomUUID(),key=UUID.randomUUID();
    private final UserPrincipal admin=new UserPrincipal(UUID.randomUUID(),PlatformRole.ADMIN,3);
    static final class Store implements AdminRefundStore {
        int reads,writes,checks;String status;RuntimeException error,authorityError;
        AdminRefundMutationDto result=new AdminRefundMutationDto(UUID.randomUUID(),"APPROVED","PROCESSING",UUID.randomUUID(),UUID.randomUUID(),List.of());
        public void requireAdmin(UserPrincipal a){checks++;if(authorityError!=null)throw authorityError;}
        public AdminRefundPageDto list(String s,int p,int n){reads++;status=s;return new AdminRefundPageDto(List.of(),0,p,n);}
        public AdminRefundDetailDto detail(UUID id){reads++;return null;}
        public AdminRefundMutationDto decide(UserPrincipal a,UUID id,AdminRefundDecisionRequestDto r,UUID k){writes++;if(error!=null)throw error;return result;}
        public AdminRefundMutationDto retry(UserPrincipal a,UUID id,UUID k){writes++;if(error!=null)throw error;return result;}
    }
    @Test void dec04NonAdminCannotListDetailOrMutate(){
        var store=new Store();var service=new AdminRefundDecisionServiceImpl(store,(a,k,r)->fail());
        var buyer=new UserPrincipal(UUID.randomUUID(),PlatformRole.CUSTOMER,0);
        assertEquals(403,assertThrows(RefundException.class,()->service.list(buyer,null,1,20)).status());
        assertEquals(403,assertThrows(RefundException.class,()->service.detail(buyer,id)).status());
        assertEquals(403,assertThrows(RefundException.class,()->service.decide(buyer,id,new AdminRefundDecisionRequestDto("APPROVE",null,null),key)).status());
        assertEquals(403,assertThrows(RefundException.class,()->service.retry(buyer,id,new AdminRefundRetryRequestDto(key))).status());
        assertEquals(0,store.checks+store.reads+store.writes);
    }
    @Test void missingActorAndRevokedAdminAreNotAccepted(){var store=new Store();var service=new AdminRefundDecisionServiceImpl(store,(a,k,r)->fail());
        assertEquals(401,assertThrows(RefundException.class,()->service.detail(null,id)).status());
        store.authorityError=new RefundException(403,"ADMIN_REQUIRED","Revoked");
        assertEquals(403,assertThrows(RefundException.class,()->service.detail(admin,id)).status());assertEquals(0,store.reads);
    }
    @Test void canonicalFiltersAndPagingAreValidated(){var store=new Store();var service=new AdminRefundDecisionServiceImpl(store,(a,k,r)->{});
        service.list(admin,"FAILED",1,20);assertEquals("RETRYABLE",store.status);
        service.list(admin,"UNKNOWN",1,20);assertEquals("NEEDS_RECONCILIATION",store.status);
        service.list(admin,"",1,20);assertNull(store.status);
        for(String state:List.of("bogus","PENDING","REQUESTED' OR 1=1"))assertThrows(RefundException.class,()->service.list(admin,state,1,20));
        assertThrows(RefundException.class,()->service.list(admin,null,0,20));assertThrows(RefundException.class,()->service.list(admin,null,1,101));
    }
    @Test void databaseAlwaysDecidesEvenIfRedisIsUnavailable(){var store=new Store();var service=new AdminRefundDecisionServiceImpl(store,(a,k,r)->{assertEquals(1,store.writes);throw new IllegalStateException("Redis offline");});
        assertSame(store.result,service.decide(admin,id,new AdminRefundDecisionRequestDto("APPROVE",null,null),key));
    }
    @Test void databaseConflictNeverWritesSuccessfulCache(){var store=new Store();store.error=new RefundException(409,"CONFLICT","Conflict");
        var service=new AdminRefundDecisionServiceImpl(store,(a,k,r)->fail("Cannot cache failed transaction"));
        assertEquals(409,assertThrows(RefundException.class,()->service.retry(admin,id,new AdminRefundRetryRequestDto(key))).status());
    }
}
