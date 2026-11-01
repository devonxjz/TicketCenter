package vn.ticketscenter.report.service;

import java.io.*;
import java.sql.*;
import java.util.*;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.report.dto.ReportFilter;

public interface ReportService {
    String json(UserPrincipal actor, ReportFilter filter) throws SQLException;

    String jsonAdmin(UserPrincipal actor, ReportFilter filter) throws SQLException;

    void export(UserPrincipal actor, ReportFilter filter, OutputStream output)
            throws SQLException, IOException;

    String options(UserPrincipal actor, UUID selectedOrg) throws SQLException;
}
