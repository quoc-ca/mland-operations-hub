## 2. Performance & Scalability Design

Normal reads and writes return a result or an actionable failure without waiting for email delivery, AI processing, cleanup, or provider reconciliation. Long-running work reports a pending or failed state when needed.

Creating a Staff or Manager commits the pending Account and invitation request without waiting for SMTP. Admin sees queued, sent, failed or uncertain delivery separately. A possible provider acceptance is not retried automatically; two definite invitation failures permit controlled Admin recovery while the link is valid.

Booking and checkout recheck capacity, stock, and benefit limits before acceptance. Conflicting edits and unavailable dependencies stop the affected action. Repeated payment evidence and expiry jobs must not duplicate results or release confirmed resources.

Expiry follows stored deadlines. Peak users, request rate, response-time targets, and production capacity remain to be measured and agreed before release.
