## 2. Performance & Scalability Design

Normal reads and writes return a result or an actionable failure without waiting for long-running email, AI, cleanup, or provider reconciliation work. Such work uses a visible pending or failure state where needed. Booking, payment, and retail expiry jobs must be safe to repeat and must not release a confirmed resource.

Concurrent booking and checkout requests recheck current capacity, stock, and benefit limits before acceptance. A stale edit or unavailable dependency stops the affected action instead of accepting an uncertain result.

Hold and payment deadlines use stored timestamps and repeatable expiry jobs. Peak users, request rate, page latency, and production infrastructure size need measured targets before release.
