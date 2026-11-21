## 6. Loyalty Management

This design set covers feature FE-06 Loyalty & Promotion (spec `ppswbs-spec/features/006-loyalty-promotion`). The backend module is `features.loyaltypromotion`, a closed Spring Modulith module layered as `controller → service → dao → entity`. Its only public surface is the `facade` package: `LoyaltyFacade` for the order and booking owners, plus the SPI ports `PromotionTargetPort` and `PromotionSeedCataloguePort`. FE-06 has no HTTP endpoint for reserving, settling, earning, releasing or restoring benefits; FE-08 Retail Order and FE-03 Workshop Booking call the facade synchronously inside their own transaction, after their own VNPay or attendance evidence is verified. Manager and Member commands are idempotent through `Idempotency-Key`; facade effects are idempotent through the unique `(sourceKind, sourceRef)` and outcome keys.

### 6.1 Class Diagram

(Class diagram)

::: {.docx-table width="100%" columns="8%,32%,60%" repeat-header="false"}

|No|Class|Description|
|---|---|---|
|1|PromotionManagementApiController|Manager-only endpoints under `/api/v1/promotion-programs`: create, list, detail, update (new revision), pause, resume and usage.|
|2|LoyaltyPolicyApiController|Manager reads and updates the current loyalty policy (earn unit, point value, minimum and maximum redemption).|
|3|PublicPromotionApiController|Public list and detail of active promotions by scope (PRODUCT or WORKSHOP).|
|4|SavedVoucherApiController|Member saves a voucher to the wallet and lists saved vouchers.|
|5|LoyaltyAccountApiController|Member reads the point balance and the point transaction history.|
|6|LoyaltyAuthorization|Requires a Manager capability (`MANAGE_PROGRAMS`, `MANAGE_POLICY`) or a Member actor, based on FE-01 `ActorContext`.|
|7|LoyaltyCommandService|Idempotency receipt for Manager and Member commands: same key and digest replays, a different digest returns `IDEMPOTENCY_CONFLICT`.|
|8|LoyaltyAuditService|Writes `LoyaltyAudit` rows in the same transaction; refused requests are audited separately.|
|9|LoyaltyLockCoordinator|Takes pessimistic locks in a fixed order: registry → programs → wallet → member programs → accepted benefit; lock timeouts become `RETRYABLE_CONTENTION`.|
|10|PromotionCreationService / PromotionMaintenanceService|Create a voucher or automatic discount with its first revision and targets; update by adding an immutable revision; pause and resume with `expectedVersion`.|
|11|PromotionTargetValidationService|Validates targets through the single `PromotionTargetPort` provider of the scope before taking the registry write lock.|
|12|LoyaltyPolicyService|Reads and revises the immutable `LoyaltyPolicy` pointed to by `LoyaltyRegistry`.|
|13|SavedVoucherService, LoyaltyAccountQueryService, PublicPromotionQueryService|Member and public read and save use cases.|
|14|LoyaltyFacade / DefaultLoyaltyFacade|Public API for FE-08 and FE-03: preview, reserve, settle, grant earned points, release, restore, finish ineligible earn, and per-ticket voucher restoration.|
|15|LoyaltyQuoteService / LoyaltyCalculator|Evaluates eligible programs and points for a trusted source context: one program per order (largest automatic discount unless a voucher is chosen), point cap, point discount, earn base.|
|16|LoyaltyReservationService|Re-evaluates the quote strictly, holds voucher quota and points, and stores the `AcceptedBenefit` snapshot with its parts.|
|17|LoyaltySettlementService, LoyaltyEarnService, LoyaltyReleaseService, LoyaltyRestorationService, LoyaltyPartLifecycleService|Apply authoritative outcomes to the snapshot: consume, grant earned points, release holds, restore after a valid workshop cancellation, and the per-branch or per-ticket variants.|
|18|BenefitEffectSupport / VoucherValidity|Apply wallet, quota and ledger effects; decide whether a consumed voucher may come back (only while its revision window is still open).|
|19|PromotionTargetPort|SPI implemented by FE-02 `LoyaltyProductTargetAdapter` (PRODUCT) and FE-03 `LoyaltyWorkshopTargetAdapter` (WORKSHOP).|
|20|PromotionSeedCataloguePort|SPI implemented by FE-02 `LoyaltySeedCatalogueAdapter` so the promotion seed can resolve products and branches.|
|21|Repositories (`LoyaltyWalletRepository`, `AcceptedBenefitRepository`, `PointLedgerRepository`, …)|Spring Data JPA repositories, including eligibility candidate queries in `PromotionEligibilityRepository`.|
|22|LoyaltyRegistry|Singleton lock gate pointing to the current loyalty policy.|
|23|LoyaltyPolicy|Immutable policy revision: earn unit amount, point value, minimum points, maximum redemption percent, rounding mode.|
|24|PromotionProgram|Voucher or automatic discount: scope, normalized code, status, current revision, total and per-member limits, held and consumed counts.|
|25|PromotionRevision / PromotionTarget|Immutable terms of a program (window, percent or fixed discount, cap, minimum, points allowed) and its targets by dimension.|
|26|MemberProgram|Per-Member voucher state: saved time, held and consumed counts against the member limit.|
|27|LoyaltyWallet|One point wallet per Member; available points = balance − held.|
|28|AcceptedBenefit / AcceptedBenefitPart|Snapshot of the benefits accepted for one retail checkout or workshop booking, with points, voucher and earn states; parts split it per branch order or per ticket.|
|29|PointLedger|Append-only point movements (HOLD, CONSUME, RELEASE, RESTORE, EARN) with balances after each entry.|
|30|LoyaltyAudit|Operational audit event that also stores the command receipt.|

:::

### 6.2 Sequence Diagram — Apply Voucher and Points at Retail Checkout

(Sequence diagram)

The Member first receives a read-only `BenefitPreview` with a terms digest. When the order is placed, FE-08 calls `reserveAcceptedTransaction` inside its own transaction; FE-06 locks the registry, programs, wallet, member program and benefit in order, re-evaluates the quote strictly, and holds the voucher quota and the points. A changed quote, an exhausted quota or changed points aborts the whole checkout, including stock holds. After VNPay sends a verified IPN, FE-08 reports the authoritative outcome `RETAIL_FULLY_PAID`: FE-06 consumes the held components and then grants earned points with the policy recorded at acceptance, writing CONSUME and EARN ledger entries. An unpaid expiry or a cancellation before payment releases the holds and marks the earn ineligible.

### 6.3 Sequence Diagram — Hold Benefits when Booking a Workshop

(Sequence diagram)

The workshop cart quote calls `previewBenefits` with the package fee, participant count, sessions and tickets. FE-06 picks the largest active workshop automatic discount (FR-044) or the chosen voucher, then the points limit. When the booking group is created, FE-03 reserves seats and calls `reserveAcceptedTransaction` in the same transaction. FE-06 re-evaluates strictly, holds the points and voucher quota, stores the `AcceptedBenefit` with its `postPromotionAmount`, and splits it into one `AcceptedBenefitPart` per ticket. A Guest receives the automatic discount only, with no wallet and no earn; a Guest asking for a voucher or points, a changed quote or a negative net amount rolls back the seat reservation. FE-03 computes the deposit from `postPromotionAmount`.

### 6.4 Sequence Diagram — Restore Benefits after Workshop Cancellation

(Sequence diagram)

A late or invalid cancellation restores nothing and only marks the earn ineligible. A valid cancellation calls `restoreCancelledWorkshopBenefits`. Points always come back: consumed points are restored with a RESTORE ledger entry linked to the original debit, and held points are released. A held voucher is released. A consumed voucher comes back to the Member and program totals only while its accepted revision is still valid at the cancellation decision time; if the code has already expired, the use stays counted (FR-035, Session 2026-11-21). The code is never extended or reactivated.

### 6.5 Sequence Diagram — Earn Points after Workshop Completion

(Sequence diagram)

A scheduler in FE-03 processes finished tickets one by one. A Guest booking is marked processed with no earn. A no-show ticket ends its earn as ineligible. A ticket that is completed and fully paid grants the earned points allocated to that ticket at acceptance, credits the wallet and writes an EARN ledger entry with the ticket's part sequence. The workshop deposit only consumes benefits and never earns points.

### 6.6 Sequence Diagram — Create a Promotion Program

(Sequence diagram)

Only a Manager may create a program. The request is parsed into `CreateProgram`, executed through `LoyaltyCommandService` for idempotency, and its targets are validated by the owning feature through `PromotionTargetPort` before the registry write lock is taken. A voucher code must be unique across all history. The service stores the program, its first immutable revision and its targets, and writes an audit entry in the same transaction. Kind, scope and code cannot be changed afterwards; later edits create a new revision.

### 6.7 Sequence Diagram — Pause / Resume a Promotion Program

(Sequence diagram)

A Manager pauses or resumes a program with its current `configVersion` as `expectedVersion`. The service takes the registry write lock, returns `PROMOTION_NOT_FOUND` for an unknown program and `STALE_VERSION` on a version mismatch, then changes the status between `ENABLED` and `PAUSED` and increments `configVersion`. Resuming does not reset quota, usage history or the validity window.

### 6.8 Sequence Diagram — Update Loyalty Policy

(Sequence diagram)

A Manager reads the current policy and saves new values for the earn unit, point value, minimum points and maximum redemption percentage. The service creates a new immutable `LoyaltyPolicy` revision and points `LoyaltyRegistry` to it under the registry write lock. Benefits already accepted keep the policy revision recorded at acceptance, so later earning uses the old rate.

### 6.9 Sequence Diagram — Save Voucher

(Sequence diagram)

A Member saves a public voucher to the wallet. An unknown program or an automatic discount returns `PROMOTION_NOT_FOUND`. The service locks or creates the Member's `MemberProgram` row and sets only `savedAt`; saving does not hold quota and does not guarantee the voucher can still be used at checkout. Saving the same voucher again changes nothing.

### 6.10 Sequence Diagram — View Loyalty Point History

(Sequence diagram)

A Member reads the current balance, held points and available points, then a paged list of point transactions. A Member without a wallet sees a balance of zero; the read does not create a wallet. Each transaction shows its kind (HOLD, CONSUME, RELEASE, RESTORE, EARN), the change and the balance after it.
