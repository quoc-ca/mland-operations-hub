## 8. Order Management and Fulfillment

This section covers Member retail carts, whole-cart checkout, verified VNPay payment, own-order views and pickup/manual GHTK fulfilment.

**Shared order requirements**

- Order attempts retain normally append-only actor/source, target, action, time, actual success/rejection/failure, reason and permitted context/references. Preserve original quote/payment evidence; keep minimized payment/exception/business-audit evidence five years and sanitized ordinary technical logs 30 days. Rejected/failed attempts are never recorded as success; exclude raw recipient data, card data, credentials, tokens and signing secrets.
- Keep necessary recipient data while GHTK handoff is pending; delete/irreversibly obscure private values and applicable copies 30 days after original actual handoff. Entry/edit/preparation and retries do not start/reset the clock; audit retention never extends it. Switching to pickup before preparing removes obsolete carrier data. Pickup collects no additional contact/address.
- Recorded in-application status is primary. Optional email through the existing workflow uses approved usable contact at paid, ready_for_pickup and actual pickup/handoff completion. Send once with at most two retries after 1 and 5 minutes from initial send. Email is not mandatory; failures never change a sale/order and messages follow recipient-data retirement.
- Benefits require complete policy-owner approval. Commercial voucher/loyalty rates, eligibility/caps, conversions/minimums and credit lifetime remain dependency/TBD. Missing terms disable only the related benefit and permit otherwise-valid no-benefit checkout; requested unavailable benefits require explicit reselection. If policy permits earning, trigger once at verified paid, not fulfilment. This section adds no policy/ledger administration.
- Preserve accepted variant/price snapshots and unknown legacy options. New checkout requires reviewed configured variants; no legacy option mapping or extra role grants are inferred. UC50 records readiness and UC53 records actual GHTK handoff, separately from prepared_for_carrier.
