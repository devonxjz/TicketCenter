SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON;
GO
IF COL_LENGTH('dbo.tc_order_items','event_id') IS NULL ALTER TABLE dbo.tc_order_items ADD event_id uniqueidentifier NULL;
GO
-- The retained insert contract supplies order_id; fill its event before committing the row.
UPDATE i SET event_id=o.event_id FROM dbo.tc_order_items i JOIN dbo.tc_orders o ON o.id=i.order_id WHERE i.event_id IS NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_orders') AND name='UQ_Order_Id_Event_Report')
 CREATE UNIQUE INDEX UQ_Order_Id_Event_Report ON dbo.tc_orders(id,event_id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_zones') AND name='UQ_Zone_Id_Event_Report')
 CREATE UNIQUE INDEX UQ_Zone_Id_Event_Report ON dbo.tc_zones(id,event_id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_seats') AND name='UQ_Seat_Id_Zone_Report')
 CREATE UNIQUE INDEX UQ_Seat_Id_Zone_Report ON dbo.tc_seats(id,zone_id);
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='FK_OrderItem_OrderEvent_Report')
 ALTER TABLE dbo.tc_order_items WITH CHECK ADD CONSTRAINT FK_OrderItem_OrderEvent_Report FOREIGN KEY(order_id,event_id) REFERENCES dbo.tc_orders(id,event_id);
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='FK_OrderItem_ZoneEvent_Report')
 ALTER TABLE dbo.tc_order_items WITH CHECK ADD CONSTRAINT FK_OrderItem_ZoneEvent_Report FOREIGN KEY(zone_id,event_id) REFERENCES dbo.tc_zones(id,event_id);
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='FK_OrderItem_SeatZone_Report')
 ALTER TABLE dbo.tc_order_items WITH CHECK ADD CONSTRAINT FK_OrderItem_SeatZone_Report FOREIGN KEY(seat_id,zone_id) REFERENCES dbo.tc_seats(id,zone_id);
GO
CREATE OR ALTER TRIGGER dbo.trg_OrderItem_ValidateEventSeat ON dbo.tc_order_items AFTER INSERT,UPDATE AS
BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.tc_orders o ON o.id=i.order_id JOIN dbo.tc_zones z ON z.id=i.zone_id LEFT JOIN dbo.tc_seats s ON s.id=i.seat_id
 WHERE z.event_id<>o.event_id OR (i.event_id IS NOT NULL AND i.event_id<>o.event_id) OR (i.seat_id IS NOT NULL AND s.zone_id<>i.zone_id)) THROW 51820,'Order item zone or seat belongs to another event',1;
 UPDATE i SET event_id=o.event_id FROM dbo.tc_order_items i JOIN inserted n ON n.id=i.id JOIN dbo.tc_orders o ON o.id=i.order_id WHERE i.event_id IS NULL;
END;
GO
