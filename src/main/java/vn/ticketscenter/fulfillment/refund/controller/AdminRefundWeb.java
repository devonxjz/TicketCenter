package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

final class AdminRefundWeb {
    private AdminRefundWeb() { }
    static UserPrincipal actor(HttpServletRequest request) {
        var actor=RefundWeb.actor(request);
        if(!actor.isAdmin())throw new RefundException(403,"ADMIN_REQUIRED","Chỉ quản trị viên được phép xử lý hoàn tiền.");
        return actor;
    }
    static String body(HttpServletRequest request)throws IOException {
        var result=new StringBuilder();var buffer=new char[1024];int count;
        try(var reader=request.getReader()){while((count=reader.read(buffer))!=-1){result.append(buffer,0,count);if(result.length()>8192)throw new RefundException(413,"PAYLOAD_TOO_LARGE","Nội dung yêu cầu quá lớn.");}}
        return result.toString();
    }
    static java.util.UUID target(HttpServletRequest request,String action) {
        String path=RefundWeb.path(request),prefix="/api/admin/refund-requests/",suffix="/"+action;
        if(!path.startsWith(prefix)||!path.endsWith(suffix))throw new RefundException(404,"ROUTE_NOT_FOUND","Đường dẫn không tồn tại.");
        return RefundWeb.id(path.substring(prefix.length(),path.length()-suffix.length()));
    }
}
