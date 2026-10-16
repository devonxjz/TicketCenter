package vn.ticketscenter.event.cancellation.controller;

import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import vn.ticketscenter.config.web.*;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.event.cancellation.dto.*;
import vn.ticketscenter.event.cancellation.service.*;
import vn.ticketscenter.event.dao.EventCancellationRepository;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.*;

final class EventCancellationWeb {
    private EventCancellationWeb() { }
    static UUID id(String value) {
        if(value==null||!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw invalid();
        return UUID.fromString(value);
    }
    static RefundException invalid() { return new RefundException(400,"CANCELLATION_INVALID","Thông tin hủy sự kiện không hợp lệ."); }
    static UUID target(HttpServletRequest r,String action) {
        String path=r.getRequestURI().substring(r.getContextPath().length());String[] pieces=path.split("/",-1);
        if(pieces.length!=6||!pieces[1].equals("api")||!pieces[2].equals("admin")||!pieces[3].equals("events")||!pieces[5].equals(action))
            throw new RefundException(404,"ROUTE_NOT_FOUND","Đường dẫn không tồn tại.");
        return id(pieces[4]);
    }
    static UserPrincipal actor(HttpServletRequest r) {
        var actor=SecurityContext.getCurrentUser(r).orElseThrow(()->new RefundException(401,"UNAUTHENTICATED","Bạn cần đăng nhập."));
        if(!actor.isAdmin())throw new RefundException(403,"ADMIN_REQUIRED","Chỉ quản trị viên được hủy sự kiện.");
        return actor; // The service/repository verifies current status, authVersion and role with SQL.
    }
    static UUID mutation(HttpServletRequest r) {
        if(!SecurityContext.validateCsrfToken(r))throw new RefundException(403,"CSRF_INVALID","Mã bảo vệ phiên không hợp lệ.");
        if(r.getContentType()==null||!r.getContentType().split(";",2)[0].strip().equalsIgnoreCase("application/json"))
            throw new RefundException(415,"JSON_REQUIRED","Yêu cầu phải sử dụng JSON.");
        return id(r.getHeader("Idempotency-Key"));
    }
    static EventCancelRequestDto body(HttpServletRequest r)throws IOException {
        var source=new StringBuilder();char[] buffer=new char[1024];int n;
        try(var reader=r.getReader()) {while((n=reader.read(buffer))!=-1) {
            source.append(buffer,0,n);if(source.length()>8192)throw new RefundException(413,"PAYLOAD_TOO_LARGE","Nội dung yêu cầu quá lớn.");
        }}
        return parse(source.toString());
    }
    static EventCancelRequestDto parse(String body) {
        try {
            // The shared parser rejects duplicate fields and invalid/trailing JSON. Require a quoted reason;
            // its numeric/null convenience conversions must never become accepted cancellation text.
            var fields=JsonObjectParser.parse(new StringReader(body));
            if(fields.size()!=1||!fields.containsKey("reason")||!body.stripLeading().matches("(?s)\\{\\s*\"reason\"\\s*:\\s*\".*"))throw invalid();
            return new EventCancelRequestDto(fields.get("reason"));
        }catch(IOException|IllegalArgumentException e){throw invalid();}
    }
    static EventCancellationService service(HttpServletRequest r) {
        var context=r.getServletContext();synchronized(context) {
            var existing=context.getAttribute(EventCancellationService.class.getName());
            if(existing instanceof EventCancellationService s)return s;
            var configured=context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if(!(configured instanceof PersistenceRegistry registry)||!registry.hasPrincipal(DatabasePrincipal.ADMIN))
                throw new RefundException(503,"DATABASE_UNAVAILABLE","Hệ thống hủy sự kiện chưa sẵn sàng.");
            var service=new EventCancellationService(new EventCancellationRepository(registry.transactionManager()),new CancellationProgressCacheHelper());
            context.setAttribute(EventCancellationService.class.getName(),service);return service;
        }
    }
    static void error(HttpServletResponse r,RuntimeException failure)throws IOException {
        if(failure instanceof RefundException e)HttpResponses.error(r,e.status(),e.code(),e.getMessage());
        else HttpResponses.error(r,503,"CANCELLATION_UNAVAILABLE","Hệ thống hủy sự kiện tạm thời không khả dụng.");
    }
}
