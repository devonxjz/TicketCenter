package vn.ticketscenter.event.integration;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in migration smoke against an exact isolated Dong fixture target. */
class EventSqlMigrationIT {
    @Test void reviewedMigrationReappliesWithoutLosingReceiptsAndGrantsOnlyPublicSeatRead() throws Exception {
        try(Connection c=DongFixtureConnectionSettings.configured().open()) {
            long receipts=count(c);String cleanupBefore=cleanupFingerprint(c);
            String migration=Files.readString(Path.of("database/migrations/045_event_commands_and_public_seats.sql"))+"\n"+Files.readString(Path.of("database/migrations/047_event_authority_lock_order.sql"));
            assertTrue(migration.contains("CREATE OR ALTER PROCEDURE dbo.usp_EventCommand"),"Refuse an incomplete migration artifact" );
            for(int attempt=0;attempt<2;attempt++) {
                c.setAutoCommit(false);
                try(var q=c.createStatement()) {
                    q.setQueryTimeout(30);
                    for(String batch:migration.split("(?im)^\\s*GO\\s*\\r?$")) if(!batch.isBlank()) q.execute(batch);
                    c.commit();
                } catch(Exception e) {c.rollback();throw e;}
                finally {c.setAutoCommit(true);}
                assertEquals(receipts,count(c),"Durable command results must survive reapplication");
                assertEquals(cleanupBefore,cleanupFingerprint(c),"Durable private cleanup metadata must survive reapplication");
            }
            try(var q=c.createStatement()) {
                q.execute("EXECUTE AS USER='dong_buyer'");
                try {
                    try(var rows=q.executeQuery("SELECT HAS_PERMS_BY_NAME('dbo.vw_PublicEventSeats','OBJECT','SELECT'), HAS_PERMS_BY_NAME('dbo.tc_event_command_receipts','OBJECT','SELECT')")) {
                        assertTrue(rows.next());assertEquals(1,rows.getInt(1));assertEquals(0,rows.getInt(2),"Buyer must not read private command receipts");
                    }
                    try(var rows=q.executeQuery("SELECT COUNT_BIG(*) FROM dbo.vw_PublicEventSeats")) {
                        assertTrue(rows.next());assertTrue(rows.getLong(1)>=0);
                    }
                    assertEquals(229,assertThrows(SQLException.class,()->q.executeQuery("SELECT TOP(1) id FROM dbo.tc_events")).getErrorCode(),"Buyer must read the projection rather than private Event tables");
                } finally {q.execute("REVERT");}
                try(var rows=q.executeQuery("SELECT COUNT_BIG(*) FROM dbo.vw_PublicEventSeats s JOIN dbo.tc_zones z ON z.id=s.zone_id JOIN dbo.tc_events e ON e.id=z.event_id WHERE e.status<>'PUBLISHED'")) {
                    assertTrue(rows.next());assertEquals(0,rows.getLong(1),"Owner verifies that hidden inventory never appears in the public projection");
                }
            }
        }
    }
    private static String cleanupFingerprint(Connection c) throws Exception {
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        try(var q=c.createStatement();var rows=q.executeQuery("SELECT COL_LENGTH('dbo.tc_event_command_receipts','cleanup_json')")) {
            rows.next();if(rows.getObject(1)==null)return java.util.HexFormat.of().formatHex(digest.digest());
        }
        try(var q=c.createStatement();var rows=q.executeQuery("SELECT actor_id,scope,idempotency_key,cleanup_json FROM dbo.tc_event_command_receipts WHERE cleanup_json IS NOT NULL ORDER BY actor_id,scope,idempotency_key")) {
            while(rows.next())for(int column=1;column<=4;column++) {
                byte[] bytes=rows.getString(column).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    private static long count(Connection c) throws SQLException {
        try(var q=c.createStatement();var rows=q.executeQuery("IF OBJECT_ID('dbo.tc_event_command_receipts','U') IS NULL SELECT CAST(0 AS bigint) ELSE SELECT COUNT_BIG(*) FROM dbo.tc_event_command_receipts")) {rows.next();return rows.getLong(1);}
    }
}
