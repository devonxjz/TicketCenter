package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.ServletContext;
import java.time.Clock;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.fulfillment.refund.dao.RefundRequestRepository;
import vn.ticketscenter.fulfillment.refund.service.*;

public final class RefundRuntime {
    public static final String SERVICE_ATTRIBUTE=RefundRequestService.class.getName();
    private RefundRuntime(){}
    public static RefundRequestService resolve(ServletContext context){synchronized(context){
        Object cached=context.getAttribute(SERVICE_ATTRIBUTE);if(cached instanceof RefundRequestService service)return service;
        Object value=context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if(!(value instanceof PersistenceRegistry registry)||!registry.hasPrincipal(DatabasePrincipal.BUYER))throw new RefundException(503,"DATABASE_UNAVAILABLE","Hệ thống hoàn vé chưa sẵn sàng.");
        var service=new RefundRequestService(new RefundRequestRepository(registry.transactionManager()),new RedisRefundRequestCache(),Clock.systemUTC());context.setAttribute(SERVICE_ATTRIBUTE,service);return service;
    }}
}
