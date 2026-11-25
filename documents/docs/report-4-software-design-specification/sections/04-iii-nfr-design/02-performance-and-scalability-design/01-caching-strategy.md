### 2.1 Caching Strategy

#### Observed implementation

| Component | Current behavior | Design implication |
|---|---|---|
| Shared HTTP client | `ppswbs_frontend/src/shared/api/http.js` explicitly does not cache responses. | Do not describe a general API response cache as implemented. |
| Navigation badges | `ppswbs_frontend/src/shared/nav/useNavBadges.js` keeps an in-memory result associated with a user ID; freshness is 30 seconds and periodic refresh is 60 seconds. Cleanup is registered for private state. Failed count reads omit affected badges. | Counts are advisory and may lag business changes; a missing count must not be shown as zero. |
| Role lookup | `ppswbs_frontend/src/shared/auth/useRole.js` remembers a role by user ID and clears the previous account's cache on account changes. | Cached roles support presentation only; permission-sensitive operations must be checked by the server. |

The reviewed backend application configuration does not establish a shared Redis cache. Distributed caching is therefore a proposed option, not an existing system guarantee.

#### Proposed cache policy

| Data | Policy | Invalidation and consistency |
|---|---|---|
| Public catalogue descriptions and reference data | Eligible for bounded caching after profiling demonstrates repeated expensive reads. | Evict affected entries after a successful update transaction. Choose TTL from agreed freshness needs; no TTL is approved here. |
| Availability, stock, prices at checkout, payment state and booking capacity | Read authoritative state when validating or committing a transaction. | Never authorize a sale, payment or reservation from stale display data. |
| Account-specific lists and operational counts | Partition by authenticated identity and relevant role/branch scope. | Clear on logout, session loss or scope changes; refresh after relevant successful mutations. |
| Static versioned assets | Eligible for browser caching when deployment uses content-versioned filenames. | New releases change asset URLs; verify actual response headers before claiming this behavior. |

For a future server cache, use keys containing resource type, identifier and all applicable filters, page, sort, locale and access scope. Bound entry count and lifetime. Populate on a cache miss from the database; invalidate only after commit. Coordinate invalidation across replicas, prevent concurrent misses from producing an uncontrolled database burst, and monitor hit rate, eviction, memory and stale reads. A cache outage may fall back to database reads only within a bounded load budget.

#### Required verification

Check hits, misses and expiry; account switching; logout; permission and branch changes; update followed by read; failed updates; cache unavailability; and simultaneous requests. Verify that cached data cannot cross account boundaries and transactional validation uses current state. These checks are acceptance requirements, not recorded passing results.

The preliminary API probe in Section 2 bypassed the browser and therefore did not exercise role or navigation-badge caches. Repeated API reads alone do not establish cache hit rate or a caching speedup. Source inspection also confirms that `PublicProductApiController.image` explicitly uses `CacheControl.noStore()` for product media; the proposed policy for versioned static assets must not be interpreted as existing product-media caching.

