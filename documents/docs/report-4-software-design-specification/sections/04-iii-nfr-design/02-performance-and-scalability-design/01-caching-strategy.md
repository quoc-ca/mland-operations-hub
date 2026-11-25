### 2.1 Caching Strategy

Cache only reads whose age does not decide a business action. Manager navigation badge counts are cached for 30 seconds and refreshed every 60 seconds while the page is visible. A failed count is omitted; the underlying list remains authoritative.

The Manager dashboard reloads data when the page opens, a filter is applied, or the Manager reloads the page. It shows the last successful update time and has no timed refresh.

Do not use cached Account role, status, or Workshop Location scope to authorize a request. Sensitive actions check current facts. Catalogue, price, stock, and capacity data have no shared cache duration; the system checks current values before accepting a business action.

