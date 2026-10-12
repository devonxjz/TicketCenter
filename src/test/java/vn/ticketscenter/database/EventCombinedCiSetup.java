package vn.ticketscenter.database;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;

/** Clone only the freshly installed disposable Linux CI fixture; never a configurable shared target. */
public final class EventCombinedCiSetup {
    private static final String DATABASE="TicketCenter_Dong_Vuong_Ci";
    private static final String BACKUP="/var/opt/mssql/data/vuong-event-ci.bak";
    public static void main(String[] args) throws Exception {
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"true".equals(System.getenv("TC_SQL_REPORT_TEST")))
            throw new IllegalStateException("Disposable GitHub CI fixture required");
        String password=ScanFixture.required("TC_DONG_BUYER_PASSWORD");
        if(!password.matches("K02![a-fA-F0-9]{48}"))throw new IllegalArgumentException("Disposable buyer credential required");
        Map<String,UUID> ids=new LinkedHashMap<>();for(String key:List.of("org","category","published","hidden","zone","seat"))ids.put(key,UUID.randomUUID());
        try(var connection=DriverManager.getConnection("jdbc:sqlserver://127.0.0.1:1433;databaseName=master;encrypt=true;trustServerCertificate=true;loginTimeout=5","sa",ScanFixture.required("TC_SCAN_SA_PASSWORD"));var query=connection.createStatement()) {
            query.setQueryTimeout(90);
            try(var rows=query.executeQuery("SELECT host_platform FROM sys.dm_os_host_info")) {
                if(!rows.next()||!"Linux".equals(rows.getString(1)))throw new IllegalStateException("Linux CI service required");
            }
            try(var rows=query.executeQuery("SELECT DB_ID('"+DATABASE+"')")) {
                rows.next();if(rows.getObject(1)!=null)throw new IllegalStateException("Refusing an existing Event CI database");
            }
            query.execute("BACKUP DATABASE TicketCenter_Khanh02_Test TO DISK='"+BACKUP+"' WITH COPY_ONLY,INIT");
            var moves=new ArrayList<String>();
            try(var files=query.executeQuery("RESTORE FILELISTONLY FROM DISK='"+BACKUP+"'")) {
                while(files.next()) {
                    String type=files.getString("Type");long fileId=files.getLong("FileId");
                    if(!Set.of("D","L").contains(type)||fileId<1)throw new IllegalStateException("Unexpected CI backup files");
                    moves.add("MOVE '"+files.getString("LogicalName").replace("'","''")+"' TO '/var/opt/mssql/data/vuong-event-ci-"+fileId+(type.equals("L")?".ldf":".mdf")+"'");
                }
            }
            if(moves.isEmpty())throw new IllegalStateException("CI backup has no database files");
            query.execute("RESTORE DATABASE "+DATABASE+" FROM DISK='"+BACKUP+"' WITH "+String.join(",",moves)+",RECOVERY");
            query.execute("CREATE LOGIN dong_runtime_buyer WITH PASSWORD='"+password+"',CHECK_POLICY=ON");
            query.execute("USE "+DATABASE);
            for(String role:List.of("buyer","manager","admin"))
                query.execute("CREATE USER dong_"+role+" WITHOUT LOGIN; ALTER ROLE "+(role.equals("admin")?"tc_platform_admin":"tc_"+role)+" ADD MEMBER dong_"+role);
            query.execute("CREATE USER dong_runtime_buyer FOR LOGIN dong_runtime_buyer; ALTER ROLE tc_buyer ADD MEMBER dong_runtime_buyer");
            try(var seed=connection.prepareStatement("""
                SET XACT_ABORT ON;
                BEGIN TRANSACTION;
                INSERT dbo.tc_organizations(id,name,contact_email,status) VALUES(?,N'CI catalogue',N'fixture@example.invalid','APPROVED');
                INSERT dbo.tc_event_categories(id,name,slug) VALUES(?,N'CI catalogue',?);
                INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time)
                VALUES(?,?,?,N'Published CI Event',N'Isolated acceptance',N'Fixture venue',N'Fixture address',DATEADD(day,-1,SYSUTCDATETIME()),DATEADD(hour,2,SYSUTCDATETIME()),DATEADD(day,1,SYSUTCDATETIME()),DATEADD(day,2,SYSUTCDATETIME())),
                (?,?,?,N'Hidden CI Event',N'Isolated acceptance',N'Fixture venue',N'Fixture address',DATEADD(day,1,SYSUTCDATETIME()),DATEADD(day,2,SYSUTCDATETIME()),DATEADD(day,3,SYSUTCDATETIME()),DATEADD(day,4,SYSUTCDATETIME()));
                INSERT dbo.tc_zones(id,event_id,name,type,price,capacity) VALUES(?,?,N'Fixture seated zone','SEATED',1000,NULL);
                INSERT dbo.tc_seats(id,zone_id,row_name,seat_number,label) VALUES(?,?,N'A',1,N'A1');
                UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?;
                COMMIT;
                """)) {
                seed.setQueryTimeout(30);
                List<UUID> values=List.of(ids.get("org"),ids.get("category"),ids.get("category"),ids.get("published"),ids.get("org"),ids.get("category"),ids.get("hidden"),ids.get("org"),ids.get("category"),ids.get("zone"),ids.get("published"),ids.get("seat"),ids.get("zone"),ids.get("published"));
                for(int index=0;index<values.size();index++)seed.setString(index+1,values.get(index).toString());seed.execute();
            }
        }
        String environment="TC_EVENT_TEST_PUBLISHED_ID="+ids.get("published")+"\nTC_EVENT_TEST_HIDDEN_ID="+ids.get("hidden")+"\nTC_EVENT_TEST_ZONE_ID="+ids.get("zone")+"\n";
        Files.writeString(Path.of(ScanFixture.required("GITHUB_ENV")),environment,StandardOpenOption.APPEND);
        System.out.println("Created isolated combined Event CI database and restricted catalogue buyer.");
    }
}
