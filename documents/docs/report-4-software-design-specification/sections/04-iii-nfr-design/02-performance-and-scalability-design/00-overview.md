## 2. Performance & Scalability Design

This section covers response time, resource usage and growth of the Personalized Product Sales and Workshop Booking System. **Observed** describes repository evidence; **Proposed design** describes measures that still require implementation or runtime verification. Source review does not establish production capacity.

#### Current implementation

The backend uses Spring Data JPA with MySQL. `ppswbs_backend/src/main/resources/application.properties` enables Flyway, defaults Hibernate schema handling to validation and disables Open Session in View. Repository methods use pagination for several lists; this is not evidence that every endpoint enforces a maximum page size. The production Compose override defines backend and frontend services but does not establish automatic scaling or a measured replica capacity.

#### Proposed design

| Area | Design and rationale | Verification required |
|---|---|---|
| Read requests | Bound page sizes and return only fields needed by the screen. Use database aggregates for counts rather than loading complete lists. | Record SQL count, response size and latency for representative filters and page depths. |
| Concurrent writes | Preserve transaction boundaries, uniqueness and idempotency when processing booking, stock and payment changes. Performance changes must preserve operational audit records. | Concurrent requests must not oversell, duplicate payments or lose audit events. |
| Connection capacity | Budget database connections across all application instances and background workers. Set pool sizes from measured demand and database limits. | Monitor active connections, acquisition wait, timeouts and database CPU. |
| Horizontal growth | Before adding replicas, inspect session state, local caches, scheduled jobs, file storage and callback processing for shared-state or duplicate-execution risks. | Exercise concurrent replicas and verify job coordination and business invariants. |
| External services | Use bounded timeouts and explicit failure handling; retry only operations whose duplicate effects are controlled. | Record timeout and recovery behavior under dependency failures. |

#### Performance acceptance evidence

The verification workload should include catalogue browsing, review lists, staff queues, checkout and workshop booking, with realistic data volumes and concurrent reads/writes. Capture p50/p95/p99 latency, throughput, error rate, SQL duration, connection wait, CPU and memory; include a steady-load run, a burst and recovery. Record environment, dataset size and request mix so results are reproducible.

#### Observed preliminary API measurement

A bounded read-only probe ran on 25 November 2026 at 16:20 (UTC+07:00) against the existing service at `http://localhost:8080`, from the same Windows workstation. The inspected source HEAD was `821754dff032a448a13c19d48d83c8b732299399`; the running service's build identity and database host/resources were not independently verified. The workload used Python 3.14 standard-library HTTP requests, a new connection per request, a 10-second timeout and 100 ms think time per worker. Three warm-up requests per endpoint were excluded from the measured samples. Endpoints and concurrency levels ran in separate sequential phases; concurrency was not combined across endpoints.

| Endpoint | Workers | Requests | Errors | p50 (ms) | p95 (ms) | Maximum (ms) | Observed requests/s |
|---|---|---|---|---|---|---|---|
| GET /api/v1/products | 1 | 30 | 0 | 48.634 | 56.781 | 56.843 | 6.67 |
| GET /api/v1/product-categories | 1 | 30 | 0 | 23.881 | 29.515 | 29.731 | 8.34 |
| GET /api/v1/product-selling-locations | 1 | 30 | 0 | 23.996 | 28.271 | 28.471 | 8.32 |
| GET /api/v1/products | 3 | 30 | 0 | 49.311 | 59.240 | 63.779 | 20.03 |
| GET /api/v1/product-categories | 3 | 30 | 0 | 23.572 | 29.993 | 31.226 | 24.48 |
| GET /api/v1/product-selling-locations | 3 | 30 | 0 | 23.482 | 27.643 | 27.911 | 24.54 |

All 180 measured requests returned HTTP 200 with a JSON object containing an `items` array. This validates response status and basic shape, not full business correctness. Percentiles use nearest rank; latency includes receiving and parsing the response. Throughput includes worker think time and is not maximum server throughput. Each phase lasted approximately 1.2–4.5 seconds, so tail estimates are preliminary; p99 is not reported for these small samples.

The default product page returned 20 items out of 25 reported total (52,699 response bytes); categories returned 5 of 5 (912 bytes), and selling locations returned 2 items (285 bytes). These are API-visible counts, not database table sizes. No data was seeded or changed by the probe. Full response bodies were not retained.

Reproducible evidence is stored at repository paths `artifacts/performance/catalogue_smoke.py` and `artifacts/performance/catalogue_smoke_result.json`. Run the script from the repository root with Python while the intended local backend is available; a new run replaces the result file. The JSON preserves timings and response metadata for each request.

**Limitations:** this measures a small public catalogue workload over loopback. It does not establish production latency, sustained capacity, authenticated workflows, concurrent booking/payment correctness, database execution plans, cache effectiveness or resource saturation. No CPU, memory, SQL timing or pool-wait telemetry was collected. Formal performance acceptance remains pending agreed targets and representative sustained-load tests.

**TBD — Mland decision required:** expected concurrent users, peak transaction rate, data retention and growth, endpoint latency objectives, acceptable error rate and hosting budget. No capacity figure or performance pass is claimed until those targets are agreed and measured. Sections 2.1 and 2.2 define the caching and database measures supporting this design.
