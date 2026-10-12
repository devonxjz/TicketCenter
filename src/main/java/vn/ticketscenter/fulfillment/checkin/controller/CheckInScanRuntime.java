package vn.ticketscenter.fulfillment.checkin.controller;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.ServerConfig;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.repository.CheckInScanRepository;
import vn.ticketscenter.fulfillment.checkin.service.CheckInScanService;
import vn.ticketscenter.fulfillment.ticket.service.SignedTicketQrSigner;

final class CheckInScanRuntime {
    static final String SERVICE_ATTRIBUTE = CheckInScanService.class.getName();

    private CheckInScanRuntime() { }

    static CheckInScanService resolve(ServletContext context) {
        synchronized (context) {
            if (context.getAttribute(SERVICE_ATTRIBUTE) instanceof CheckInScanService service) return service;
            Object value = context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if (!(value instanceof PersistenceRegistry registry) || !registry.hasPrincipal(DatabasePrincipal.CHECK_IN))
                throw new CheckInException(503, "CHECK_IN_UNAVAILABLE", "Hệ thống soát vé chưa sẵn sàng.");

            // Use the same key resolution as TicketRuntime, without opening a buyer connection.
            String secret = ServerConfig.getSecretKey();
            if (secret == null || secret.isBlank())
                secret = AppConfig.get("ticket.qr.secret", "");
            if (secret == null || secret.isBlank()) {
                if (ServerConfig.isProduction())
                    throw new CheckInException(503, "CHECK_IN_UNAVAILABLE", "Khóa xác thực QR chưa được cấu hình.");
                secret = "dev-secret-key-ticketscenter-2026";
            }
            CheckInScanService service = new CheckInScanService(
                    new CheckInScanRepository(registry.transactionManager()), new SignedTicketQrSigner(secret));
            context.setAttribute(SERVICE_ATTRIBUTE, service);
            return service;
        }
    }
}
