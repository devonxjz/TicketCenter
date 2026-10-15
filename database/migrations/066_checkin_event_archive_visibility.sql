SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER VIEW dbo.vw_CheckInEvents AS
SELECT e.id,e.organization_id,e.title,e.venue_name,e.start_time,e.end_time,e.status
FROM dbo.tc_events e WHERE e.archived_at IS NULL;
GO
