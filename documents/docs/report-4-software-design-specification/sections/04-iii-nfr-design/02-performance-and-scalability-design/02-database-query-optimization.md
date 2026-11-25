### 2.2 Database Query Optimization

#### Observed implementation

| Evidence | Existing mechanism | Scope of conclusion |
|---|---|---|
| `ppswbs_backend/src/main/resources/application.properties` | MySQL, Flyway migrations, schema validation by default and `spring.jpa.open-in-view=false`. | Schema changes are migration-managed; DTO mapping must load necessary data within the service transaction. |
| `ReviewRepository.java` under `features/reviewmanagement/dao` | A paginated target/status lookup, aggregate projections for count and average, and collection-based source-reference lookup. | These methods support bounded reads and aggregation; runtime execution plans remain unverified. |
| `V102__review_package_context.sql` | Index `idx_reviews_package_status_created` on `(package_id, status, created_at)`. | An index exists for this column sequence; usefulness depends on actual predicates, ordering and data distribution. |
| `V109__catalogue_variant_branch_stock.sql` | Unique key on `(variant_id, location_id)`. | Enforces one stock record per variant/location and supports matching lookups. |

#### Proposed optimization procedure

1. Capture slow statements and request-level SQL counts using representative, non-sensitive datasets. Prioritize frequent or expensive queries and record parameter shapes, row counts and baseline latency.
2. Inspect execution plans for selective and broad filters, sorting, count queries and deep pages. Review rows examined, join order, temporary work and index use. Run execution-based analysis only in an appropriate controlled environment.
3. Add or adjust composite indexes to match measured equality predicates, ranges and ordering. Check existing indexes first and account for write overhead and storage. Introduce changes through a new Flyway migration; do not rewrite applied migrations.
4. Detect N+1 reads by comparing statement counts as result size grows. Use DTO projections, explicit fetch plans or batched relationship reads where appropriate. Avoid collection fetch joins that undermine database pagination.
5. Enforce a maximum page size and deterministic ordering with a unique tie-breaker. Consider cursor pagination for measured deep-page bottlenecks. Avoid loading complete datasets to compute counts.
6. Keep write transactions short and use consistent lock order. Preserve uniqueness, stock/seat checks, payment idempotency and audit logging while reducing lock duration. Measure contention before changing transaction behavior.
7. Compare before/after latency, SQL counts, rows examined and resource usage with the same workload. Include concurrent writes and validate result correctness before retaining an optimization.

#### Specific review points

`ReviewRepository.getAggregateByOptionalTarget` combines nullable optional predicates. Its plan should be checked for both filtered and unfiltered calls; alternative query shapes should be introduced only if measurements justify them. The existing package/status/date index does not prove that every optional-filter query is optimized.

The connection initialization sets `sort_buffer_size=16777216`. This setting is not proof of faster queries and requires a memory review under concurrent sorting workloads. Connection-pool limits and total database connection capacity must be evaluated together before increasing replica count.

**Verification status:** repository mechanisms have been inspected and Section 2 records a preliminary 180-request public API probe. At three workers the product-list p95 was 59.240 ms on an API-visible catalogue of 25 products. This end-to-end result does not isolate SQL execution time or demonstrate index effectiveness. No database execution plans or production telemetry were collected, and no query/index optimization was applied as part of this measurement. Performance acceptance remains pending the workload and targets described in Section 2.
