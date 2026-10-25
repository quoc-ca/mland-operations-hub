# Quickstart Validation: Workshop Settlement and Changes

**Date created**: 2026-10-24
**Date updated**: 2026-10-25
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

This is a design-validation checklist for later implementation; it does not
authorize production payment calls, credentials, or migrations.

## Preconditions

- Use non-production records and test VNPay configuration only.
- Prepare a confirmed Basic Ring booking and a confirmed Silverclay booking; 003.1
  provides their deposits and frozen package price.
- 003.2 must provide approved facade outcomes for required-session completion,
  attendance, capacity and ticket entitlement.
- Use active, authorized test actors. The central-policy case needs the eventual
  FE-001 authority, not a guessed role mapping.

## Validation scenarios

1. **One final bill/package**: after Basic Ring's only required session and after
   Silverclay's second required session, create/finalize one bill. No session,
   participant or ticket bill may exist; frozen package price/deposit stay intact.
2. **Charges**: accept an allowed fixed catalogue add-on at displayed price;
   reject arbitrary customer amount; require Staff/Manager confirmation and audit
   before a variable material/service charge enters the bill.
3. **VNPay**: accept one valid signed reference/amount/currency-matched IPN or
   QueryDR, then replay it. Browser return, invalid signature, mismatch and
   out-of-order failure must not settle or release output.
4. **Deferred payment**: verify authorized 24-hour link, at most one reminder,
   expiry retaining balance, and an audited reissue action.
5. **Customer cancellation**: test whole-booking 72h, 24–72h, and below-24h/
   checked-in conditions; verify 100%, 50%, rejection respectively. Ticket/capacity
   effects occur only after verified refund success.
6. **Required-session change**: request one/two future replacements. 003.2 either
   applies all capacity/entitlement changes and retains QR, or applies none. For
   Silverclay verify same branch, distinct chronology and the same-day slot gap.
7. **Mland cancellation**: offer replacement first; otherwise refund whole package
   by the retained policy snapshot. Confirm the default gives 100% of collected
   funds, and a prospective policy change affects only later confirmed bookings.
8. **Authorization/audit**: inactive/out-of-scope/unauthorized actions have no
   financial/capacity side effect; business evidence omits raw VNPay/Firebase data.

## Evidence

Retain approved contract/facade outcomes, module and upgrade-test output,
idempotency replay result, authorization denials, redacted audit references, and
data-bearing-environment recovery/rollback evidence. Dev/test reset is permitted
only after preflight confirms no retained booking/customer data; otherwise use an
append-only preservation migration and upgrade test.
