package vn.ticketscenter.event.integration;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.storage.CloudinaryStorageService;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit cleanup of this suite's retained HTTP assets, after browser acceptance only. */
class EventHttpFixtureCleanupIT {
    @Test void removeOnlyUnreferencedDedicatedHttpFixtureAssets() throws Exception {
        assertTrue(AppConfig.getBoolean("TC_DONG_CLOUDINARY_ACCEPTANCE",false),"Explicit fixture/provider cleanup opt-in required");
        var assets=new LinkedHashMap<UUID,String>();
        try(Connection c=DongSqlFixture.connection(null);var q=c.prepareStatement("SELECT id,event_image_public_id FROM dbo.tc_events WHERE title=? AND event_image_public_id IS NOT NULL")) {
            q.setNString(1,"Đêm nhạc nghiệm thu Đông");try(var rows=q.executeQuery()){while(rows.next())assets.put(UUID.fromString(rows.getString(1)),rows.getString(2));}
            c.setAutoCommit(false);
            for(var asset:assets.entrySet()) {
                assertTrue(asset.getValue().startsWith("ticketscenter/events/"+asset.getKey()+"/"),"Only the fixture's Event folder may be cleaned");
                try(var guard=c.prepareStatement("SELECT (SELECT COUNT_BIG(*) FROM dbo.tc_orders WHERE event_id=?)+(SELECT COUNT_BIG(*) FROM dbo.tc_ticket_holds WHERE event_id=?)")) {
                    guard.setString(1,asset.getKey().toString());guard.setString(2,asset.getKey().toString());
                    try(var rows=guard.executeQuery()){rows.next();assertEquals(0,rows.getLong(1),"Refuse cleanup of an Event with transactions");}
                }
                DongSqlFixture.execute(c,"UPDATE dbo.tc_events SET status='DRAFT' WHERE id=?",asset.getKey());
                DongSqlFixture.execute(c,"DELETE s FROM dbo.tc_seats s JOIN dbo.tc_zones z ON z.id=s.zone_id WHERE z.event_id=?",asset.getKey());
                DongSqlFixture.execute(c,"DELETE dbo.tc_zones WHERE event_id=?",asset.getKey());
                DongSqlFixture.execute(c,"DELETE dbo.tc_events WHERE id=?",asset.getKey());
            }
            c.commit();
        }
        var provider=CloudinaryStorageService.fromEnvironment();
        for(String publicId:assets.values()) {
            try(Connection c=DongSqlFixture.connection(null);var q=c.prepareStatement("SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE event_image_public_id=?")) {
                q.setString(1,publicId);try(var rows=q.executeQuery()){rows.next();assertEquals(0,rows.getLong(1),"Never delete a referenced asset");}
            }
            provider.delete(publicId);
        }
    }
}
