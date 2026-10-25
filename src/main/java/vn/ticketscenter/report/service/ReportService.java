package vn.ticketscenter.report.service;

import java.io.*;
import java.sql.*;
import java.util.*;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.report.csv.StreamingCsvExporter;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.report.dao.ReadProjection;
import vn.ticketscenter.report.dao.ReportRepository;
import vn.ticketscenter.settlement.infrastructure.*;

public final class ReportService {
    private final FinanceDatabase database;
    private final ReportCache cache;
    private final ReportRepository repository = new ReportRepository();

    public ReportService(FinanceDatabase database, ReportCache cache) {
        this.database = Objects.requireNonNull(database);
        this.cache = Objects.requireNonNull(cache);
    }

    private void authorize(Connection db, UserPrincipal actor, ReportFilter f) throws SQLException {
        if (f.organizationId() == null) FinanceAuthorization.requireAdmin(db, actor);
        else FinanceAuthorization.requireManager(db, actor, f.organizationId());
        repository.requireEventScope(db, f);
    }

    public String json(UserPrincipal actor, ReportFilter filter) throws SQLException {
        return json(actor, filter, false);
    }

    public String jsonAdmin(UserPrincipal actor, ReportFilter filter) throws SQLException {
        return json(actor, filter, true);
    }

    private String json(UserPrincipal actor, ReportFilter filter, boolean adminOnly)
            throws SQLException {
        try (var db = database.open()) {
            if (adminOnly) FinanceAuthorization.requireAdmin(db, actor);
            authorize(db, actor, filter);
            String key = cache.key(actor, filter), hit = cache.get(key);
            if (hit != null) return hit;
            String json = FinanceJson.write(Map.of("data", repository.page(db, filter)));
            cache.put(key, json);
            return json;
        }
    }

    public void export(UserPrincipal actor, ReportFilter filter, OutputStream output)
            throws SQLException, IOException {
        try (var db = database.open()) {
            authorize(db, actor, filter);
            try (var cursor = repository.cursor(db, filter)) {
                StreamingCsvExporter.write(output, cursor.headers(), cursor);
            }
        }
    }

    public String options(UserPrincipal actor, UUID selectedOrg) throws SQLException {
        try (var db = database.open()) {
            boolean admin = true;
            try {
                FinanceAuthorization.requireAdmin(db, actor);
            } catch (FinanceException e) {
                if (e.status() != 403) throw e;
                admin = false;
            }
            if (selectedOrg != null) FinanceAuthorization.requireManager(db, actor, selectedOrg);
            String sql =
                    "SELECT o.id,o.name FROM dbo.vw_FinanceOrganizations o"
                            + (admin
                                    ? ""
                                    : " WHERE EXISTS (SELECT 1 FROM dbo.vw_ActiveManagerMemberships m WHERE m.organization_id=o.id AND m.user_id=?)")
                            + " ORDER BY o.name,o.id";
            List<Object> member = admin ? List.of() : List.of(actor.actorId());
            var organizations =
                    ReadProjection.list(db, sql, member, r -> ReadProjection.row(r, List.of("id", "name")));
            if (!admin && organizations.isEmpty()) throw new FinanceException("FORBIDDEN", 403);
            List<Map<String, Object>> events =
                    selectedOrg == null
                            ? new ArrayList<>()
                            : ReadProjection.list(
                                    db,
                                    "SELECT id,title FROM dbo.vw_FinanceEvents WHERE organization_id=? ORDER BY title,id",
                                    List.of(selectedOrg),
                                    r -> ReadProjection.row(r, List.of("id", "title")));
            return FinanceJson.write(
                    Map.of("data", Map.of("isAdmin", admin, "organizations", organizations, "events", events)));
        }
    }
}
