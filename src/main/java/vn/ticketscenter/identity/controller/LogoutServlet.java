package vn.ticketscenter.identity.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.security.SecurityContext;

import java.io.IOException;

@WebServlet(name = "LogoutServlet", urlPatterns = {"/api/auth/logout", "/auth/logout"})
public class LogoutServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        SecurityContext.clearSession(req);
        resp.sendRedirect(req.getContextPath() + "/");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Hủy phiên an toàn và có tính lũy thỏa (idempotent, gọi nhiều lần không lỗi)
        SecurityContext.clearSession(req);
        String accept = req.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) {
            resp.sendRedirect(req.getContextPath() + "/");
            return;
        }
        HttpResponses.data(resp, "{\"loggedOut\":true}");
    }
}
