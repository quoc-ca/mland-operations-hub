### 2.2 Order Management & Purchasing

*Trigger*: 
- A Member selects an available retail product and adds it to the shopping cart to initiate a purchase.

*End condition*: 
- After successful payment confirmation, the system creates and confirms the retail order, sends an order-confirmation email, submits the fulfilment or shipping request to the delivery provider, and records the initial order status.

*Alternative end conditions*:

- If the product is unavailable, the promotion is invalid, or payment fails, the system does not complete the order and displays an appropriate error message. If a payment hold or temporary reservation exists, it is released when the applicable timeout expires.

{{r3-swl-order width=100% align=center}}
