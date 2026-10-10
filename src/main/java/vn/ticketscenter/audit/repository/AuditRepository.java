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
        var params=new ArrayList<Object>();params.add(f.from());params.add(f.to());String where=" FROM dbo.tc_audit_logs a WHERE created_at>=? AND created_at<?";
        if(f.action()!=null){where+=" AND action=?";params.add(f.action());}if(f.entityType()!=null){where+=" AND entity_type=?";params.add(f.entityType());}if(f.entityId()!=null){where+=" AND entity_id=?";params.add(f.entityId());}
        String json="CASE WHEN ISJSON(metadata_json)=1 THEN metadata_json ELSE N'{}' END";
        if(f.eventId()!=null){where+=" AND ((entity_type='Event' AND TRY_CONVERT(uniqueidentifier,entity_id)=?) OR TRY_CONVERT(uniqueidentifier,JSON_VALUE("+json+",'$.eventId'))=?)";params.add(f.eventId());params.add(f.eventId());}
        if(f.organizationId()!=null){where+=" AND ((entity_type='Organization' AND TRY_CONVERT(uniqueidentifier,entity_id)=?) OR TRY_CONVERT(uniqueidentifier,JSON_VALUE("+json+",'$.organizationId'))=? OR EXISTS (SELECT 1 FROM dbo.tc_events e WHERE e.organization_id=? AND entity_type='Event' AND TRY_CONVERT(uniqueidentifier,entity_id)=e.id))";params.add(f.organizationId());params.add(f.organizationId());params.add(f.organizationId());}
        long total;try(var query=db.prepareStatement("SELECT COUNT_BIG(*)"+where)){ReadProjection.bind(query,params);try(var row=query.executeQuery()){row.next();total=row.getLong(1);}}
        var rows=new ArrayList<Map<String,Object>>();try(var query=db.prepareStatement("SELECT "+COLUMNS+where+" ORDER BY created_at DESC,id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")){var all=new ArrayList<>(params);all.add(f.offset());all.add(f.pageSize());ReadProjection.bind(query,all);try(var row=query.executeQuery()){while(row.next())rows.add(project(row));}}
        return Map.of("rows",rows,"page",f.page(),"pageSize",f.pageSize(),"total",total,"from",f.from(),"to",f.to(),"timezone","UTC");
    }
    public Map<String,Object> detail(Connection db,UUID id)throws SQLException{try(var query=db.prepareStatement("SELECT "+COLUMNS+" FROM dbo.tc_audit_logs WHERE id=?")){query.setString(1,id.toString());try(var row=query.executeQuery()){if(!row.next())throw new FinanceException("NOT_FOUND",404);return project(row);}}}
    private Map<String,Object> project(ResultSet row)throws SQLException{var result=ReadProjection.row(row,List.of("id","actor_id","action","entity_type","entity_id","created_at"));result.put("metadata",AuditMetadata.project(row.getString("metadata_json")));return result;}
}
