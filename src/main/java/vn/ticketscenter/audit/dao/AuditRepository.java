package vn.ticketscenter.audit.dao;

import java.sql.*;
import java.util.*;
import vn.ticketscenter.audit.dto.AuditFilter;
import vn.ticketscenter.audit.service.AuditMetadata;
import vn.ticketscenter.report.dao.ReadProjection;
import vn.ticketscenter.settlement.infrastructure.FinanceException;

public final class AuditRepository {
    private static final String COLUMNS =
            "a.id,a.actor_id,a.action,a.entity_type,a.entity_id,a.metadata_json,a.created_at,a.actor_name,a.entity_name";
    private static final String SOURCE = " FROM dbo.vw_AuditDisplayNames a";

    public Map<String, Object> list(Connection db, AuditFilter f) throws SQLException {
        var params = new ArrayList<Object>();
        params.add(f.from());
        params.add(f.to());
        String where = SOURCE + " WHERE a.created_at>=? AND a.created_at<?";
        if (f.action() != null) {
            where += " AND action=?";
            params.add(f.action());
        }
        if (f.entityType() != null) {
            where += " AND entity_type=?";
            params.add(f.entityType());
        }
        if (f.entityId() != null) {
            where += " AND entity_id=?";
            params.add(f.entityId());
        }
        if (f.actorId() != null) {
            where += " AND actor_id=?";
            params.add(f.actorId());
        }
        if (f.eventId() != null) {
            where +=
                    " AND EXISTS (SELECT 1 FROM dbo.fn_AuditEventMatch(?,a.entity_type,a.entity_id,a.metadata_json))";
            params.add(f.eventId());
        }
        if (f.organizationId() != null) {
            where +=
                    " AND EXISTS (SELECT 1 FROM dbo.fn_AuditOrganizationMatch(?,a.entity_type,a.entity_id,a.metadata_json))";
            params.add(f.organizationId());
        }
        long total = ReadProjection.first(db, "SELECT COUNT_BIG(*)" + where, params, row -> row.getLong(1));
        var all = new ArrayList<>(params);
        all.add(f.offset());
        all.add(f.pageSize());
        var rows =
                ReadProjection.list(
                        db,
                        "SELECT " + COLUMNS + where + " ORDER BY a.created_at DESC,a.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                        all,
                        this::project);
        return Map.of(
                "rows",
                rows,
                "page",
                f.page(),
                "pageSize",
                f.pageSize(),
                "total",
                total,
                "from",
                f.from(),
                "to",
                f.to(),
                "timezone",
                "UTC");
    }

    public Map<String, Object> detail(Connection db, UUID id) throws SQLException {
        try (var query = db.prepareStatement("SELECT " + COLUMNS + SOURCE + " WHERE a.id=?")) {
            query.setString(1, id.toString());
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new FinanceException("NOT_FOUND", 404);
                return project(row);
            }
        }
    }

    private Map<String, Object> project(ResultSet row) throws SQLException {
        var result =
                ReadProjection.row(
                        row,
                        List.of(
                                "id",
                                "actor_id",
                                "action",
                                "entity_type",
                                "entity_id",
                                "created_at",
                                "actor_name",
                                "entity_name"));
        result.put("metadata", AuditMetadata.project(row.getString("metadata_json")));
        return result;
    }
}
