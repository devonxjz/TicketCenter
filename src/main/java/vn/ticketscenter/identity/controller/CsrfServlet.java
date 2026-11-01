package vn.ticketscenter.identity.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.security.SecurityContext;

import java.io.IOException;

@WebServlet(name = "CsrfServlet", urlPatterns = "/api/auth/csrf")
public class CsrfServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(true);
        String token = SecurityContext.getOrCreateCsrfToken(session);
        HttpResponses.data(response, "{\"token\":\"" + token + "\"}");
    }
}
