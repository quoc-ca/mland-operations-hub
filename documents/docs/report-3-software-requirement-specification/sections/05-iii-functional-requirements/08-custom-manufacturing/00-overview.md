## 8. Custom Manufacturing

Custom manufacturing is a Member-only, quote-to-order flow. It is separate
from ready-ring retail checkout and does not use a retail cart. After Staff or
a Manager accepts a Member's design request, the reviewer prepares a quote for
the complete custom-order amount and a Manager approves it. The Member must pay
that amount in one VNPay payment before the quote deadline; there is no deposit
and no later balance.

The customer-facing label is **Đơn hàng cá nhân** and each quote is shown as a
**Phiên bản báo giá**. A Feature-012 design may be attached as an owner-checked
reference regardless of its readable lifecycle state; the custom order keeps
its own immutable snapshot.
If payment is not verified before expiry, the quote and custom order are
expired/cancelled and any temporary queue hold is released; the Member may then
ask for a new quote.
