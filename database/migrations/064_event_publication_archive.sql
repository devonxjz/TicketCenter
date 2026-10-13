-- Withdrawing a listing preserves orders, payment history and the event's actual lifecycle.
-- Data selection belongs to the database; this is not a demo-name filter.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
IF COL_LENGTH('dbo.tc_events','archived_at') IS NULL
BEGIN
 ALTER TABLE dbo.tc_events ADD archived_at datetime2(3) NULL,
  archived_by uniqueidentifier NULL REFERENCES dbo.tc_users(id),archive_reason nvarchar(500) NULL;
END;
GO
CREATE OR ALTER VIEW dbo.vw_PublicEvents AS
SELECT e.id event_id,e.organization_id,e.category_id,e.title,e.description,
 e.event_image cover_image_url,e.venue_name,e.venue_address,e.sale_start,e.sale_end,
 e.start_time,e.end_time,MIN(z.price) minimum_price
FROM dbo.tc_events e LEFT JOIN dbo.tc_zones z ON z.event_id=e.id
WHERE e.status='PUBLISHED' AND e.archived_at IS NULL
GROUP BY e.id,e.organization_id,e.category_id,e.title,e.description,e.event_image,
 e.venue_name,e.venue_address,e.sale_start,e.sale_end,e.start_time,e.end_time;
GO
