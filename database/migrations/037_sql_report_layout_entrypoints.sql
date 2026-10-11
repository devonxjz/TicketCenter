SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER PROCEDURE dbo.usp_SaveZoneDraft @zone_id uniqueidentifier,@event_id uniqueidentifier,@actor_id uniqueidentifier,
 @name nvarchar(120),@type varchar(20),@price decimal(19,0),@capacity int=NULL,@expected_version bigint=NULL
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @org uniqueidentifier;
 SELECT @org=organization_id FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id AND status IN('DRAFT','REJECTED');
 IF @org IS NULL THROW 51812,'Layout is editable only before approval',1;
 EXEC dbo.usp_RequireOrganizationManager @actor_id,@org;
 IF @price IS NULL OR @price<0 OR @type IS NULL OR @type NOT IN('SEATED','STANDING') THROW 51822,'Invalid zone terms',1;
 IF @expected_version IS NULL
  INSERT dbo.tc_zones(id,event_id,name,type,price,capacity) VALUES(@zone_id,@event_id,@name,@type,@price,@capacity);
 ELSE BEGIN
  UPDATE dbo.tc_zones SET name=@name,type=@type,price=@price,capacity=@capacity,version=version+1
  WHERE id=@zone_id AND event_id=@event_id AND version=@expected_version AND held_quantity=0 AND sold_quantity=0;
  IF @@ROWCOUNT<>1 THROW 51813,'Zone version or inventory conflict',1;
 END;
 INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id) VALUES(@actor_id,'ZONE_DRAFT_SAVED','ZONE',@zone_id);
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_SaveSeatDraft @seat_id uniqueidentifier,@zone_id uniqueidentifier,@actor_id uniqueidentifier,
 @row_name nvarchar(20),@seat_number int,@label nvarchar(50),@expected_version bigint=NULL
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @event uniqueidentifier,@org uniqueidentifier,@lock_count bigint;
 SELECT @event=event_id FROM dbo.tc_zones WHERE id=@zone_id AND type='SEATED';
 SELECT @org=organization_id FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event AND status IN('DRAFT','REJECTED');
 IF @org IS NULL THROW 51812,'Seated layout is editable only before approval',1;
 EXEC dbo.usp_RequireOrganizationManager @actor_id,@org;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_zones WITH(UPDLOCK,HOLDLOCK) WHERE id=@zone_id;
 IF @expected_version IS NULL
  INSERT dbo.tc_seats(id,zone_id,row_name,seat_number,label,status) VALUES(@seat_id,@zone_id,@row_name,@seat_number,@label,'AVAILABLE');
 ELSE BEGIN
  UPDATE dbo.tc_seats SET row_name=@row_name,seat_number=@seat_number,label=@label,version=version+1 WHERE id=@seat_id AND zone_id=@zone_id AND version=@expected_version AND status='AVAILABLE';
  IF @@ROWCOUNT<>1 THROW 51813,'Seat version or inventory conflict',1;
 END;
 INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id) VALUES(@actor_id,'SEAT_DRAFT_SAVED','SEAT',@seat_id);
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
GRANT EXECUTE ON dbo.usp_SaveZoneDraft TO tc_manager;
GRANT EXECUTE ON dbo.usp_SaveSeatDraft TO tc_manager;
GO
