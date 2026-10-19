# 3-Week Code Reduction Refactoring Plan

**Goal:** Reduce codebase from 26,422 lines to <22,000 lines while improving quality (~15% reduction).

**Status:** Weeks 1-2 committed, Week 3 plan documented.

---

## ✅ WEEK 1-2 Completed

### Commit 1: Universal DTO Serialization
- **DtoSerializer** - Reflection-based JSON serializer (eliminates manual toJson() methods)
- **JsonSerializable** interface for type safety
- **HttpResponses.dataDto/createdDto** helpers for automatic serialization
- **Savings:** 50-100 lines per DTO × 46 DTOs = **potential 2,300+ lines**

### Commit 2: BaseApiServlet
- Eliminates servlet error handling boilerplate (30 lines per servlet)
- Provides `respondData`, `respondCreated`, `respondError` helpers  
- Consolidates HTTP method routing (GET/POST/PUT/DELETE → doRequest)
- **Savings:** 30 lines × 60 servlets = **1,800 lines**

### Consolidations Completed
- TicketSummaryDto + TicketDetailDto → **TicketDto** (171 → 50 lines, -121 lines)
- OrderDetailDto cleaned up (171 → 43 lines, -128 lines)
- **Total Week 1-2:** ~2,000+ lines saved

---

## 📋 WEEK 3 Remaining Work (To Execute)

### Phase 1: Remove Wrapper DTOs (~500 lines)
These DTOs exist only to wrap other DTOs or simple data:

1. **OrganizationRequestPageDto** (wrap list + pagination)
   - Replace with `Page<OrganizationRequestDto>` or generic pagination
   - Files: `AdminOrganizationApprovalServlet.java`
   - Savings: ~40 lines

2. **OrganizationDecisionResultDto** (wrap approval response)  
   - Inline into `OrganizationRequestDetailDto` as nullable fields
   - Files: `AdminOrganizationApprovalServlet.java`
   - Savings: ~60 lines

3. **OtpVerifyResponseDto** (wrap token)
   - Merge fields into existing response DTO
   - Savings: ~20 lines

4. **EventCancelResponseDto** (wrap status)
   - Consolidate into main response
   - Savings: ~30 lines

### Phase 2: Consolidate Summary/Detail Pairs (~600 lines)
Merge 3+ Summary+Detail DTO pairs into single unified DTOs:

1. **RefundSummaryDto + RefundDetailDto → RefundDto**
   - Current: 59 + 59 = 118 lines
   - Consolidated: ~40 lines
   - Savings: ~78 lines

2. **AdminRefundSummaryDto + AdminRefundDetailDto → AdminRefundDto**
   - Current: 54 + 62 = 116 lines
   - Consolidated: ~45 lines
   - Savings: ~71 lines

3. **MembershipSummaryDto** (extract fields only)
   - Can be simplified or merged with User DTO
   - Savings: ~30 lines

4. **TicketSummaryDto + TicketDetailDto** (already done)
   - Status: ✅ Consolidated to TicketDto
   - Savings: ~121 lines

### Phase 3: Remove Redundant Exception Classes (~200 lines)
Consolidate scattered exception classes into feature-based error tanks:

Current: 14+ exception files
Target: 7 consolidated error packages (one per feature):
- `identity/error/` ✅ (already has pattern)
- `order/error/` (consolidate OrderException)
- `payment/error/` (consolidate PaymentException)
- `event/error/` (consolidate EventException)
- `ticketing/error/` (consolidate HoldException)
- `fulfillment/error/` (consolidate all refund/ticket/checkin exceptions)
- `settlement/error/` (consolidate finance/commission exceptions)

Savings: ~50 lines per consolidated error tank × 7 = **~350 lines**

### Phase 4: Create Generic Page<T> Utility (~100 lines)
Remove *PageDto classes by using generic pagination:

```java
public record Page<T>(
    List<T> data,
    int total,
    int page,
    int size) {}
```

Replace:
- `OrganizationRequestPageDto`
- `RefundPageDto`  
- `AdminRefundPageDto`
- Other list responses

Savings: ~100 lines

---

## 📊 Expected Final Reductions

| Component | Lines Before | Lines After | Savings |
|-----------|--------------|-------------|---------|
| Week 1-2 Completed | - | - | **~2,000** |
| Phase 1: Remove Wrapper DTOs | - | - | ~500 |
| Phase 2: Consolidate Summary/Detail | - | - | ~600 |
| Phase 3: Exception Consolidation | - | - | ~350 |
| Phase 4: Generic Page<T> | - | - | ~100 |
| **TOTAL** | 26,422 | **~23,000** | **~3,500** |

**Final Target:** 23,000 lines (-13% from 26,422)

---

## 🔄 Next Steps

1. **Week 3 Execution:**
   - Test: Compile after each phase
   - Commit: After each phase with savings recorded
   - Update: Servlets/services that use removed DTOs

2. **After Refactoring:**
   - Run full test suite: `mvn -B test`
   - Run integration tests: `mvn -B -Psqlserver-it verify`
   - Update SPEC.md with new code organization

3. **Video Recording:**
   - Record walkthrough of simplified architecture
   - Show before/after code examples
   - Explain MVC flow with new BaseApiServlet pattern

---

## 📚 Architecture Goals (From Original Review)

✅ Clean servlet layer with BaseApiServlet
✅ Universal DTO serialization with DtoSerializer
⏳ Consolidated exception handling (Phase 3)
⏳ Unified response wrapper (Page<T> in Phase 4)
⏳ Video walkthrough for newbies

---

## Notes

- All changes maintain backward compatibility at API level
- Database schema and stored procedures unchanged
- Focus on code readability, not feature changes
- Each phase tested before next begins
