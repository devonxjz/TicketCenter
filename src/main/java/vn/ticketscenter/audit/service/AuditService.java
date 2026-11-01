package vn.ticketscenter.audit.service;

import java.sql.*;
import java.util.*;
import vn.ticketscenter.audit.dto.AuditFilter;
import vn.ticketscenter.identity.security.UserPrincipal;

public interface AuditService {
    String list(UserPrincipal actor, AuditFilter filter) throws SQLException;

    String detail(UserPrincipal actor, UUID id) throws SQLException;
}
