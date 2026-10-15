SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER VIEW dbo.vw_ManagedEvents AS
SELECT e.id,e.organization_id,e.category_id,e.title,e.description,e.venue_name,e.venue_address,
 e.sale_start,e.sale_end,e.start_time,e.end_time,e.status,e.version,NULLIF(e.event_image,'') image_url,
 e.event_image_public_id,cat.slug category_slug,e.event_image
FROM dbo.tc_events e JOIN dbo.tc_event_categories cat ON cat.id=e.category_id
WHERE e.archived_at IS NULL;
GO
