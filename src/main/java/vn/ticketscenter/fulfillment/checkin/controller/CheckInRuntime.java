package vn.ticketscenter.fulfillment.checkin.controller;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.cache.CheckInEventCacheImpl;
import vn.ticketscenter.fulfillment.checkin.cache.CheckInScanCacheImpl;
import vn.ticketscenter.fulfillment.checkin.dao.CheckInEventRepository;
import vn.ticketscenter.fulfillment.checkin.dao.TicketCheckInRepository;
import vn.ticketscenter.fulfillment.checkin.security.SignedQrDecoder;
import vn.ticketscenter.fulfillment.checkin.service.CheckInEventService;
import vn.ticketscenter.fulfillment.checkin.service.CheckInEventServiceImpl;
import vn.ticketscenter.fulfillment.checkin.service.TicketCheckInService;
import vn.ticketscenter.fulfillment.checkin.service.TicketCheckInServiceImpl;

import java.time.Clock;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.CHECK_IN;

public final class CheckInRuntime {
    public static final String SERVICE_ATTRIBUTE = CheckInEventService.class.getName();
    private static final String SCANNER_ATTRIBUTE = TicketCheckInService.class.getName();

    private CheckInRuntime() {}

    public static TicketCheckInService resolveScanner(ServletContext context) {
        return FeatureContext.cached(
                context,
                SCANNER_ATTRIBUTE,
                () ->
                        new TicketCheckInServiceImpl(
                                new TicketCheckInRepository(registry(context).transactionManager()),
                                SignedQrDecoder.configured(),
                                new CheckInScanCacheImpl()));
    }

    public static CheckInEventService resolve(ServletContext context) {
        return FeatureContext.cached(
                context,
                SERVICE_ATTRIBUTE,
                () ->
                        new CheckInEventServiceImpl(
                                new CheckInEventRepository(registry(context).transactionManager()),
                                new CheckInEventCacheImpl(),
                                Clock.systemUTC()));
    }

    private static PersistenceRegistry registry(ServletContext context) {
        return FeatureContext.registry(
                context,
                CHECK_IN,
                () ->
                        new CheckInException(
                                503, "DATABASE_UNAVAILABLE", "Hệ thống soát vé chưa sẵn sàng."));
    }
}
