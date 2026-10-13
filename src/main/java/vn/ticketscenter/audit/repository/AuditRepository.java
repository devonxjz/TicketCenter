package vn.ticketscenter.audit.repository;
import java.sql.*;
import java.util.*;
import vn.ticketscenter.audit.dto.AuditFilter;
import vn.ticketscenter.audit.service.AuditMetadata;
import vn.ticketscenter.report.repository.ReadProjection;
import vn.ticketscenter.settlement.infrastructure.FinanceException;

public final class AuditRepository {
    private static final String COLUMNS="id,actor_id,action,entity_type,entity_id,metadata_json,created_at";
    public Map<String,Object> list(Connection db,AuditFilter f)throws SQLException{
        var params=new ArrayList<Object>();params.add(f.from());params.add(f.to());String where=" FROM dbo.vw_AuditLogs a WHERE created_at>=? AND created_at<?";
        if(f.action()!=null){where+=" AND action=?";params.add(f.action());}if(f.entityType()!=null){where+=" AND entity_type=?";params.add(f.entityType());}if(f.entityId()!=null){where+=" AND entity_id=?";params.add(f.entityId());}
        if(f.actorId()!=null){where+=" AND actor_id=?";params.add(f.actorId());}
        if(f.eventId()!=null){where+=" AND EXISTS (SELECT 1 FROM dbo.fn_AuditEventMatch(?,a.entity_type,a.entity_id,a.metadata_json))";params.add(f.eventId());}
        if(f.organizationId()!=null){where+=" AND EXISTS (SELECT 1 FROM dbo.fn_AuditOrganizationMatch(?,a.entity_type,a.entity_id,a.metadata_json))";params.add(f.organizationId());}
        long total;try(var query=db.prepareStatement("SELECT COUNT_BIG(*)"+where)){ReadProjection.bind(query,params);try(var row=query.executeQuery()){row.next();total=row.getLong(1);}}
        var rows=new ArrayList<Map<String,Object>>();try(var query=db.prepareStatement("SELECT "+COLUMNS+where+" ORDER BY created_at DESC,id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")){var all=new ArrayList<>(params);all.add(f.offset());all.add(f.pageSize());ReadProjection.bind(query,all);try(var row=query.executeQuery()){while(row.next())rows.add(project(row));}}
        return Map.of("rows",rows,"page",f.page(),"pageSize",f.pageSize(),"total",total,"from",f.from(),"to",f.to(),"timezone","UTC");
    }
    public Map<String,Object> detail(Connection db,UUID id)throws SQLException{try(var query=db.prepareStatement("SELECT "+COLUMNS+" FROM dbo.vw_AuditLogs WHERE id=?")){query.setString(1,id.toString());try(var row=query.executeQuery()){if(!row.next())throw new FinanceException("NOT_FOUND",404);return project(row);}}}
    private Map<String,Object> project(ResultSet row)throws SQLException{var result=ReadProjection.row(row,List.of("id","actor_id","action","entity_type","entity_id","created_at"));result.put("metadata",AuditMetadata.project(row.getString("metadata_json")));return result;}
}
