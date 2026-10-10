package vn.ticketscenter.event.integration;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.event.repository.EventRepositoryFixture;
import vn.ticketscenter.event.service.EventCatalogService;
import vn.ticketscenter.storage.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real Cloudinary upload to a dedicated test Event, followed by verified cleanup. No skip. */
class EventCloudinarySqlServerIT {
    @Test void realCoverUploadCommitsUrlPublishesAndCleansOnlyItsOwnAsset() throws Exception {
        assertTrue(AppConfig.getBoolean("TC_DONG_CLOUDINARY_ACCEPTANCE",false),"Explicit Cloudinary acceptance opt-in required");
        var fixture=new DongSqlFixture();UUID event=fixture.draft();fixture.seated(event,"Live provider seats",1,2);
        var provider=CloudinaryStorageService.fromEnvironment();List<StoredAsset> uploaded=new ArrayList<>();
        StorageService tracking=new StorageService(){
            public StoredAsset upload(InputStream input,String folder) throws IOException {var asset=provider.upload(input,folder);uploaded.add(asset);return asset;}
            public void delete(String id) throws IOException {provider.delete(id);}
            public String getUrl(String id){return provider.getUrl(id);}
        };
        try {
            var result=fixture.covers(tracking,(e,a)->fail("Successful upload must not create an orphan")).upload(fixture.actor,event,UUID.randomUUID().toString(),new ByteArrayInputStream(DongSqlFixture.png()));
            assertEquals(200,result.status());assertEquals(1,uploaded.size());
            fixture.service.mutate(fixture.actor,UUID.randomUUID().toString(),fixture.command("SUBMIT",event,null,Map.of()));
            fixture.service.mutate(fixture.adminActor,UUID.randomUUID().toString(),fixture.command("PUBLISH",event,null,Map.of("commissionRuleId",fixture.rule.toString())));
            try(Connection c=DongSqlFixture.connection("dong_buyer")){assertTrue(new EventCatalogService(EventRepositoryFixture.repository(c),fixture.cache).event(event).contains(uploaded.getFirst().secureUrl()));}
        } finally {
            // Dedicated fixture only: make its Event deletable, then use the real feature transaction.
            try(Connection c=DongSqlFixture.connection(null)){DongSqlFixture.execute(c,"UPDATE dbo.tc_events SET status='DRAFT' WHERE id=?",event);}
            fixture.service.mutate(fixture.actor,UUID.randomUUID().toString(),fixture.command("DELETE",event,null,Map.of()));
            for(StoredAsset asset:uploaded) {
                try(Connection c=DongSqlFixture.connection(null);var q=c.prepareStatement("SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE event_image_public_id=?")){
                    q.setString(1,asset.publicId());try(var rows=q.executeQuery()){rows.next();assertEquals(0,rows.getLong(1),"Never delete a referenced image");}
                }
                provider.delete(asset.publicId());
            }
        }
    }
}
