/* 052: remove objects nothing reads or calls.
   - vw_JpaCouponRedemptions, vw_JpaOrganizationRequests: existed only on Azure (never in a repo migration); no entity, query or module uses them.
   - usp_SubmitEvent, usp_RejectEvent, usp_SaveZoneDraft, usp_SaveSeatDraft: superseded by usp_EventCommand (SUBMIT/REJECT/ZONE_x/SEATED).
   - fn_ReadOwnMemberships, fn_ReportOrganizationVisibility: no caller in the application, tests or other modules.
   - sysdiagrams and its helper routines: SSMS database-diagram support objects, empty.
   Objects named in docs/references/SPEC.md (e.g. usp_CancelEvent) are intentionally kept. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
IF OBJECT_ID('dbo.sysdiagrams','U') IS NOT NULL AND EXISTS(SELECT 1 FROM dbo.sysdiagrams)
 THROW 52070,'052 refuses to drop sysdiagrams while it holds diagrams',1;
GO
DROP VIEW IF EXISTS dbo.vw_JpaCouponRedemptions, dbo.vw_JpaOrganizationRequests;
DROP PROCEDURE IF EXISTS dbo.usp_SubmitEvent, dbo.usp_RejectEvent, dbo.usp_SaveZoneDraft, dbo.usp_SaveSeatDraft;
DROP FUNCTION IF EXISTS dbo.fn_ReadOwnMemberships, dbo.fn_ReportOrganizationVisibility;
GO
DROP PROCEDURE IF EXISTS dbo.sp_alterdiagram, dbo.sp_creatediagram, dbo.sp_dropdiagram, dbo.sp_helpdiagramdefinition,
 dbo.sp_helpdiagrams, dbo.sp_renamediagram, dbo.sp_upgraddiagrams;
DROP FUNCTION IF EXISTS dbo.fn_diagramobjects;
DROP TABLE IF EXISTS dbo.sysdiagrams;
GO
