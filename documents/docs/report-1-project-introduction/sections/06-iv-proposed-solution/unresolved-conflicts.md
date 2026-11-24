# Unresolved Conflicts and Open Decisions

This file records only decisions that remain open after the current Report 1 and feature baselines were synchronized. Historical conflicts are retained as resolved context in the feature artifacts and are not implementation instructions.

## 1. Resolved baseline decisions

1. Feature-004 no longer uses AI analysis, AI pricing, or automated design decisions.
2. Guest and Member may submit a design request; each request supports a description and/or up to five images.
3. Image quality is checked before submission and human review always records an explanation.
4. Feature-012 no longer attaches designs to workshop tickets or bookings.
5. Feature-012 saved designs provide advisory workshop recommendations and keep all discovery options visible.
6. Custom orders use one approved quote and complete payment, with no cart line and no deposit.
7. Workshop deposit and custom-order full payment remain separate payment policies.
8. Feature-013 consultation is not included in the Major Features implementation baseline.

## 2. Remaining open decisions

### 2.1 Payment provider and reconciliation

The provider-specific webhook/IPN contract, reconciliation evidence, retry policy, duplicate-event handling, and refund rules remain dependent on the payment integration baseline. A browser redirect must not be treated as payment confirmation.

### 2.2 Workshop capacity and branch exceptions

Two branches and three session templates are configurable data, but exact capacity, holidays, temporary closure, timezone, and branch capability precedence still require an approved operational configuration.

### 2.3 Notifications

The project still needs a final event list and delivery policy for booking confirmation, payment receipt, review result, quote availability, order status, pickup readiness, and internal approval notifications.

### 2.4 Google review governance

The customer-facing Google review link and any one-way Manager-triggered import require verified access, retention, attribution, moderation, appeal, and public-visibility policy. Google reviews must not be written back or used for staff compensation.

### 2.5 Feature-013 approval

Feature-013 has its own draft SPEC/DESIGN and may support consultation, but it remains outside the Report 1 Major Features baseline until scope and implementation approval are recorded.
