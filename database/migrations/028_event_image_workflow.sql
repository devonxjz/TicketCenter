SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
IF OBJECT_ID(N'dbo.tc_event_image_requests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_event_image_requests (
        actor_id uniqueidentifier NOT NULL,
        request_id uniqueidentifier NOT NULL,
        fingerprint char(64) NOT NULL,
        operation varchar(10) NOT NULL,
        event_id uniqueidentifier NOT NULL,
        organization_id uniqueidentifier NOT NULL,
        expected_version bigint NOT NULL,
        public_id varchar(255) NULL,
        state varchar(12) NOT NULL,
        lease_token uniqueidentifier NOT NULL,
        lease_until datetime2(3) NOT NULL,
        result_json nvarchar(max) NULL,
        created_at datetime2(3) NOT NULL CONSTRAINT DF_EventImageRequest_Created DEFAULT SYSUTCDATETIME(),
        updated_at datetime2(3) NOT NULL CONSTRAINT DF_EventImageRequest_Updated DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_EventImageRequests PRIMARY KEY(actor_id, request_id),
        CONSTRAINT CK_EventImageRequest_State CHECK(state IN ('PENDING','COMMITTED','ABORTED')),
        CONSTRAINT CK_EventImageRequest_Operation CHECK(operation IN ('CREATE','EDIT','COVER','MIGRATE')),
        CONSTRAINT CK_EventImageRequest_Result CHECK(state <> 'COMMITTED' OR result_json IS NOT NULL)
    );
    CREATE INDEX IX_EventImageRequests_PublicState ON dbo.tc_event_image_requests(public_id,state);
END
GO
IF OBJECT_ID(N'dbo.tc_event_image_assets', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_event_image_assets (
        public_id varchar(255) NOT NULL CONSTRAINT PK_EventImageAssets PRIMARY KEY,
        event_id uniqueidentifier NOT NULL,
        state varchar(16) NOT NULL,
        secure_url varchar(2048) NULL,
        lease_token uniqueidentifier NULL,
        lease_until datetime2(3) NULL,
        available_at datetime2(3) NOT NULL CONSTRAINT DF_EventImageAsset_Available DEFAULT SYSUTCDATETIME(),
        attempts int NOT NULL CONSTRAINT DF_EventImageAsset_Attempts DEFAULT 0,
        created_at datetime2(3) NOT NULL CONSTRAINT DF_EventImageAsset_Created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_EventImageAsset_State CHECK(state IN ('AVAILABLE','DELETE_PENDING','DELETED')),
        CONSTRAINT CK_EventImageAsset_Namespace CHECK(public_id LIKE 'ticketcenter/events/%')
    );
    CREATE INDEX IX_EventImageAssets_Cleanup ON dbo.tc_event_image_assets(state,available_at);
END
GO
IF DATABASE_PRINCIPAL_ID(N'tc_manager') IS NOT NULL
BEGIN
    GRANT SELECT, INSERT, UPDATE ON dbo.tc_event_image_requests TO tc_manager;
    GRANT SELECT, INSERT, UPDATE ON dbo.tc_event_image_assets TO tc_manager;
    GRANT SELECT ON dbo.tc_users (id,status,auth_version) TO tc_manager;
    GRANT SELECT ON dbo.tc_organization_memberships TO tc_manager;
    GRANT SELECT ON dbo.tc_event_categories TO tc_manager;
    GRANT SELECT ON dbo.tc_organizations TO tc_manager;
END
GO
IF DATABASE_PRINCIPAL_ID(N'tc_worker') IS NOT NULL
BEGIN
    GRANT SELECT, UPDATE ON dbo.tc_event_image_requests TO tc_worker;
    GRANT SELECT, UPDATE ON dbo.tc_event_image_assets TO tc_worker;
    GRANT SELECT ON dbo.tc_events (id,event_image_public_id,event_image,cover_image_url) TO tc_worker;
    GRANT SELECT ON dbo.tc_users (id,avatar_public_id,avatar) TO tc_worker;
END
GO
