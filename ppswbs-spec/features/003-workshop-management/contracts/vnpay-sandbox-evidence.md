# VNPay Sandbox Evidence (T005)

**Date created**: 2026-11-15
**Date updated**: 2026-11-15
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Status**: Redacted evidence for 003.1 T005 and 003.3 T005, to be reviewed under 003.1 T046 and
003.3 T035. It is not an approval: the owner's review decides whether it is enough. No secret, raw
checksum, raw callback, card data, OTP, Magic Link or ticket credential is recorded here.

## Environment

| Item | Value |
| --- | --- |
| Environment | VNPay sandbox (`sandbox.vnpayment.vn`) |
| Terminal | `J4VFKGUW` (merchant identity; not a secret) |
| Hash secret | Held only in the backend runtime configuration (`.env`, git-ignored) |
| IPN URL registered with VNPay | `https://<ngrok-dev-domain>/api/v1/integrations/payments/vnpay/events` (HTTPS, the one shared ingress) |
| Return URL | `https://<ngrok-dev-domain>/payment/vnpay/return` (display only) |
| QueryDR URL | `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction` |
| Refund | Not used (owner decision 2026-11-15): every refund is the manual bank refund of 003.3 FR-038a |
| Backend build | Branch `quoc`, commits up to `762b008d`, plus the 2026-11-15 working-tree change removing VNPay Refund |

The ngrok domain is a developer tunnel; a real environment registers its own stable HTTPS host.

## Evidence against the preflight acceptance list

| Preflight requirement | Result | Evidence |
| --- | --- | --- |
| Registered IPN address is HTTPS and reaches the one shared ingress | Met | SIT portal "Cấu hình IPN URL" shows the HTTPS ingress above for terminal `J4VFKGUW`; every IPN below reached it |
| A valid signed sandbox callback is acknowledged with the safe JSON response and reaches exactly its owner | Met | E1 (real payment) and SIT case 1 |
| A duplicate callback has no second business effect | Met | SIT case 4 answered `02` "Already received"; the group stayed confirmed once |
| Invalid checksum (97), unknown reference (01), mismatched amount (04) have no side effect | Met | SIT cases 6, 3 and 5 |
| Late-after-hold-expiry success has no Booking/Settlement side effect | Not shown in sandbox | Covered only by automated tests (`PaymentIngressServiceTest`, late-deposit path); no sandbox run |
| QueryDR is outbound-only and its result follows the same verification boundary | Partly | The backend's handling is shown (an unsigned answer is kept `UNKNOWN`, no booking change); a verified QueryDR answer could not be obtained, see Q1 |
| No secret or raw credential in retained evidence | Met | This file |

## E1. Real sandbox payment through to IPN (2026-11-15)

A Guest group booking (package 146, one ticket, 200,000 VND, test contact `@example.com`) was paid on the
VNPay sandbox payment page with VNPay's published NCB test card, through OTP to completion.

| Field | Value |
| --- | --- |
| Group | `GRP-E05782BE5CCA45D5A714` |
| `vnp_TxnRef` | `GRP-E05782BE5CCA45D5A714-3905e5a8bb4e4052855bc151f8653f54` |
| `vnp_CreateDate` | `20261115101826` |
| VNPay transaction number | `15737386` |
| IPN received | `vnp_ResponseCode=00`, `vnp_TransactionStatus=00`, signature verified |
| Backend answer | `RspCode 00` |
| Result in Mland | Group `CONFIRMED`, payment attempt `SUCCESS`, one ingress receipt |

## E2. VNPay SIT IPN test suite (2026-11-15)

Run in the VNPay SIT portal for terminal `J4VFKGUW` on transaction
`GRP-D3DC6B53BEAE46878ACE-1984741264a341f1a450d0e19db7a741` (failure case on
`GRP-8FA8217A4A5C401DBA18-43f369294b9240beaf44c77d7ae3b0ec`). The portal sent each simulated IPN to the
registered ingress and graded the backend's answer. The SIT list "DS GD đã test" shows the run as
"Thành công" (tested 15/11/2026 04:41:49).

| # | SIT case | Expected by VNPay | Backend answered | Graded |
| --- | --- | --- | --- | --- |
| 1 | Successful payment | `00` | `00` OK | Đạt |
| 2 | Failed payment | `00` | `00` OK | Đạt |
| 3 | Order not found | `01` | `01` UNKNOWN_REFERENCE | Đạt |
| 4 | Order already confirmed | `02` | `02` Already received | Đạt |
| 5 | Invalid amount | `04` | `04` AMOUNT_CURRENCY_MISMATCH | Đạt |
| 6 | Invalid checksum | `97` | `97` INVALID_SIGNATURE | Đạt |

Notes:

- These IPNs are simulated by the SIT tool; the gateway transactions themselves stopped at OTP, as the
  SIT guide requires. E1 is the evidence of a real payment completing.
- Case 2 leaves the group `PENDING_PAYMENT` and the attempt `PENDING`: a failed payment is recorded but
  does not end the hold, so the customer may retry before the deadline.
- Case 4 answers `02` since the 2026-11-15 decision (it answered `00` before).

## Q1. QueryDR on the sandbox (2026-11-15): blocked by VNPay

| Request | VNPay answer |
| --- | --- |
| Backend `queryTransaction` for the completed payment of E1 (request built and signed per the VNPay QueryDR guide) | `99` "Unexpected error", no `vnp_SecureHash` |
| Same, earlier, for `GRP-D3DC6B53…` | `99` "Unexpected error", no signature |
| Same request with a **deliberately wrong checksum** | `99` "Unexpected error" (a checked request would give `97`) |
| Wrong terminal code and wrong checksum | `99` "Unexpected error" |
| Non-existent `vnp_TxnRef` and wrong checksum | `99` "Unexpected error" (a checked request would give `91`) |
| `vnp_RequestId` left empty | `99` "Parameter required" |

Conclusion: VNPay validates the required fields and then rejects every QueryDR request before it checks
the checksum, the terminal or the order. The fault is on VNPay's side for this sandbox terminal (QueryDR not
enabled, or restricted on sandbox), not in the request. Other teams report the same unsigned `99` on the
sandbox QueryDR. The backend handled it as designed: the answer is unsigned, so the operation is kept
`UNKNOWN` and nothing is confirmed or changed. The development journal shows every QueryDR since
2026-11-06 ended `UNKNOWN`, i.e. this has never worked on this terminal.

Impact while it stays blocked: payment confirmation through IPN works; the QueryDR recovery of a payment
whose IPN was lost cannot conclude, so such a case needs a manual check in the VNPay merchant portal.

Open action: ask VNPay to enable QueryDR for terminal `J4VFKGUW` (or to confirm that sandbox QueryDR is
restricted), then repeat Q1 and record a verified answer here.

## Defects found and fixed during this run

- Group payment `merchant_reference` was 65 characters for a `VARCHAR(64)` column, so every group VNPay
  payment failed with HTTP 500; fixed in commit `d4458ef1` (57 characters).
- A replayed IPN answered `00`; changed to `02` in commit `d0c77547` (SIT case 4).
