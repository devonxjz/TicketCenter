# Code Optimization Plan - Reduce Lines & Improve Quality

**Current State:** 26,422 Java + 22,685 JS = **49,107 total lines**

**Target:** < 42,000 lines (-15% reduction) with better quality & maintainability

---

## 🔴 CRITICAL ISSUES (Code-Review findings pending)

Waiting for `/code-review max` results to identify:
- Bug patterns
- Security issues
- Logic errors
- Dead code
- Performance bottlenecks

---

## 📋 OPTIMIZATION OPPORTUNITIES (Pre-Analysis)

### **TIER 1: High Impact (500+ lines each)**

#### 1. Redis Cache Consolidation
**Current:** 24 cache files with duplicate logic
```java
// Current pattern repeated 24x:
public final class Redis{X}Cache {
    private final Supplier<Jedis> connections;
    public Optional<T> get(UUID id) {
        try (Jedis redis = connections.get()) {
            String value = redis.get(key(id));
            if (value == null) return Optional.empty();
            return Optional.of(decode(value));
        } catch (Exception ignored) { return Optional.empty(); }
    }
    public void put(UUID id, T value) {
        try (Jedis redis = connections.get()) {
            redis.setex(key(id), TTL, encode(value));
        } catch (Exception ignored) {}
    }
}
```

**Solution: Generic RedisCache<T>**
```java
public class RedisCache<T> {
    private final Supplier<Jedis> connections;
    private final Function<UUID, String> keyFn;
    private final Function<String, T> decodeFn;
    private final Function<T, String> encodeFn;
    private final int ttlSeconds;
    
    public Optional<T> get(UUID id) { /* generic impl */ }
    public void put(UUID id, T value) { /* generic impl */ }
}

// Usage: New RedisCache<>(connections, ::key, ::decode, ::encode, TTL)
```

**Savings:** 20-30 lines/cache × 24 caches = **480-720 lines** ✂️

---

#### 2. Exception Consolidation
**Current:** 15 Exception files, scattered patterns
- `AppException` base ✓
- `OrderException` inherits ✓
- 13 others: mixed patterns

**Solution: Unified Error Hierarchy**
```
config/error/
  ├── AppException.java (base) ✓
  └── ErrorCode.java (enum - single source of truth)
    
feature/error/
  ├── {Feature}Exception.java (inherits AppException)
  └── {Feature}ErrorCode.java (enum with HTTP status)
```

**New Files to Create:**
- `identity/error/IdentityErrorCode.java`
- `order/error/OrderErrorCode.java`
- `payment/error/PaymentErrorCode.java`
- `event/error/EventErrorCode.java`
- `ticketing/error/TicketingErrorCode.java`
- `fulfillment/error/FulfillmentErrorCode.java`
- `settlement/error/SettlementErrorCode.java`

**Files to Consolidate/Remove:**
- Merge scattered exceptions (PaymentException, HoldException, etc.)
- Remove duplicate error message definitions
- **Savings:** ~200-300 lines

---

#### 3. Config Simplification
**Current:** AppConfig (278L), DatabaseConfig (118L), RedisClientProvider (104L), SqlCall (305L)
- Total: ~2,853 lines in /config

**Issues:**
- SqlCall has too much logic (305 lines)
- JsonObjectParser (152L) duplicates DtoSerializer (94L)
- DatabaseConfig can be inline into AppConfig

**Solution:**
1. Extract SqlCall query logic into separate module
2. Remove JsonObjectParser (use DtoSerializer)
3. Inline DatabaseConfig into AppConfig
4. **Savings:** ~250-350 lines

---

### **TIER 2: Medium Impact (100-300 lines each)**

#### 4. DTO Consolidation (Already planned)
- Remove wrapper DTOs (PageDto, ResultDto, ResponseDto) 
- Consolidate Summary+Detail pairs
- Use DtoSerializer universally
- **Savings:** ~600-800 lines

#### 5. Servlet Boilerplate Reduction
**Current:** 60+ servlets with repeated patterns
- Already have `BaseApiServlet` (126L)
- Apply to all servlets
- **Savings:** ~30 lines/servlet × 60 = **1,800 lines**

#### 6. Test Code Optimization
- Consolidate test fixtures
- Remove duplicate test setup
- **Savings:** ~300-500 lines

---

### **TIER 3: Low Impact (< 100 lines each)**

#### 7. Constants Extraction
- Move magic strings to feature-level Constants
- **Savings:** ~50-100 lines

#### 8. Validator Consolidation
- Merge duplicate validation logic
- **Savings:** ~50-100 lines

#### 9. Unused Code Removal
- Dead code from refactorings
- Orphan imports
- **Savings:** ~50-150 lines

---

## 📊 ESTIMATED TOTAL SAVINGS

| Tier | Component | Savings | Status |
|------|-----------|---------|--------|
| 1 | Redis Consolidation | 500-720 | 📋 Planned |
| 1 | Exception Unification | 200-300 | 📋 Planned |
| 1 | Config Simplification | 250-350 | 📋 Planned |
| 2 | DTO Consolidation | 600-800 | 📋 Planned |
| 2 | Servlet Reduction | 1,800 | ✅ BaseApiServlet done |
| 2 | Test Optimization | 300-500 | 📋 Planned |
| 3 | Constants, Validators, Dead Code | 150-350 | 📋 Planned |
| **TOTAL JAVA** | | **~4,000-5,500** | |
| **JS Review** | Logic sequencing, consolidation | TBD | ⏳ Pending review |

---

## 🛠️ EXECUTION ROADMAP

### **Phase 1: Foundation (2-3 days)**
1. ✅ Create `RedisCache<T>` generic
2. Consolidate 24 Redis implementations
3. Create `ErrorCode` enums per feature
4. Consolidate Exception files

**Deliverable:** -500-600 lines, cleaner error handling, Redis pool reuse

### **Phase 2: Config & DTOs (2-3 days)**
1. Consolidate AppConfig/DatabaseConfig
2. Remove JsonObjectParser duplication
3. Consolidate DTO pairs
4. Remove wrapper DTOs

**Deliverable:** -600-700 lines, single config entry point, 46 → 30 DTO files

### **Phase 3: Servlet & Tests (2-3 days)**
1. Apply BaseApiServlet to all 60 servlets
2. Consolidate test fixtures
3. Extract constants

**Deliverable:** -1,800 lines, consistent servlet pattern, tests reusable

### **Phase 4: Code Review + JS (2-3 days)**
1. Apply /code-review findings
2. Fix bugs, security issues
3. Review JS logic sequencing
4. Remove dead code

**Deliverable:** Final optimization, all issues resolved

---

## ✅ QUALITY GATES

After each phase, verify:
- ✓ `mvn -B clean compile` succeeds
- ✓ `mvn -B test` all pass (unit tests)
- ✓ `mvn -B -Psqlserver-it verify` succeeds (integration tests)
- ✓ Code review passes
- ✓ Git commits with clear messages

---

## 📝 NEXT STEPS

**Waiting for:** `/code-review max` to complete

**Then:** Execute Phase 1 immediately

**Goal:** Target 23,000 Java lines by end of optimization

---

## 📚 Related Documentation

- `docs/REFACTORING_PLAN.md` - 3-week plan (Weeks 1-2 done)
- `docs/slides/architecture-review.html` - Architecture analysis
- `docs/references/SPEC.md` - Business logic (won't change)
- `docs/backend/database-runbook.md` - DB operations (won't change)
