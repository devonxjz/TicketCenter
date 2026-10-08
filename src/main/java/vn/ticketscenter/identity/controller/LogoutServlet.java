package vn.ticketscenter.identity.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.security.SecurityContext;

import java.io.IOException;

@WebServlet(name = "LogoutServlet", urlPatterns = "/api/auth/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Hủy phiên an toàn và có tính lũy thỏa (idempotent, gọi nhiều lần không lỗi)
        SecurityContext.clearSession(req);
        HttpResponses.data(resp, "{\"loggedOut\":true}");
    }
}
