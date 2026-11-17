# Review Management Use Cases

Date created: 2026-11-03  
Date updated: 2026-11-03  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt

## UC-011.1 Submit or edit review

Guest looks up eligible sources with email/phone or Member opens own eligible
sources. The selected workshop attendance must be `CHECKED_IN`; the selected
retail order line must be from a `PICKED_UP` or `DELIVERED` order. The actor
submits rating 1–5 and optional text ≤2,000 characters. The server revalidates
ownership, target mapping, uniqueness and idempotency, then creates/updates a
`PUBLIC` review and audit history.

## UC-011.2 Public review discovery

Visitors browse `PUBLIC` reviews and public-only aggregates for a workshop
session or retail product. The response may prioritize newest five-star reviews
on the Homepage, Product Details and Workshop Details. It never exposes email,
phone or transaction identifiers.

## UC-011.3 Post-publication moderation

Staff can hide any review. Manager can hide/remove and create/edit one current
reply. Every action requires authorization, an optional reason according to the
contract, optimistic version protection and an append-only audit event. Admin
cannot make these business decisions.
