package vn.ticketscenter.fulfillment.checkin.controller;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.cache.RedisCheckInEventCache;
import vn.ticketscenter.fulfillment.checkin.dao.CheckInEventRepository;
import vn.ticketscenter.fulfillment.checkin.service.CheckInEventService;
import java.time.Clock;
import vn.ticketscenter.fulfillment.checkin.service.TicketCheckInService;
import vn.ticketscenter.fulfillment.checkin.security.SignedQrDecoder;
import vn.ticketscenter.fulfillment.checkin.dao.TicketCheckInRepository;
import vn.ticketscenter.fulfillment.checkin.cache.RedisCheckInScanCache;

/** Feature-owned wiring; does not alter shared persistence/configuration files. */
public final class CheckInRuntime {
    public static final String SERVICE_ATTRIBUTE = CheckInEventService.class.getName();
    private CheckInRuntime() { }

    public static TicketCheckInService resolveScanner(ServletContext context) {
        synchronized(context){
            Object existing=context.getAttribute(TicketCheckInService.class.getName());
            if(existing instanceof TicketCheckInService service)return service;
            Object value=context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if(!(value instanceof PersistenceRegistry registry) || !registry.hasPrincipal(DatabasePrincipal.CHECK_IN))
                throw new CheckInException(503,"DATABASE_UNAVAILABLE","Hệ thống soát vé chưa sẵn sàng.");
            var service=new TicketCheckInService(new TicketCheckInRepository(registry.transactionManager()),SignedQrDecoder.configured(),new RedisCheckInScanCache());
            context.setAttribute(TicketCheckInService.class.getName(),service);return service;
        }
    }

    public static CheckInEventService resolve(ServletContext context) {
        synchronized (context) {
            Object existing = context.getAttribute(SERVICE_ATTRIBUTE);
            if (existing instanceof CheckInEventService service) return service;
            Object value = context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if (!(value instanceof PersistenceRegistry registry) || !registry.hasPrincipal(DatabasePrincipal.CHECK_IN)) {
                throw new CheckInException(503, "DATABASE_UNAVAILABLE", "Hệ thống soát vé chưa sẵn sàng.");
            }
            CheckInEventService service = new CheckInEventService(new CheckInEventRepository(registry.transactionManager()),
                    new RedisCheckInEventCache(), Clock.systemUTC());
            context.setAttribute(SERVICE_ATTRIBUTE, service);
            return service;
        }
    }
}
