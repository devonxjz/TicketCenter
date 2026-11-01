/**
 * Dữ liệu minh họa cho prototype tĩnh, đồng bộ spec.md (05/10/2026).
 * DTO trình bày và projection kỹ thuật không phải class nghiệp vụ bổ sung.
 * Coupon discountValue, categoryId, orgName và nhãn giữ tương thích app.js.
 */
const SEED_DATA = {
  "users": [
    {
      "id": "u-001",
      "email": "buyer@ticketscenter.vn",
      "name": "Nguyễn Văn Hùng",
      "emailVerified": true,
      "emailVerifiedAt": "2026-09-01T08:30:00Z",
      "authVersion": 1,
      "userName": "buyer",
      "platformRole": "CUSTOMER",
      "organizationRoles": {
        "org-002": "MANAGER",
        "org-001": "CHECK_IN_STAFF"
      },
      "status": "ACTIVE"
    },
    {
      "id": "u-002",
      "email": "manager@vietnamshow.vn",
      "name": "Trần Minh Thảo",
      "emailVerified": true,
      "emailVerifiedAt": "2026-08-15T10:00:00Z",
      "authVersion": 2,
      "userName": "minhthao",
      "platformRole": "CUSTOMER",
      "organizationRoles": {
        "org-001": "MANAGER"
      },
      "status": "ACTIVE"
    },
    {
      "id": "u-003",
      "email": "checkin@vietnamshow.vn",
      "name": "Lê Hoàng Phúc",
      "emailVerified": true,
      "emailVerifiedAt": "2026-08-20T14:15:00Z",
      "authVersion": 1,
      "userName": "hoangphuc",
      "platformRole": "CUSTOMER",
      "organizationRoles": {
        "org-001": "CHECK_IN_STAFF"
      },
      "status": "ACTIVE"
    },
    {
      "id": "u-000",
      "email": "admin@ticketscenter.vn",
      "name": "Quản trị viên Hệ thống",
      "emailVerified": true,
      "emailVerifiedAt": "2026-01-01T00:00:00Z",
      "authVersion": 1,
      "userName": "admin",
      "platformRole": "ADMIN",
      "organizationRoles": {},
      "status": "ACTIVE"
    },
    {
      "id": "u-004",
      "userName": "ngoclan",
      "email": "ngoclan@amusemusic.vn",
      "name": "Trương Ngọc Lan",
      "emailVerified": true,
      "emailVerifiedAt": "2026-09-01T08:30:00Z",
      "platformRole": "CUSTOMER",
      "organizationRoles": {},
      "status": "ACTIVE"
    }
  ],
  "categories": [
    {
      "id": "all",
      "name": "Tất cả",
      "icon": "ph-squares-four",
      "valueType": "EventCategory"
    },
    {
      "id": "music",
      "name": "Nhạc sống",
      "icon": "ph-music-notes",
      "valueType": "EventCategory"
    },
    {
      "id": "stage",
      "name": "Sân khấu & Nghệ thuật",
      "icon": "ph-mask-happy",
      "valueType": "EventCategory"
    },
    {
      "id": "museum",
      "name": "Tham quan & Bảo tàng",
      "icon": "ph-bank",
      "valueType": "EventCategory"
    },
    {
      "id": "fanmeet",
      "name": "Fan Meeting",
      "icon": "ph-heart",
      "valueType": "EventCategory"
    },
    {
      "id": "sports",
      "name": "Thể thao",
      "icon": "ph-soccer-ball",
      "valueType": "EventCategory"
    },
    {
      "id": "conference",
      "name": "Hội thảo & Triển lãm",
      "icon": "ph-presentation",
      "valueType": "EventCategory"
    },
    {
      "id": "nightlife",
      "name": "Nightlife",
      "icon": "ph-martini",
      "valueType": "EventCategory"
    }
  ],
  "organizations": [
    {
      "id": "org-001",
      "name": "Vietnam Show Corporation",
      "contactEmail": "contact@vietnamshow.vn",
      "phone": "0908 123 456",
      "status": "APPROVED",
      "description": "Đơn vị tổ chức các đại nhạc hội và chương trình biểu diễn đỉnh cao tại Việt Nam.",
      "commissionRule": {
        "id": "cr-001",
        "name": "Standard Standard Tier",
        "ratePercent": 5,
        "fixedFee": 5000,
        "active": true
      },
      "requesterId": "u-002",
      "submittedAt": "2026-08-15T08:00:00Z",
      "approvalNote": "Hồ sơ được duyệt; cấp MANAGER và quy tắc phí ban đầu trong cùng giao dịch dự kiến."
    },
    {
      "id": "org-002",
      "name": "Bảo tàng Phụ nữ Việt Nam",
      "contactEmail": "info@vwm.gov.vn",
      "phone": "024 3825 9936",
      "status": "APPROVED",
      "description": "Không gian trưng bày di sản, văn hóa và triển lãm lịch sử phụ nữ Việt Nam.",
      "commissionRule": {
        "id": "cr-002",
        "name": "Di sản & Văn hóa",
        "ratePercent": 3,
        "fixedFee": 2000,
        "active": true
      },
      "requesterId": "u-001",
      "submittedAt": "2026-08-10T08:00:00Z",
      "approvalNote": "Hồ sơ được duyệt; cấp MANAGER và quy tắc phí ban đầu trong cùng giao dịch dự kiến."
    },
    {
      "id": "org-003",
      "name": "Amuse Music Production",
      "requesterId": "u-004",
      "contactEmail": "ngoclan@amusemusic.vn",
      "phone": "0912 345 678",
      "description": "Đơn vị sản xuất chuỗi minishow acoustic và hòa nhạc thính phòng độc lập.",
      "status": "PENDING_APPROVAL",
      "submittedAt": "2026-09-23T11:00:00Z",
      "rejectionReason": null
    }
  ],
  "events": [
    {
      "id": "evt-001",
      "orgId": "org-002",
      "orgName": "Bảo tàng Phụ nữ Việt Nam",
      "categoryId": "museum",
      "title": "Vé tham quan Bảo tàng Phụ nữ Việt Nam & Triển lãm Đặc biệt",
      "tagline": "Khám phá chiều sâu văn hóa, di sản áo dài và hình tượng người phụ nữ Việt",
      "coverImageUrl": "https://images.unsplash.com/photo-1554907984-15263bfd63bd?auto=format&fit=crop&w=1200&q=80",
      "locationName": "Bảo tàng Phụ nữ Việt Nam",
      "locationAddress": "36 Lý Thường Kiệt, Hàng Bài, Hoàn Kiếm, Hà Nội",
      "city": "Hà Nội",
      "status": "PUBLISHED",
      "saleStart": "2026-09-01T08:00:00",
      "saleEnd": "2026-10-31T17:00:00",
      "startTime": "2026-11-01T08:30:00",
      "endTime": "2026-11-01T17:30:00",
      "minPrice": 40000,
      "zones": [
        {
          "id": "z-01",
          "name": "Vé Tiêu Chuẩn Người Lớn",
          "type": "STANDING",
          "price": 40000,
          "capacity": 500,
          "heldCount": 12,
          "soldCount": 148
        },
        {
          "id": "z-02",
          "name": "Vé Trải Nghiệm Thuyết Minh & Audio Guide",
          "type": "STANDING",
          "price": 90000,
          "capacity": 200,
          "heldCount": 4,
          "soldCount": 65
        }
      ],
      "description": "Bảo tàng Phụ nữ Việt Nam là một trong những điểm đến văn hóa hàng đầu Thủ đô, lưu giữ hơn 40.000 hiện vật quý giá thể hiện vai trò của phụ nữ trong lịch sử dựng nước, kháng chiến và xây dựng đất nước.",
      "category": {
        "id": "museum",
        "name": "Tham quan & Bảo tàng",
        "icon": "ph-bank",
        "valueType": "EventCategory"
      },
      "venueName": "Bảo tàng Phụ nữ Việt Nam",
      "venueAddress": "36 Lý Thường Kiệt, Hàng Bài, Hoàn Kiếm, Hà Nội",
      "commissionRuleId": "cr-002"
    },
    {
      "id": "evt-002",
      "orgId": "org-001",
      "orgName": "Vietnam Show Corporation",
      "categoryId": "music",
      "title": "Live Concert: Symphony of Autumn 2026",
      "tagline": "Đêm hòa nhạc giao hưởng mùa thu quy tụ dàn nghệ sĩ thính phòng quốc tế",
      "coverImageUrl": "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?auto=format&fit=crop&w=1200&q=80",
      "locationName": "Nhà hát Lớn Hà Nội",
      "locationAddress": "01 Tràng Tiền, Phan Chu Trinh, Hoàn Kiếm, Hà Nội",
      "city": "Hà Nội",
      "status": "PUBLISHED",
      "saleStart": "2026-09-10T10:00:00",
      "saleEnd": "2026-10-15T18:00:00",
      "startTime": "2026-10-20T19:30:00",
      "endTime": "2026-10-20T22:30:00",
      "minPrice": 450000,
      "zones": [
        {
          "id": "z-vip",
          "name": "Khu VIP - Tầng 1 Trung Tâm",
          "type": "SEATED",
          "price": 1200000,
          "rows": [
            "A",
            "B"
          ],
          "seatsPerRow": 8,
          "seats": [
            {
              "id": "s-A1",
              "label": "A-01",
              "status": "AVAILABLE"
            },
            {
              "id": "s-A2",
              "label": "A-02",
              "status": "AVAILABLE"
            },
            {
              "id": "s-A3",
              "label": "A-03",
              "status": "SOLD"
            },
            {
              "id": "s-A4",
              "label": "A-04",
              "status": "HELD"
            },
            {
              "id": "s-A5",
              "label": "A-05",
              "status": "AVAILABLE"
            },
            {
              "id": "s-A6",
              "label": "A-06",
              "status": "AVAILABLE"
            },
            {
              "id": "s-A7",
              "label": "A-07",
              "status": "SOLD"
            },
            {
              "id": "s-A8",
              "label": "A-08",
              "status": "AVAILABLE"
            },
            {
              "id": "s-B1",
              "label": "B-01",
              "status": "AVAILABLE"
            },
            {
              "id": "s-B2",
              "label": "B-02",
              "status": "AVAILABLE"
            },
            {
              "id": "s-B3",
              "label": "B-03",
              "status": "AVAILABLE"
            },
            {
              "id": "s-B4",
              "label": "B-04",
              "status": "AVAILABLE"
            },
            {
              "id": "s-B5",
              "label": "B-05",
              "status": "SOLD"
            },
            {
              "id": "s-B6",
              "label": "B-06",
              "status": "AVAILABLE"
            },
            {
              "id": "s-B7",
              "label": "B-07",
              "status": "AVAILABLE"
            },
            {
              "id": "s-B8",
              "label": "B-08",
              "status": "AVAILABLE"
            }
          ]
        },
        {
          "id": "z-std",
          "name": "Khu Tiêu Chuẩn - Tầng 2",
          "type": "SEATED",
          "price": 450000,
          "rows": [
            "C",
            "D"
          ],
          "seatsPerRow": 8,
          "seats": [
            {
              "id": "s-C1",
              "label": "C-01",
              "status": "AVAILABLE"
            },
            {
              "id": "s-C2",
              "label": "C-02",
              "status": "AVAILABLE"
            },
            {
              "id": "s-C3",
              "label": "C-03",
              "status": "AVAILABLE"
            },
            {
              "id": "s-C4",
              "label": "C-04",
              "status": "AVAILABLE"
            },
            {
              "id": "s-C5",
              "label": "C-05",
              "status": "AVAILABLE"
            },
            {
              "id": "s-C6",
              "label": "C-06",
              "status": "AVAILABLE"
            },
            {
              "id": "s-C7",
              "label": "C-07",
              "status": "AVAILABLE"
            },
            {
              "id": "s-C8",
              "label": "C-08",
              "status": "AVAILABLE"
            },
            {
              "id": "s-D1",
              "label": "D-01",
              "status": "AVAILABLE"
            },
            {
              "id": "s-D2",
              "label": "D-02",
              "status": "AVAILABLE"
            },
            {
              "id": "s-D3",
              "label": "D-03",
              "status": "AVAILABLE"
            },
            {
              "id": "s-D4",
              "label": "D-04",
              "status": "AVAILABLE"
            },
            {
              "id": "s-D5",
              "label": "D-05",
              "status": "AVAILABLE"
            },
            {
              "id": "s-D6",
              "label": "D-06",
              "status": "AVAILABLE"
            },
            {
              "id": "s-D7",
              "label": "D-07",
              "status": "AVAILABLE"
            },
            {
              "id": "s-D8",
              "label": "D-08",
              "status": "AVAILABLE"
            }
          ]
        }
      ],
      "description": "Chương trình hòa nhạc mùa thu thường niên được chỉ huy bởi Nhạc trưởng khách mời hàng đầu châu Âu, biểu diễn những tuyệt phẩm của Tchaikovsky, Chopin và Dvořák.",
      "category": {
        "id": "music",
        "name": "Nhạc sống",
        "icon": "ph-music-notes",
        "valueType": "EventCategory"
      },
      "venueName": "Nhà hát Lớn Hà Nội",
      "venueAddress": "01 Tràng Tiền, Phan Chu Trinh, Hoàn Kiếm, Hà Nội",
      "commissionRuleId": "cr-001"
    },
    {
      "id": "evt-003",
      "orgId": "org-001",
      "orgName": "Vietnam Show Corporation",
      "categoryId": "stage",
      "title": "Vở Kịch: Hồn Trương Ba, Da Hàng Thịt (Phiên Bản Đương Đại)",
      "tagline": "Kịch bản kinh điển của Lưu Quang Vũ tái xuất với ngôn ngữ sân khấu mới",
      "coverImageUrl": "https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?auto=format&fit=crop&w=1200&q=80",
      "locationName": "Nhà hát Kịch Việt Nam",
      "locationAddress": "01 Tràng Tiền, Hoàn Kiếm, Hà Nội",
      "city": "Hà Nội",
      "status": "PUBLISHED",
      "saleStart": "2026-09-15T09:00:00",
      "saleEnd": "2026-10-25T18:00:00",
      "startTime": "2026-10-28T20:00:00",
      "endTime": "2026-10-28T22:30:00",
      "minPrice": 200000,
      "zones": [
        {
          "id": "z-k1",
          "name": "Hàng Ghế Danh Dự",
          "type": "SEATED",
          "price": 500000,
          "rows": [
            "A"
          ],
          "seatsPerRow": 6,
          "seats": []
        },
        {
          "id": "z-k2",
          "name": "Khán Phòng Phổ Thông",
          "type": "SEATED",
          "price": 200000,
          "rows": [
            "B",
            "C"
          ],
          "seatsPerRow": 10,
          "seats": []
        }
      ],
      "category": {
        "id": "stage",
        "name": "Sân khấu & Nghệ thuật",
        "icon": "ph-mask-happy",
        "valueType": "EventCategory"
      },
      "venueName": "Nhà hát Kịch Việt Nam",
      "venueAddress": "01 Tràng Tiền, Hoàn Kiếm, Hà Nội",
      "commissionRuleId": "cr-001"
    },
    {
      "id": "evt-004",
      "orgId": "org-001",
      "orgName": "Vietnam Show Corporation",
      "categoryId": "sports",
      "title": "Trận Chung Kết Bóng Chuyền Quốc Gia 2026",
      "tagline": "Màn so tài nảy lửa giữa hai đại diện xuất sắc nhất mùa giải",
      "coverImageUrl": "https://images.unsplash.com/photo-1612872087720-bb876e2e67d1?auto=format&fit=crop&w=1200&q=80",
      "locationName": "Cung Điền kinh Trong nhà Hà Nội",
      "locationAddress": "Trần Hữu Dực, Mỹ Đình 1, Nam Từ Liêm, Hà Nội",
      "city": "Hà Nội",
      "status": "PUBLISHED",
      "saleStart": "2026-09-20T08:00:00",
      "saleEnd": "2026-11-05T15:00:00",
      "startTime": "2026-11-06T18:30:00",
      "endTime": "2026-11-06T21:30:00",
      "minPrice": 150000,
      "zones": [
        {
          "id": "z-sp1",
          "name": "Khán Đài A",
          "type": "STANDING",
          "price": 300000,
          "capacity": 1000,
          "heldCount": 50,
          "soldCount": 450
        },
        {
          "id": "z-sp2",
          "name": "Khán Đài B",
          "type": "STANDING",
          "price": 150000,
          "capacity": 2000,
          "heldCount": 80,
          "soldCount": 890
        }
      ],
      "category": {
        "id": "sports",
        "name": "Thể thao",
        "icon": "ph-soccer-ball",
        "valueType": "EventCategory"
      },
      "venueName": "Cung Điền kinh Trong nhà Hà Nội",
      "venueAddress": "Trần Hữu Dực, Mỹ Đình 1, Nam Từ Liêm, Hà Nội",
      "commissionRuleId": "cr-001"
    },
    {
      "id": "evt-free",
      "orgId": "org-001",
      "orgName": "Vietnam Show Corporation",
      "categoryId": "conference",
      "category": {
        "id": "conference",
        "name": "Hội thảo & Triển lãm",
        "icon": "ph-presentation",
        "valueType": "EventCategory"
      },
      "title": "Giao lưu cộng đồng miễn phí (fixture 0đ)",
      "venueName": "Hội trường mẫu",
      "venueAddress": "Hà Nội",
      "status": "PUBLISHED",
      "saleStart": "2026-09-01T08:00:00",
      "saleEnd": "2026-10-28T18:00:00",
      "startTime": "2026-10-29T09:00:00",
      "endTime": "2026-10-29T11:00:00",
      "commissionRuleId": "cr-001",
      "zones": [
        {
          "id": "z-free",
          "name": "Khu đứng miễn phí",
          "type": "STANDING",
          "price": 0,
          "capacity": 100,
          "heldCount": 0,
          "soldCount": 0
        }
      ],
      "minPrice": 0
    }
  ],
  "coupons": [
    {
      "id": "cp-001",
      "orgId": "org-002",
      "code": "DISCOVERVN10",
      "discountType": "PERCENTAGE",
      "discountValue": 10,
      "maxUses": 200,
      "reservedCount": 3,
      "consumedCount": 42,
      "active": true,
      "validFrom": "2026-09-01T00:00:00",
      "validUntil": "2026-11-30T23:59:59"
    },
    {
      "id": "cp-002",
      "orgId": "org-001",
      "code": "AUTUMN50K",
      "discountType": "FIXED_AMOUNT",
      "discountValue": 50000,
      "maxUses": 100,
      "reservedCount": 2,
      "consumedCount": 38,
      "active": true,
      "validFrom": "2026-09-10T00:00:00",
      "validUntil": "2026-10-31T23:59:59"
    },
    {
      "id": "cp-003",
      "orgId": "org-001",
      "code": "VIPCONCERT25",
      "discountType": "PERCENTAGE",
      "discountValue": 25,
      "maxUses": 50,
      "reservedCount": 1,
      "consumedCount": 15,
      "active": true,
      "validFrom": "2026-09-10T00:00:00",
      "validUntil": "2026-10-20T23:59:59"
    }
  ],
  "currentHold": {
    "id": "hld-90214",
    "userId": "u-001",
    "eventId": "evt-002",
    "eventTitle": "Live Concert: Symphony of Autumn 2026",
    "status": "ACTIVE",
    "expiresAt": "2026-10-06T05:02:28.780Z",
    "items": [
      {
        "id": "hi-01",
        "zoneName": "Khu VIP - Tầng 1 Trung Tâm",
        "seatLabel": "A-01",
        "quantity": 1,
        "zoneId": "z-vip",
        "seatId": "s-A1",
        "unitPrice": 1200000
      },
      {
        "id": "hi-02",
        "zoneName": "Khu VIP - Tầng 1 Trung Tâm",
        "seatLabel": "A-02",
        "quantity": 1,
        "zoneId": "z-vip",
        "seatId": "s-A2",
        "unitPrice": 1200000
      }
    ],
    "createdAt": "2026-10-06T04:52:28.780Z"
  },
  "orders": [
    {
      "id": "ord-88391",
      "userId": "u-001",
      "eventId": "evt-001",
      "eventTitle": "Vé tham quan Bảo tàng Phụ nữ Việt Nam & Triển lãm Đặc biệt",
      "status": "PAID",
      "createdAt": "2026-09-22T14:20:00Z",
      "subtotalAmount": 180000,
      "discountAmount": 18000,
      "totalAmount": 162000,
      "couponCode": "DISCOVERVN10",
      "items": [
        {
          "id": "oi-101",
          "zoneName": "Vé Trải Nghiệm Thuyết Minh & Audio Guide",
          "quantity": 2,
          "unitPrice": 90000,
          "totalPaid": 162000,
          "zoneId": "z-02",
          "seatId": null
        }
      ],
      "tickets": [
        {
          "id": "tkt-001",
          "ticketCode": "TC-VWM-99214-A",
          "qrHash": "TICKET_VWM_001_8a9df2c4810e",
          "zoneName": "Vé Trải Nghiệm Thuyết Minh",
          "seatLabel": null,
          "status": "ACTIVE",
          "paidAmount": 81000,
          "usedAt": null,
          "orderId": "ord-88391",
          "orderItemId": "oi-101"
        },
        {
          "id": "tkt-002",
          "ticketCode": "TC-VWM-99215-B",
          "qrHash": "TICKET_VWM_002_7b8ec1d3920f",
          "zoneName": "Vé Trải Nghiệm Thuyết Minh",
          "seatLabel": null,
          "status": "ACTIVE",
          "paidAmount": 81000,
          "usedAt": null,
          "orderId": "ord-88391",
          "orderItemId": "oi-101"
        }
      ],
      "paidAt": "2026-09-22T14:23:45Z",
      "acceptedPaymentId": "pay-001",
      "payments": [
        {
          "id": "pay-001",
          "orderId": "ord-88391",
          "amount": 162000,
          "txnRef": "VNPAY-202609221420-88391",
          "method": "VNPAY Sandbox",
          "status": "CAPTURED",
          "paidAt": "2026-09-22T14:23:45Z"
        }
      ]
    },
    {
      "id": "ord-77120",
      "userId": "u-001",
      "eventId": "evt-003",
      "eventTitle": "Vở Kịch: Hồn Trương Ba, Da Hàng Thịt (Phiên Bản Đương Đại)",
      "status": "PAID",
      "createdAt": "2026-09-18T10:10:00Z",
      "subtotalAmount": 400000,
      "discountAmount": 0,
      "totalAmount": 400000,
      "couponCode": null,
      "tickets": [
        {
          "id": "tkt-003",
          "ticketCode": "TC-STAGE-44810",
          "qrHash": "TICKET_STAGE_003_110ae94d",
          "zoneName": "Khán Phòng Phổ Thông",
          "seatLabel": "B-05",
          "status": "USED",
          "paidAmount": 200000,
          "usedAt": "2026-10-28T12:45:10Z",
          "orderId": "ord-77120",
          "orderItemId": "oi-201"
        },
        {
          "id": "tkt-004",
          "ticketCode": "TC-STAGE-44811",
          "qrHash": "TICKET_STAGE_004_221bf85e",
          "zoneName": "Khán Phòng Phổ Thông",
          "seatLabel": "B-06",
          "status": "REFUND_PENDING",
          "paidAmount": 200000,
          "usedAt": null,
          "orderId": "ord-77120",
          "orderItemId": "oi-202"
        }
      ],
      "paidAt": "2026-09-18T10:12:10Z",
      "acceptedPaymentId": "pay-002",
      "payments": [
        {
          "id": "pay-002",
          "orderId": "ord-77120",
          "amount": 400000,
          "txnRef": "VNPAY-202609181010-77120",
          "method": "VNPAY Sandbox",
          "status": "CAPTURED",
          "paidAt": "2026-09-18T10:12:10Z"
        }
      ],
      "items": [
        {
          "id": "oi-201",
          "zoneId": "z-k2",
          "seatId": "s-B5",
          "zoneName": "Khán Phòng Phổ Thông",
          "seatLabel": "B-05",
          "quantity": 1,
          "unitPrice": 200000
        },
        {
          "id": "oi-202",
          "zoneId": "z-k2",
          "seatId": "s-B6",
          "zoneName": "Khán Phòng Phổ Thông",
          "seatLabel": "B-06",
          "quantity": 1,
          "unitPrice": 200000
        }
      ]
    },
    {
      "id": "ord-free",
      "userId": "u-001",
      "eventId": "evt-free",
      "eventTitle": "Giao lưu cộng đồng miễn phí (fixture 0đ)",
      "status": "PAID",
      "createdAt": "2026-09-22T14:20:00Z",
      "paidAt": "2026-09-22T14:21:00Z",
      "subtotalAmount": 0,
      "discountAmount": 0,
      "totalAmount": 0,
      "couponCode": null,
      "acceptedPaymentId": null,
      "payments": [],
      "items": [
        {
          "id": "oi-free",
          "zoneId": "z-free",
          "seatId": null,
          "zoneName": "Khu đứng miễn phí",
          "seatLabel": null,
          "quantity": 1,
          "unitPrice": 0
        }
      ],
      "tickets": [
        {
          "id": "tkt-free",
          "orderId": "ord-free",
          "orderItemId": "oi-free",
          "ticketCode": "TC-FREE-EXAMPLE",
          "zoneName": "Khu đứng miễn phí",
          "seatLabel": null,
          "status": "REFUNDED",
          "paidAmount": 0,
          "usedAt": null
        }
      ]
    },
    {
      "id": "ord-late",
      "userId": "u-001",
      "eventId": "evt-002",
      "status": "EXPIRED",
      "createdAt": "2026-09-23T09:00:00Z",
      "paidAt": null,
      "subtotalAmount": 1200000,
      "discountAmount": 0,
      "totalAmount": 1200000,
      "acceptedPaymentId": null,
      "payments": [
        {
          "id": "pay-failed",
          "orderId": "ord-late",
          "amount": 1200000,
          "txnRef": "SIM-FAILED-001",
          "status": "FAILED",
          "paidAt": null
        },
        {
          "id": "pay-late",
          "orderId": "ord-late",
          "amount": 1200000,
          "txnRef": "SIM-LATE-002",
          "status": "CAPTURED",
          "paidAt": "2026-09-23T09:15:00Z"
        }
      ],
      "items": [
        {
          "id": "oi-late",
          "zoneId": "z-vip",
          "seatId": "s-A1",
          "zoneName": "Khu VIP - Tầng 1 Trung Tâm",
          "seatLabel": "A-01",
          "quantity": 1,
          "unitPrice": 1200000
        }
      ],
      "tickets": []
    }
  ],
  "settlements": [
    {
      "id": "stl-001",
      "eventId": "evt-001",
      "eventTitle": "Vé tham quan Bảo tàng Phụ nữ Việt Nam & Triển lãm Đặc biệt",
      "orgName": "Bảo tàng Phụ nữ Việt Nam",
      "commissionRuleName": "Di sản & Văn hóa (3% + 2k/đơn)",
      "netPayable": 155140,
      "status": "CONFIRMED",
      "grossRevenue": 162000,
      "totalRefund": 0,
      "totalCommission": 6860,
      "paidAmount": 100000,
      "pendingAmount": 50000,
      "availableToPay": 5140,
      "confirmedAt": "2026-11-02T09:00:00Z"
    }
  ],
  "auditLogs": [
    {
      "id": "log-101",
      "timestamp": "2026-09-24T09:30:15Z",
      "actor": "admin@ticketscenter.vn",
      "action": "APPROVE_ORGANIZATION",
      "targetEntity": "Organization #org-001",
      "details": "Duyệt thành lập Vietnam Show Corp, gán CommissionRule cr-001 và cấp quyền MANAGER."
    },
    {
      "id": "log-102",
      "timestamp": "2026-09-24T08:15:00Z",
      "actor": "buyer@ticketscenter.vn",
      "action": "REQUEST_TICKET_REFUND",
      "targetEntity": "Ticket #tkt-004",
      "details": "Khách gửi yêu cầu hoàn 200,000đ cho vé B-06 (Hồn Trương Ba, Da Hàng Thịt)."
    },
    {
      "id": "log-103",
      "timestamp": "2026-10-28T12:45:10Z",
      "actor": "checkin@vietnamshow.vn",
      "action": "CHECK_IN_SUCCESS",
      "targetEntity": "Ticket #tkt-003",
      "details": "Check-in thành công qua quét camera QR tại Cửa A - Khán Phòng."
    },
    {
      "id": "log-104",
      "timestamp": "2026-09-22T14:23:45Z",
      "actor": "SYSTEM",
      "action": "VNPAY_IPN_CAPTURED",
      "targetEntity": "Order #ord-88391",
      "details": "Nhận IPN hợp lệ, phát hành 2 vé QR, tiêu thụ 1 lượt coupon DISCOVERVN10."
    }
  ],
  "prototypeInfo": {
    "type": "STATIC_MOCK",
    "canonicalSpec": "../../../spec.md",
    "canonicalDiagram": "../../classdiagram/diagram.md",
    "businessClassCount": 15,
    "note": "Dữ liệu minh họa dạng DTO; không có backend, gửi email, VNPAY, chuyển tiền hoặc kiểm tra quyền thật. Các fixture tài chính/quét dùng thời điểm giả lập riêng."
  },
  "refunds": [
    {
      "id": "refund-001",
      "orderId": "ord-77120",
      "paymentId": "pay-002",
      "ticketIds": [
        "tkt-004"
      ],
      "amount": 200000,
      "purpose": "CUSTOMER_REFUND",
      "reasonType": "CUSTOMER_REQUEST",
      "customerReason": "Gặp sự cố cá nhân đột xuất nên không thể thu xếp đến xem đúng lịch diễn.",
      "status": "REQUESTED",
      "createdAt": "2026-09-23T09:15:00Z",
      "reviewedAt": null,
      "rejectionReason": null,
      "currentAttemptId": null,
      "providerReference": null,
      "processedAt": null
    },
    {
      "id": "refund-free",
      "orderId": "ord-free",
      "paymentId": null,
      "ticketIds": [
        "tkt-free"
      ],
      "amount": 0,
      "purpose": "CUSTOMER_REFUND",
      "reasonType": "CUSTOMER_REQUEST",
      "customerReason": "Không thể tham dự",
      "status": "COMPLETED",
      "createdAt": "2026-09-23T09:00:00Z",
      "reviewedAt": "2026-09-23T10:00:00Z",
      "rejectionReason": null,
      "currentAttemptId": null,
      "providerReference": null,
      "processedAt": null
    },
    {
      "id": "refund-compensation",
      "orderId": "ord-late",
      "paymentId": "pay-late",
      "ticketIds": [],
      "amount": 1200000,
      "purpose": "PAYMENT_COMPENSATION",
      "reasonType": null,
      "customerReason": null,
      "status": "NEEDS_RECONCILIATION",
      "currentAttemptId": "attempt-comp-001",
      "providerReference": null,
      "processedAt": null
    }
  ],
  "refundTransferLogs": [
    {
      "attemptId": "attempt-comp-001",
      "refundId": "refund-compensation",
      "status": "UNKNOWN",
      "providerReference": null,
      "processedAt": null
    }
  ],
  "settlementOrderSnapshots": [
    {
      "settlementId": "stl-001",
      "orderId": "ord-88391",
      "grossAmount": 162000,
      "refundAmount": 0,
      "remainingAmount": 162000,
      "commissionAmount": 6860,
      "netAmount": 155140
    }
  ],
  "settlementTransferLogs": [
    {
      "settlementId": "stl-001",
      "payoutId": "transfer-001",
      "amount": 100000,
      "reference": "SIM-VWM-20261102-01",
      "status": "SUCCEEDED",
      "processedAt": "2026-11-02T10:00:00Z"
    },
    {
      "settlementId": "stl-001",
      "payoutId": "transfer-002",
      "amount": 50000,
      "reference": null,
      "status": "PENDING",
      "processedAt": null
    }
  ]
};

window.SEED_DATA = SEED_DATA;
