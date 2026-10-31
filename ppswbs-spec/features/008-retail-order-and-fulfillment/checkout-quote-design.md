# FE-08 Durable Checkout Quote Design

**Date created**: 2026-10-30
**Date updated**: 2026-10-30
**Author**: Thinh
**Last updated by**: Thinh

## Scope and status

Thinh approved a source-owned checkout quote containing the Member, cart,
server-priced items, each branch's fulfilment and shipping, selected benefits,
displayed amounts, FE-06 terms digest and expiry, and selected V26 for the
quote schema. GHTK/VNPay are not implemented by this decision.

## Quote and acceptance

1. The Member selects benefits and one fulfilment choice for each cart
   location. GHTK requires a previously issued, Member/cart/recipient-bound
   shipping quote; pickup has no carrier charge.
2. FE-08 reads its own cart and FE-02 sale facts, checks each shipping quote,
   builds server-priced FE-06 `TrustedSourceContext`, and calls
   `previewBenefits`. Shipping is passed separately and is not a benefit base.
3. FE-08 persists the quote and its per-location and per-item snapshots in one
   local transaction. It returns its opaque ID and the totals actually shown
   to the Member. The quote expires after 15 minutes, or earlier if a bound
   shipping quote expires sooner. Preview itself creates no order, hold, payment or benefit
   reservation. Remote shipping quotation is completed before this transaction.
4. Acceptance requires the authenticated owner, quote ID, explicit terms
   acknowledgement and an idempotency key. FE-08 locks the cart and quote,
   verifies ownership, deadline, selection, locations, fulfilment and cart
   version, and obtains FE-02 locked sale facts. Under the FE-08 variant guards
   it checks aggregated stock, price and product facts against the quote.
5. FE-08 creates the grouped checkout, branch orders, item snapshots and
   inventory holds and invokes FE-06 `reserveAcceptedTransaction` using its
   stored quote ID and FE-06 `termsDigest`. These effects and the quote's
   accepted-checkout reference commit together or all roll back. Changed or
   expired terms require a new preview and acknowledgement.
6. A retry with the same Member and idempotency key returns the original
   accepted checkout only when the command matches; a mismatched command
   conflicts. A quote cannot be accepted twice by different command keys.

The lock sequence must follow the published FE-02 and FE-06 contracts: source
records, FE-02 category/product/variant locks in ascending order, FE-08 guards
in ascending variant order, then FE-06 locks. No provider network request runs
inside the guarded acceptance transaction. Acceptance uses READ COMMITTED so
a transaction waiting on a lock sees committed availability.

## Proposed physical records

Thinh approved reallocating FE-08 migrations after `develop` allocated V32–V37
to payment and workshop. FE-08 now uses V38–V43, with **V40** for quote storage.
Existing databases that applied earlier FE-08 versions require a separately
approved upgrade path; do not reset or repair their history automatically.
Existing
V14 demo checkouts are not converted into real accepted quotes.

### `retail_checkout_quotes`

| Column | Proposed type and constraint | Purpose |
| --- | --- | --- |
| `id` | `CHAR(36)` primary key | Opaque server-generated quote reference. |
| `member_account_id` | `BIGINT UNSIGNED NOT NULL`, FK `accounts(id)` | Owner; never supplied as trusted browser input. |
| `cart_id` | `CHAR(36) NOT NULL`, FK `retail_carts(id)` | Source cart identity. |
| `cart_version` | `BIGINT NOT NULL` | Detects subsequent cart edits. |
| `selection_choice` | `VARCHAR(16) NOT NULL` | `NONE`, `AUTO` or `VOUCHER`. |
| `voucher_code` | `VARCHAR(128) NULL` | Present only for `VOUCHER`. |
| `requested_points` | `DECIMAL(19,0) NOT NULL` | Non-negative points selected by Member. |
| `terms_digest` | `VARCHAR(128) NOT NULL` | Exact FE-06 digest produced by preview. |
| `currency` | `CHAR(3) NOT NULL` | `VND`. |
| `merchandise_subtotal`, `shipping_total`, `program_discount`, `points_discount`, `payable_total` | `DECIMAL(19,0) NOT NULL` | Server-computed and displayed whole-VND amounts. |
| `status` | `VARCHAR(16) NOT NULL` | `OPEN` or `ACCEPTED`; expiry is determined by the deadline. |
| `accepted_checkout_id` | `CHAR(36) NULL`, unique FK `retail_grouped_checkouts(id)` | Set atomically with acceptance; null for open quotes. |
| `created_at`, `expires_at` | `DATETIME(6) NOT NULL` | UTC creation and deadline; indexed by Member and expiry. |
| `version` | `BIGINT NOT NULL DEFAULT 0` | Optimistic version alongside pessimistic acceptance lock. |

Amounts and points must be non-negative; selection/code and status/reference
combinations are validated on the server and constrained where supported.
`payable_total` equals subtotal plus shipping minus the two discounts. Monetary
and point API values are decimal strings, not floating-point numbers.

### `retail_checkout_quote_branches`

Each row has a `CHAR(36)` primary key, `quote_id` foreign key with cascade
delete, `location_id BIGINT UNSIGNED`, `fulfilment_method VARCHAR(16)`, nullable
server-issued `shipping_quote_id VARCHAR(128)`, `shipping_charge DECIMAL(19,0)`
and `subtotal DECIMAL(19,0)`. `(quote_id, location_id)` is unique; method is
`PICKUP` or `GHTK`. Pickup has zero shipping and no shipping quote. GHTK
requires a valid source-owned shipping quote; recipient data remains in the
shipping/fulfilment owner and is not duplicated in the benefit quote. The
recipient binding and shipping snapshot require the separate GHTK contract and
schema gate before GHTK can be enabled.

### `retail_checkout_quote_items`

Each row has a `CHAR(36)` primary key, `quote_branch_id` foreign key with
cascade delete, `product_id BIGINT UNSIGNED`, `variant_id BIGINT UNSIGNED`,
`quantity TINYINT UNSIGNED` constrained to 1–99, `unit_price DECIMAL(19,0)`
and `line_total DECIMAL(19,0)`. `(quote_branch_id, variant_id)` is unique;
price and totals are non-negative. Product/variant identifiers and prices are
immutable snapshots, not foreign keys into another feature's tables.

## API and validation changes

The existing benefit preview request contains only `selection`; a quote that
binds shipping and fulfilment must also receive one `branchOrders` selection
per current cart location. Update the preview request, DTO and OpenAPI together
while preserving the existing checkout `benefitPreviewId` as the source quote
reference. The real preview response must distinguish merchandise subtotal,
discounts, shipping total and combined payable total in decimal strings. This
is an intentional contract change and must be coordinated with frontend and
the existing local prototype. The local mock remains explicitly local and must
not create real loyalty reservations or claim real carrier/payment results.

The current non-local implementation supports pickup only and fails closed for
GHTK while the separate shipping contract is unresolved. It persists the
FE-02/FE-06 preview and 15-minute deadline but does not yet implement the
real acceptance or payment lifecycle. The `local` profile retains its mock.

Reject wrong owner without leaking quote data; reject invalid or missing branch
choices, invalid selection and untrusted fee input; re-preview on stale cart,
shipping or changed FE-06 digest. On unavailable catalogue, loyalty or carrier
inputs, fail without creating an accepted checkout. Existing cart data stays
available to the Member after a rejected acceptance.

## Verification

- MySQL 8.4 fresh migration and upgrade from the V39 baseline; verify keys,
  constraints and that existing V14 local-demo rows are preserved.
- Unit/API tests for preview shape, ownership, changed/expired quote, branch
  selection, replay and mismatched idempotency key.
- MySQL transaction tests for FE-02 stock contention, FE-06 reservation failure,
  quote change and rollback of every order/hold/benefit effect.
- Concurrent accept tests with the same and different keys, plus crash/restart
  showing persisted acknowledgement is not lost. Do not count mock outcomes
  as VNPay or GHTK integration evidence.
