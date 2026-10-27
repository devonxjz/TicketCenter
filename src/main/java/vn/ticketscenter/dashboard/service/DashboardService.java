package vn.ticketscenter.dashboard.service;

import java.sql.*;
import java.util.*;
import vn.ticketscenter.identity.security.UserPrincipal;

public interface DashboardService {
    String admin(UserPrincipal actor) throws SQLException;

    String organization(UserPrincipal actor, UUID org) throws SQLException;
}
