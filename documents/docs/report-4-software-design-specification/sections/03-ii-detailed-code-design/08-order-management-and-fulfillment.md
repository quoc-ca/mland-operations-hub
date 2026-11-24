## 8. Order Management and Fulfillment

This design set covers feature FE-08 Retail Order and Fulfillment (spec `ppswbs-spec/features/008-retail-order-and-fulfillment`). The backend modules are `features.readyringsales` (cart, checkout, payment, GHTK shipments, carrier portal) and `features.fulfilment` (store-pickup operations), both Spring Modulith modules layered as `controller → service → dao → entity`. A checkout is one `GroupedCheckout` with one `RetailOrder` per branch; stock is held per size and branch, and benefits are held in FE-06, until the order is paid or released. A VNPay hold lasts 10 minutes and a cash-at-the-counter hold 72 hours. Commands take pessimistic locks (availability guard → checkout → order), check `expectedVersion` where the client edits a resource, and are idempotent by `Idempotency-Key`. The module integrates with FE-01 Account Management (actor, role and branch scope), FE-02 Product Management (sale facts and stock through `CatalogueSalesFacade`), FE-06 Loyalty & Promotion (benefit preview, hold, consume and release), VNPay (payment) and GHTK (shipping fee).

### 8.1 Class Diagram

(Class diagram — 8.1a Cart and Checkout)

(Class diagram — 8.1b Payment, Cancellation and Order History)

(Class diagram — 8.1c Store Pickup and GHTK Shipping)

::: {.docx-table width="100%" columns="8%,32%,60%" repeat-header="false"}

|No|Class|Description|
|---|---|---|
|1|CartController|Member cart under `/api/v1/members/me/ready-ring-cart`: read, add a line, change quantity, remove a line.|
|2|CheckoutController|Benefit and price preview (`/ready-ring-benefit-previews`), GHTK shipping quote (`/ready-ring-shipping-quotes`), place order (`/ready-ring-orders`) and start VNPay payment (`/ready-ring-checkouts/{id}/payment`).|
|3|RetailCheckoutCancellationController|Member cancellation of a whole checkout or of one cash branch order.|
|4|RetailOrderQueryController|Member order history and order detail under `/api/v1/members/me/ready-ring-orders`.|
|5|RetailCounterCashController|Staff pickup desk, cash confirmation of a store-pickup order and Manager daily cash.|
|6|StaffRetailShipmentController|Staff and Manager GHTK shipment list, detail and the "packed and ready" transition.|
|7|CarrierShipmentController|GHTK partner portal (role CARRIER): all GHTK shipments and the courier transitions "picked up" and "delivered".|
|8|RetailPaymentReconciliationController|Manager list of VNPay transactions that need manual reconciliation.|
|9|PickupOperationsController (fulfilment)|Store-pickup preparation, handover to the customer and counter-cash handover.|
|10|RetailAuthorization / RetailActorResolver|Resolves the signed-in Member, Staff, Manager or Carrier from FE-01 and checks role and branch scope; never trusts a client-supplied account ID.|
|11|CartService|Cart rules: 1–99 per size across the cart, at most 50 lines, quantity within the branch stock counting what is already in the cart (`RETAIL_CART_QUANTITY_LIMIT` when the cart already holds some).|
|12|RetailCartSelection|Resolves the ticked cart lines (or the single Mua Ngay line) that a checkout prices, holds and orders.|
|13|RetailCheckoutQuotePreviewService|Prices the selected lines through FE-02, asks FE-06 for the benefit preview (automatic discount, voucher, points), adds GHTK fees and stores a durable `RetailCheckoutQuote`; splits each line's discount to its branch order and returns each ticked cart line's own share (`cartLineDiscounts`) so the cart shows the price that line really gets.|
|14|RetailCheckoutQuoteValidator|Revalidates a locked quote against the locked cart and the locked FE-02 sale facts before acceptance.|
|15|RetailCheckoutAcceptanceService|Accepts a quote (`accept`): creates the `GroupedCheckout`, one `RetailOrder` per branch, the size × branch holds and the FE-06 benefit hold, all or nothing.|
|16|RetailPaymentMethods|Payment-method rules: VNPay (at least 5,000 VND, 10-minute hold) or cash at the counter (store pickup only, 72-hour hold).|
|17|RetailDiscountAllocation|Splits a discount over branch orders in proportion to their amount, flooring each share and giving the remainder to the largest branches.|
|18|RetailPaymentService / VnpayRetailPaymentAdapter|Starts a VNPay payment for a held checkout and returns the payment URL; the adapter signs the request through `paymentintegration`.|
|19|RetailPaymentEvidenceService|Applies verified VNPay evidence: marks the checkout paid, sells the holds, consumes the FE-06 benefits, publishes `RetailOrderPaid` and creates GHTK shipments.|
|20|RetailQueryReconciliationService|Calls VNPay QueryDR outside the lock for payments without a result and records unresolved or late payments as `RetailPaymentReconciliation`.|
|21|RetailUnstartedExpiryService / RetailConfirmedUnpaidExpiryService (+ jobs)|Scheduled release of checkouts whose payment was never started, or was started and verified as failed, after the hold ends: holds released, FE-06 benefits released, lines returned to the cart.|
|22|RetailCheckoutCancellationService / RetailBranchPartService|Member cancellation of an unpaid checkout, or of one cash branch order on its own (only that order's holds, lines and benefit share).|
|23|RetailCashCollectionService|Staff confirms the exact counter cash of a pickup order; pays the branch order and records the handover in one transaction.|
|24|RetailShipmentCreationService|Creates one GHTK shipment (tracking number, recipient snapshot) for each paid GHTK branch order.|
|25|StaffRetailShipmentService / CarrierShipmentService|Shipment state machine: Staff moves PREPARING → READY_FOR_CARRIER; the carrier moves READY_FOR_CARRIER → HANDED_TO_CARRIER and → DELIVERED. Each step writes `RetailShipmentStatusHistory`.|
|26|RetailAvailabilityGuardService / RetailVariantHoldLifecycleService|Implements FE-02's availability boundary: locks the size and branch guard rows in ascending order and creates, sells, releases or expires `RetailVariantHold` rows only inside that lock.|
|27|RetailOrderQueryService / RetailCounterCashQueryService|Read models for Member order history, Staff pickup desk and Manager daily cash.|
|28|GhtkRetailShippingQuoteAdapter / DurableShippingQuoteWriter|Calls the GHTK fee API outside any transaction, then stores the `RetailShippingQuote` in its own transaction.|
|29|RetailCataloguePort, RetailCheckoutPricingPort, RetailBenefitPort, RetailPaymentPort, RetailShippingQuotePort|Outbound ports to FE-02, FE-06, VNPay and GHTK; each has a production adapter and a local development substitute.|
|30|RetailPickupOrderFacade / RetailCounterCashFacade / RetailOrderPaid|Facade exported to `fulfilment`: transaction-bound pickup operations, counter-cash payment and the post-payment event.|
|31|RetailCart / CartLine|Member cart and its lines (size, branch, quantity).|
|32|RetailCheckoutQuote / RetailCheckoutQuoteBranch / RetailCheckoutQuoteItem|Durable server-priced quote: selection, branch orders, items, discounts and payable total.|
|33|GroupedCheckout|One accepted checkout: payment method and state, payment deadline, frozen benefit evidence.|
|34|RetailOrder / OrderItem|One branch order with frozen lines, fulfilment method, discount share, shipping charge, payable amount and payment and fulfilment state.|
|35|RetailVariantHold / CheckoutHold|Stock hold per size and branch, and the checkout-level hold with its deadline.|
|36|RetailCashCollection|Confirmed counter cash: amount, Staff and time.|
|37|RetailShipment / RetailShipmentStatusHistory|GHTK shipment with tracking number, recipient snapshot, status and its history.|
|38|RetailShippingQuote|GHTK fee quote for one branch and address, with its expiry.|
|39|RetailPaymentReconciliation|VNPay transaction that needs a Manager's attention, with its reason.|
|40|PickupEvidence (fulfilment)|Evidence of preparation and handover of a store-pickup order.|

:::

### 8.2 Sequence Diagram — Add Product to Cart

(Sequence diagram)

The Member chooses a size, a branch and a quantity on the product page and presses "Thêm Vào Giỏ Hàng". `CartController` resolves the Member through `RetailAuthorization`, and `CartService` reads the size's sale facts at that branch through `RetailCataloguePort`. The size must be on sale; the quantity across every branch must stay within 1–99; and the quantity already in the cart for the same size and branch plus the new quantity must not exceed the branch stock. When the cart already holds some, the refusal is `RETAIL_CART_QUANTITY_LIMIT` with a message naming how many are in the cart; the product page shows it in a popup with one "Đồng ý" button. For "Mua Ngay" the product page then opens checkout with the line already in the cart instead of refusing. A new size and branch adds a line (at most 50 lines); an existing one increases its quantity.

### 8.3 Sequence Diagram — Preview and Place a Retail Order

(Sequence diagram)

Checkout opens with the ticked cart lines (`RetailCartSelection`), grouped by branch. For each branch the Member chooses store pickup or GHTK delivery; for GHTK, `GhtkRetailShippingQuoteAdapter` asks GHTK for the fee outside any transaction and `DurableShippingQuoteWriter` stores the quote. `RetailCheckoutQuotePreviewService` prices every line through `RetailCheckoutPricingPort`, asks FE-06 through `RetailBenefitPort` for the best automatic discount, at most one voucher stacked on what it left, and points on the remainder, and stores a `RetailCheckoutQuote`. The response shows the product discount, the voucher discount, the points discount and the savings as separate lines; each line's discount share goes to its branch order. When the Member presses "Đặt hàng", `RetailCheckoutService` locks the cart and the guard rows, revalidates the quote with `RetailCheckoutQuoteValidator`, and in one transaction creates the `GroupedCheckout`, one `RetailOrder` per branch, the `RetailVariantHold` rows and the FE-06 benefit hold. Any changed price, stock, fee or benefit returns a conflict and the Member reviews the new total.

### 8.4 Sequence Diagram — Pay a Retail Order with VNPay

(Sequence diagram)

For VNPay, `RetailPaymentService` builds the payment request for the whole checkout (at least 5,000 VND) and `paymentintegration` returns the signed VNPay URL; the browser is redirected to VNPay. The hold lasts 10 minutes from acceptance, and a second press of Thanh toán during the hold reuses the same URL instead of starting a new VNPay payment. VNPay calls the IPN endpoint; `paymentintegration` verifies the signature and amount and hands verified evidence to `RetailPaymentEvidenceService`, which, under the guard lock, marks the checkout and its branch orders paid, sells the holds through FE-02, consumes the FE-06 benefits, publishes `RetailOrderPaid` and calls `RetailShipmentCreationService` for every GHTK branch order. The browser return only shows the result; it never marks an order paid. A payment without a result is checked later by `RetailQueryReconciliationService` (QueryDR); a late or unresolved payment becomes a `RetailPaymentReconciliation` row for the Manager.

### 8.5 Sequence Diagram — Pay Cash at the Counter and Hand Over

(Sequence diagram)

Cash at the counter is allowed only when every branch order is a store pickup. Each branch order is held for 72 hours and paid on its own. At the counter the Staff of that branch opens the pickup desk (`RetailCounterCashQueryService`), and presses "Xác nhận đã thu" for the order. `RetailCashCollectionService`, through `RetailCounterCashFacade`, locks the order, checks the Staff's branch and the deadline, records the exact amount in `RetailCashCollection`, sells the holds, settles that order's FE-06 benefit share and records the handover, so the order goes straight to completed. Nothing is recorded if any step fails. A VNPay pickup order is prepared by Staff ("Đã chuẩn bị xong") and handed over when the customer reads the order code at the counter (`PickupOperationsService`).

### 8.6 Sequence Diagram — Ship a Retail Order with GHTK

(Sequence diagram)

When a GHTK branch order is paid, `RetailShipmentCreationService` creates its `RetailShipment` in `PREPARING`. The branch Staff packs the parcel and presses "Đã chuẩn bị xong"; `StaffRetailShipmentService` checks the branch and `expectedVersion` and moves the shipment to `READY_FOR_CARRIER`. The GHTK partner signs in with role CARRIER and opens the carrier portal; `CarrierShipmentService` lists the shipments of every branch and records the courier's two steps: "Đã lấy hàng" (`HANDED_TO_CARRIER`) and "Giao thành công" (`DELIVERED`). Every transition writes `RetailShipmentStatusHistory`, and the Member's order shows the matching stage (Chờ lấy hàng, Chờ giao hàng, Đã giao).

### 8.7 Sequence Diagram — Cancel or Expire an Unpaid Order

(Sequence diagram)

A Member may cancel an unpaid VNPay checkout (all its branch orders) or a single unpaid cash branch order. `RetailCheckoutCancellationService` (or `RetailBranchPartService` for one cash order) locks the guard rows and the checkout, releases the holds, releases the FE-06 benefit hold and returns the lines to the cart through `RetailCartRestoration`. The scheduled `RetailUnstartedExpiryJob` does the same for checkouts whose hold ended without a payment start, and `RetailConfirmedUnpaidExpiryJob` for VNPay payments verified as failed; a started payment is never released without a verified result. Each checkout is locked and rechecked on its own, so one failure does not stop the others.

### 8.8 Sequence Diagram — View Purchase Orders and Order Details

(Sequence diagram)

`RetailOrderQueryService` returns the Member's branch orders, newest first, with the display code `ML` + order date + first eight characters of the order ID (`RetailOrderCodes`), the items, the money lines (merchandise subtotal, shipping, total discounts, payable), the payment and shipment state, and for an unpaid VNPay order its payment deadline. The frontend sorts the orders into the tabs Tất cả, Chờ thanh toán, Chờ lấy hàng, Chờ giao hàng, Chờ nhận tại cửa hàng, Đã giao and Đã hủy, shows the 10-minute countdown and offers Thanh toán, Hủy đơn or Mua lại. A delivered or picked-up order also offers Đánh giá, or Xem đánh giá once every line is reviewed; the page reads which lines may still be reviewed from FE-11 Review Management, which rechecks eligibility when the review is submitted. The Staff branch order page reads the same orders through the pickup desk and the Staff shipment list, limited to the Staff's branches.
