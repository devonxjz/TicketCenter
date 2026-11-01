/*
  069 · Auth principal reads organization memberships

  Reconstructed on 2026-10-27 from the Azure DB (ticketcenter-backup). The original file was applied on
  2026-10-16 but never committed; the ledger row `069_auth_membership_reads` keeps the checksum of that lost
  file, so this file's checksum differs by design. Its effect is the column-level SELECT grants that tc_auth
  holds on the Azure DB and that no other migration creates: login reads the caller's memberships and the
  organization names under the AUTH principal.

  Shares number 069 with 069_event_checkin_open_override.sql (see APPLY_ORDER.md). Re-running is harmless.
*/
IF DATABASE_PRINCIPAL_ID('tc_auth') IS NOT NULL
BEGIN
    GRANT SELECT (user_id, organization_id, role, active) ON dbo.tc_organization_memberships TO tc_auth;
    GRANT SELECT (id, name) ON dbo.tc_organizations TO tc_auth;
END;
GO
