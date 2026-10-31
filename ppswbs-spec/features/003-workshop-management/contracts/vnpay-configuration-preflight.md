# VNPay Paymentintegration Configuration Preflight

**Date created**: 2026-10-26
**Date updated**: 2026-10-26
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Status**: External-environment prerequisite; no credential value belongs in
this repository.

## Purpose

This checklist completes the implementation handoff for the shared
`paymentintegration` boundary documented in
[vnpay-payment-ingress.openapi.yaml](vnpay-payment-ingress.openapi.yaml). It
does not change the one-ingress design or authorize a provider secret in source
control.

VNPay documents IPN as a server-to-server HTTPS `GET`; the Merchant validates
the checksum before other processing and returns JSON `RspCode` and `Message`.
QueryDR and refund are separate outbound Merchant operations. See the official
[VNPay IPN guide](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
and [QueryDR/refund guide](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).

## Required external inputs per environment

The deployment/account owner must provide these through the approved secret and
deployment process, never Git, specifications, logs, tickets, or screenshots:

1. VNPay Merchant/terminal identity approved for the target environment.
2. The corresponding HMAC-SHA512 hash secret, held only by the runtime secret
   store/service identity that verifies or signs VNPay traffic.
3. A publicly reachable HTTPS IPN URL ending in
   `/api/v1/integrations/payments/vnpay/events`, registered with VNPay for that
   environment.
4. Approved outbound VNPay base endpoint and network/TLS egress for payment
   initiation, QueryDR, and refund operations.
5. Approved QueryDR and refund access/operation authority for the Merchant;
   refund remains consumed by 003.3 even though the technical adapter is shared.
6. The approved browser Return URL origin/path. It is display-only and must not
   be registered as an IPN substitute.

## Preflight evidence and acceptance

Before enabling a real VNPay environment, retain only redacted evidence that:

- the registered IPN address is HTTPS and reaches the one shared ingress;
- a valid signed sandbox callback is acknowledged with the required safe JSON
  response and reaches exactly its merchant-reference owner;
- a duplicate callback has no second business effect;
- an invalid checksum, unknown reference, mismatched amount/currency, and
  late-after-hold-expiry success have no Booking/Settlement side effect;
- QueryDR is outbound-only and result handling follows the same verification,
  routing, and idempotency boundary; and
- no secret, raw callback, raw checksum, card data, Magic Link, or ticket
  credential appears in retained evidence.

The feature can be implemented and tested with a fake adapter before this
preflight. Real payment initiation, IPN, QueryDR, and refund integration remain
disabled until all applicable inputs/evidence are approved.

## Ownership

`paymentintegration` owns inbound checksum verification, deduplication, and
owner-qualified dispatch. 003.1 consumes only verified `DEPOSIT` evidence;
003.3 consumes only verified `FINAL_PAYMENT`/`REFUND` evidence. Neither feature
owns provider credentials or may expose a second callback endpoint.
