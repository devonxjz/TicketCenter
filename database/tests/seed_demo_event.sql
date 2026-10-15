SET NOCOUNT ON;
BEGIN TRANSACTION;

DECLARE @orgId uniqueidentifier = '16E30320-D98F-4A61-B685-4B83DC7C22AB';
DECLARE @catId uniqueidentifier = 'CFAF4D2D-9D44-4969-ADA3-A1033C9C4823';
DECLARE @eventId uniqueidentifier = 'A1111111-1111-1111-1111-111111111111';
DECLARE @zoneVip uniqueidentifier = 'B1111111-1111-1111-1111-111111111111';
DECLARE @zoneGa uniqueidentifier  = 'B2222222-2222-2222-2222-222222222222';
DECLARE @coupon1 uniqueidentifier = 'C1111111-1111-1111-1111-111111111111';
DECLARE @coupon2 uniqueidentifier = 'C2222222-2222-2222-2222-222222222222';

-- 1. Xóa dữ liệu cũ nếu có
IF EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @eventId)
BEGIN
    UPDATE dbo.tc_events SET status = 'DRAFT' WHERE id = @eventId;
    DELETE FROM dbo.tc_seats WHERE zone_id IN (SELECT id FROM dbo.tc_zones WHERE event_id = @eventId);
    DELETE FROM dbo.tc_zones WHERE event_id = @eventId;
    DELETE FROM dbo.tc_events WHERE id = @eventId;
END;
DELETE FROM dbo.tc_coupons WHERE id IN (@coupon1, @coupon2);

-- 2. Thêm sự kiện ở trạng thái DRAFT
INSERT INTO dbo.tc_events (
    id, organization_id, category_id, commission_rule_id,
    title, description, venue_name, venue_address,
    sale_start, sale_end, start_time, end_time,
    status, version, created_at,
    event_image, event_image_public_id
) VALUES (
    @eventId, @orgId, @catId, NULL,
    N'Concert Mùa Thu 2026',
    N'Đại nhạc hội âm nhạc đặc sắc mùa thu với sự góp mặt của nhiều nghệ sĩ nổi tiếng. Hệ thống âm thanh ánh sáng đạt chuẩn quốc tế.',
    N'Nhà thi đấu Phú Thọ',
    N'01 Lữ Gia, Phường 15, Quận 11, TP. Hồ Chí Minh',
    '2026-10-01 00:00:00',
    '2026-10-31 23:59:59',
    '2026-11-05 19:00:00',
    '2026-11-05 22:30:00',
    'DRAFT', 1, SYSUTCDATETIME(),
    'https://res.cloudinary.com/simulated/image/upload/sample_concert.png',
    'sample_concert'
);

-- 3. Thêm Khu VIP (SEATED)
INSERT INTO dbo.tc_zones (
    id, event_id, name, type, price,
    capacity, held_quantity, sold_quantity, version
) VALUES (
    @zoneVip, @eventId, N'Khu VIP (Có ghế)', 'SEATED', 500000,
    NULL, 0, 0, 1
);

-- 4. Thêm ghế cho Khu VIP
INSERT INTO dbo.tc_seats (id, zone_id, row_name, seat_number, label, status, version) VALUES
(NEWID(), @zoneVip, N'A', 1, N'A-01', 'AVAILABLE', 1),
(NEWID(), @zoneVip, N'A', 2, N'A-02', 'AVAILABLE', 1),
(NEWID(), @zoneVip, N'A', 3, N'A-03', 'AVAILABLE', 1),
(NEWID(), @zoneVip, N'A', 4, N'A-04', 'AVAILABLE', 1),
(NEWID(), @zoneVip, N'A', 5, N'A-05', 'AVAILABLE', 1),
(NEWID(), @zoneVip, N'B', 1, N'B-01', 'AVAILABLE', 1),
(NEWID(), @zoneVip, N'B', 2, N'B-02', 'AVAILABLE', 1),
(NEWID(), @zoneVip, N'B', 3, N'B-03', 'AVAILABLE', 1),
(NEWID(), @zoneVip, N'B', 4, N'B-04', 'AVAILABLE', 1),
(NEWID(), @zoneVip, N'B', 5, N'B-05', 'AVAILABLE', 1);

-- 5. Thêm Khu GA (STANDING)
INSERT INTO dbo.tc_zones (
    id, event_id, name, type, price,
    capacity, held_quantity, sold_quantity, version
) VALUES (
    @zoneGa, @eventId, N'Khu Thường GA (Đứng)', 'STANDING', 250000,
    100, 0, 0, 1
);

-- 6. Công khai sự kiện sang PUBLISHED
UPDATE dbo.tc_events SET status = 'PUBLISHED' WHERE id = @eventId;

-- 7. Thêm Coupon GIAM20 (Giảm 20%) và GIAM50K (Giảm 50.000đ)
INSERT INTO dbo.tc_coupons (
    id, organization_id, code, discount_type,
    percentage_value, fixed_amount, max_discount_amount,
    max_uses, valid_from, valid_to, active, version,
    created_at, updated_at
) VALUES (
    @coupon1, @orgId, 'GIAM20', 'PERCENTAGE',
    20.00, NULL, 100000.00,
    50, '2026-10-01 00:00:00', '2026-12-31 23:59:59',
    1, 1, SYSUTCDATETIME(), SYSUTCDATETIME()
), (
    @coupon2, @orgId, 'GIAM50K', 'FIXED_AMOUNT',
    NULL, 50000.00, NULL,
    100, '2026-10-01 00:00:00', '2026-12-31 23:59:59',
    1, 1, SYSUTCDATETIME(), SYSUTCDATETIME()
);

COMMIT;
PRINT 'SAMPLE_EVENT_AND_COUPONS_SEEDED_SUCCESSFULLY';
