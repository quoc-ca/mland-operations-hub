## 5. Personalized Design and Custom Manufacturing (FE-05)

### 5.1 Architecture Overview (Method 1B Integration)

Custom manufacturing fulfills Member-approved bespoke jewellery through an authoritative, full-payment lifecycle. In order to support logistics tracking while strictly isolating the architecture boundary of retail order shipments (`retail_shipments`), fulfillment integrates via **Method 1B**:
- **Dedicated Entity & Storage**: `CustomOrderShipment` mapped to the database table `custom_order_shipments` (Flyway migration `V206__custom_order_shipments.sql`), maintaining a 1:1 foreign key with `custom_orders(id)`.
- **Authoritative Tracking Generation**: Upon advancing a custom order to `READY_FOR_FULFILMENT` after workshop production, the system generates a standard 10-digit GHTK tracking number (`S...`).
- **Two-Phase Custody Handover**:
  1. *Workshop Packing*: Staff inspects the finished piece, packs it, and advances the shipment to `READY_FOR_CARRIER` ("Chờ bưu tá GHTK lấy hàng").
  2. *Carrier Operations*: The courier authenticates with role `CARRIER` on `/carrier/orders`, advancing the shipment to `HANDED_TO_CARRIER` ("Đang vận chuyển") upon pickup, and `DELIVERED` ("Đã giao hàng") upon delivery.
- **Terminal Synchronization**: Recording `DELIVERED` automatically updates the parent custom order to `status = CLOSED`, `fulfilmentStatus = DELIVERED`, and `productionProgress = COMPLETED`. Direct shop-side closure without carrier delivery is rejected with HTTP `409 CONFLICT`.
- **Payment Session Decoupling**: The VNPay gateway session (`vnp_ExpireDate`) is capped at 10 minutes (`now + 10m`), decoupled from quote validity (24–72 hours). Browser return parameters (`vnp_ResponseCode=00`) trigger immediate client confirmation and reactive polling awaiting asynchronous IPN completion.

> [!NOTE]
> Detailed class and sequence diagram re-baseline specifications for FE-04, FE-05, FE-11, FE-12 and FE-13 are documented in [UML_REBASELINE_GUIDE.md](../UML_REBASELINE_GUIDE.md). Class diagrams and sequence diagrams use separate visual baselines.

### 5.2 Class Diagram

{{r4-cd-fe05-custom-manufacturing width=100% align=center}}

### 5.3 Sequence Diagram — Convert Accepted Request to Custom Order

{{r4-sd-fe05-create-custom-order width=100% align=center}}

### 5.4 Sequence Diagram — Manager Approves Quote and Member Pays in Full

{{r4-sd-fe05-full-payment width=100% align=center}}

### 5.5 Sequence Diagram — Fulfil Paid Custom Order

{{r4-sd-fe05-custom-fulfilment width=100% align=center}}
