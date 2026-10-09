# VUONG-02 Commission Rule Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây DTO và policy hoa hồng được kiểm thử độc lập cho phần đầu của #29.

**Architecture:** Immutable records kiểm tra điều khoản và biểu diễn projection. Một policy thuần dùng UserPrincipal hiện có để kiểm ADMIN và chặn edit; lỗi nghiệp vụ có mã ổn định. Adapter DB/HTTP thuộc giai đoạn tích hợp đã tách trong spec.

**Tech Stack:** JDK 25, Maven, JUnit Jupiter 5.12.0 hiện có; BigDecimal, Instant, UUID; không thêm dependency.

**Spec:** [2026-10-09-vuong-02-commission-rules-design.md](../specs/2026-10-09-vuong-02-commission-rules-design.md), được người dùng duyệt ngày 09/10/2026.

## Global Constraints

- Nhánh `feat/vuong/vuong-02-commission-rules` từ `dev` tại `cfd77c6`; bảo toàn nhánh/commit của #28.
- Mã nguồn nằm trong `src/main/java/vn/ticketscenter/settlement/commission/**`; test nằm trong package tương ứng dưới `src/test/java`.
- Tỷ lệ: `BigDecimal`, 0 đến 100 inclusive, chuẩn hóa scale 4 bằng phép chuyển chính xác.
- Phí cố định: `long`, từ 0 đến Long.MAX_VALUE, đơn vị đồng.
- Hiệu lực: `effectiveFrom <= now < effectiveTo`; thời gian dùng Instant, không dùng timezone máy.
- Exception không phụ thuộc Servlet API; lỗi validation dùng `COMMISSION_RULE_INVALID`.
- Giữ model, UserPrincipal, pom, persistence, database và package của các owner khác.
- Giai đoạn hiện tại giữ #29 mở; unit test không thay integration/transaction/HTTP nghiệm thu.

## Review Focus

1. Phần lẻ tỷ lệ bị mất khi làm tròn: `5.00001` phải bị từ chối; Task 1 kiểm mã lỗi và `5.00000` chuẩn hóa chính xác.
2. BigDecimal có scale bất thường: số 0 với scale Integer.MIN_VALUE vẫn hợp lệ, `1E+10000` bị chặn theo miền trước khi chuẩn hóa; Task 1 kiểm cả hai.
3. Count vượt int: Long.MAX_VALUE vẫn chặn edit và giữ nguyên trong projection; Task 2/3 kiểm không ép kiểu nhỏ hơn.
4. Caller thiếu quyền đi cùng count lỗi: kiểm quyền trước để không lộ thông tin trạng thái; Task 2 kiểm count -1, 0, 1.
5. Ranh giới Instant tới nano giây: đúng start được áp dụng, đúng end không áp dụng, không cắt mất precision; Task 3 dùng khoảng dài 1ns.

## File Structure

| File mới dưới `src/main/java/vn/ticketscenter/settlement/commission/` | Trách nhiệm |
|---|---|
| `CommissionRuleException.java` | Mã lỗi miền và thông điệp ngắn |
| `dto/CommissionRuleCreateDto.java` | Điều khoản đầu vào đã kiểm tra/chuẩn hóa |
| `service/CommissionRulePolicy.java` | Guard ADMIN và edit |
| `dto/CommissionRuleDto.java` | Projection giá trị và kiểm hiệu lực |

Ba test class tương ứng dưới `src/test/java/vn/ticketscenter/settlement/commission/{dto,service}`. Hồ sơ kết quả tại `docs/evidence/vuong/VUONG-02.md`.

## Chuẩn bị môi trường thực thi

- [ ] Dùng skill using-git-worktrees để chuẩn bị checkout cách ly khi thực thi; các test/target đã build của #28 không được đi vào classpath của #29. Giữ nguyên preview/checkout đang phục vụ #28.
- [ ] Chọn JDK 25 trong terminal hiện tại. Kiểm working tree và đường dẫn repo trước lệnh clean; build output phải thuộc checkout đang thực thi.

```powershell
$env:JAVA_HOME = 'C:\Users\Le Vuong\.jdks\openjdk-25'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

### Task 1: Điều khoản đã kiểm tra và lỗi miền

**Files:** Create `src/main/java/vn/ticketscenter/settlement/commission/CommissionRuleException.java`; Create `src/main/java/vn/ticketscenter/settlement/commission/dto/CommissionRuleCreateDto.java`; Test `src/test/java/vn/ticketscenter/settlement/commission/dto/CommissionRuleCreateDtoTest.java`.

**Interfaces:** Consumes BigDecimal/Instant. Produces `CommissionRuleException(String code, String message)` extends RuntimeException, `String code()`; record `CommissionRuleCreateDto(BigDecimal ratePercent, long fixedFee, Instant effectiveFrom, Instant effectiveTo)` với accessors chuẩn record.

- [ ] **Step 1: Viết test đỏ.** Fixture from=`2026-10-01T00:00:00Z`, to=`2027-10-01T00:00:00Z`. Test `acceptsAndNormalizesValidTerms`: rate 0, 5.00000, 100; fee 0, 10000, Long.MAX_VALUE. Assert scale 4 và giữ nguyên fee/Instant. Test `rejectsInvalidTerms`: rate null/-0.0001/100.0001/5.00001/1E+10000; fee -1; from/to null, bằng nhau, đảo thứ tự. Mỗi ca assert code COMMISSION_RULE_INVALID. Test `normalizesZeroWithExtremeScale` kiểm zero được chuẩn hóa mà không tràn phép tính scale. Exception message không chứa giá trị đầu vào.

```java
assertEquals(new BigDecimal("5.0000"), new CommissionRuleCreateDto(new BigDecimal("5.00000"), 10000L, from, to).ratePercent());
assertEquals("COMMISSION_RULE_INVALID", assertThrows(CommissionRuleException.class,
    () -> new CommissionRuleCreateDto(new BigDecimal("5.00001"), 0L, from, to)).code());
assertEquals(new BigDecimal("0.0000"), new CommissionRuleCreateDto(new BigDecimal(BigInteger.ZERO, Integer.MIN_VALUE), 0L, from, to).ratePercent());
```

- [ ] **Step 2: Chạy** `mvn -B '-Dtest=CommissionRuleCreateDtoTest' test`; xác nhận đỏ vì chưa có các lớp cần kiểm thử, ghi đúng nguyên nhân.
- [ ] **Step 3: Triển khai interfaces đã chốt.** Constructor DTO kiểm null/range/thời gian/fee, chuẩn hóa tỷ lệ chính xác với RoundingMode.UNNECESSARY sau kiểm miền; xử lý zero riêng để scale bất thường không gây overflow. Các ca invalid ném CommissionRuleException với mã đã chốt và message không echo payload.
- [ ] **Step 4: Chạy lại** cùng lệnh; yêu cầu 0 failures/errors và không skipped trong class này.
- [ ] **Step 5: Commit** đúng ba file: `feat(commission): validate commission rule terms`.

### Task 2: Policy quyền ADMIN và chặn sửa

**Files:** Create `src/main/java/vn/ticketscenter/settlement/commission/service/CommissionRulePolicy.java`; Test `src/test/java/vn/ticketscenter/settlement/commission/service/CommissionRulePolicyTest.java`.

**Interfaces:** Consumes CommissionRuleException/code() từ Task 1, UserPrincipal(UUID actorId, PlatformRole platformRole, int authVersion)/isAdmin() hiện có. Produces `public static void requireAdmin(UserPrincipal actor)` và `public static void requireEditable(UserPrincipal actor, long associatedEventsCount)`.

- [ ] **Step 1: Viết test đỏ.** Fixture ADMIN/CUSTOMER có actorId UUID cố định và authVersion=1. `requiresPlatformAdmin`: ADMIN pass, CUSTOMER ADMIN_REQUIRED, null AUTHENTICATION_REQUIRED. `allowsOnlyUnattachedRules`: count 0 pass, count 1/5/Long.MAX_VALUE RULE_ALREADY_ATTACHED_TO_EVENTS, ADMIN/count -1 IllegalStateException. `checksAuthorizationBeforeState`: CUSTOMER và null cùng count -1/0/1 luôn nhận mã quyền tương ứng.

```java
assertDoesNotThrow(() -> CommissionRulePolicy.requireEditable(admin, 0L));
assertEquals("RULE_ALREADY_ATTACHED_TO_EVENTS", assertThrows(CommissionRuleException.class,
    () -> CommissionRulePolicy.requireEditable(admin, Long.MAX_VALUE)).code());
assertEquals("ADMIN_REQUIRED", assertThrows(CommissionRuleException.class,
    () -> CommissionRulePolicy.requireEditable(customer, -1L)).code());
```

- [ ] **Step 2: Chạy** `mvn -B '-Dtest=CommissionRuleCreateDtoTest,CommissionRulePolicyTest' test`; xác nhận đỏ đúng phần policy chưa tồn tại.
- [ ] **Step 3: Triển khai** hai static guard trong file đã chỉ định. requireEditable gọi requireAdmin trước, count âm là IllegalStateException, count >0 là lỗi rule đã áp dụng. Message ngắn cố định; không nhận quyền/count từ HTTP hoặc truy vấn DB ở policy.
- [ ] **Step 4: Chạy lại** cùng lệnh; cả hai class xanh, không skipped.
- [ ] **Step 5: Commit** đúng hai file: `feat(commission): enforce admin and applied rule guards`.

### Task 3: Projection và hiệu lực, kiểm chứng bàn giao

**Files:** Create `src/main/java/vn/ticketscenter/settlement/commission/dto/CommissionRuleDto.java`; Test `src/test/java/vn/ticketscenter/settlement/commission/dto/CommissionRuleDtoTest.java`; Create `docs/evidence/vuong/VUONG-02.md`.

**Interfaces:** Consumes constructor/accessors CommissionRuleCreateDto và CommissionRuleException/code() từ Task 1. Produces record `CommissionRuleDto(UUID ruleId, UUID organizationId, BigDecimal ratePercent, long fixedFee, Instant effectiveFrom, Instant effectiveTo, long associatedEventsCount)` và `public boolean isEffectiveAt(Instant now)`.

- [ ] **Step 1: Viết test đỏ.** Fixture UUID rule/org cố định. `preservesValidatedProjection` kiểm rate chuẩn hóa, ID, fee, Instants và count Long.MAX_VALUE. `rejectsInvalidProjection` kiểm mỗi ID null, count -1 và toàn bộ nhóm điều khoản invalid của Task 1 bằng dữ liệu tham số dùng trong test. `usesHalfOpenValidityInterval` kiểm trước start, đúng start, giữa, đúng end, sau end; now null ném NullPointerException. `preservesNanosecondBoundaries` dùng from ở `2026-10-01T00:00:00Z`, to=from.plusNanos(1).

```java
assertFalse(rule.isEffectiveAt(from.minusNanos(1)));
assertTrue(rule.isEffectiveAt(from));
assertFalse(rule.isEffectiveAt(to));
assertThrows(NullPointerException.class, () -> rule.isEffectiveAt(null));
```

- [ ] **Step 2: Chạy** `mvn -B '-Dtest=CommissionRuleCreateDtoTest,CommissionRulePolicyTest,CommissionRuleDtoTest' test`; xác nhận đỏ đúng projection chưa tồn tại.
- [ ] **Step 3: Triển khai** record và method đã chỉ định. Tái dùng CommissionRuleCreateDto để kiểm/chuẩn hóa điều khoản, kiểm ID/count, chỉ chứa giá trị; isEffectiveAt so sánh Instant trực tiếp theo [from,to). ID/count invalid dùng COMMISSION_RULE_INVALID; now null dùng Objects.requireNonNull.
- [ ] **Step 4: Chạy lại** cùng lệnh; cả ba class xanh, không skipped. Sau đó chạy nhóm regression an toàn dưới đây và package; kết quả phải được đọc trước khi ghi evidence.

```powershell
mvn -B '-Dtest=CommissionRuleCreateDtoTest,CommissionRulePolicyTest,CommissionRuleDtoTest,FeaturePackageStructureTest,AuthOtpServletsTest,IdentityWebFlowTest,UserPrincipalTest,SecurityContextTest,OtpCryptoHelperTest,AuthServiceTest#testRegisterInvalidEmail+testRegisterPasswordTooShort+testLoginNullOrBlankInputs+testCurrentUserDtoSanitization,EmailSenderTest#shouldValidateEmailMessageFields+fakeEmailSenderShouldRecordMessages+fakeEmailSenderShouldThrowWhenFailureSimulated' test
mvn -B -DskipTests package
git diff --check
```

- [ ] **Step 5: Review/bàn giao.** Dùng requesting-code-review cho một review độc lập của toàn nhánh khi Native được chọn; sửa lỗi quan trọng và chạy lại nhóm test bị ảnh hưởng. Evidence ghi kết quả thật, tên test/lệnh, red-green, commit/baseline và các điều kiện DB/HTTP còn thiếu; full mvn test có ca gửi SMTP thật/DB chung nên không được báo xanh từ nhóm test đã chọn.
- [ ] **Step 6: Commit** DTO/test/evidence: `feat(commission): add validated rule projection and evidence`. Giữ feature branch để review; #29 vẫn mở, không push/merge hoặc sửa các feature khác trong kế hoạch này.

## Self-review

Ba task bao phủ các thành phần và unit cases trong mục 2–4 của spec. Mục 5 là điều kiện tích hợp ở giai đoạn sau, được giữ nguyên trong evidence. Interfaces của Task 2/3 chỉ dùng kiểu đã định nghĩa ở Task 1 hoặc kiểu identity có sẵn; năm Review Focus đều có ca test được chỉ định. Plan không thêm endpoint, adapter fake persistence hoặc dependency.

## Execution Handoff

Đề xuất **Native**: implementer thực hiện ba task theo thứ tự trong phiên này, mỗi task có test đỏ/xanh và commit, rồi một reviewer độc lập kiểm toàn nhánh. Các interface nhỏ và Task 2/3 cùng dùng DTO/lỗi của Task 1 nên cách này giảm việc truyền ngữ cảnh. Người dùng cần duyệt kế hoạch và chọn phương thức thực thi trước khi bắt đầu mã nguồn.
