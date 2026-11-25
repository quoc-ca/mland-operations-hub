## 2. Product Management (FE-02)

**Implementation status:** Backend and frontend slices exist; publication, price approval and branch stock remain server-authorized operations.

### 2.1 Class Diagram

{{r4-cd-fe02-product-management width=100% align=center}}

The module owns catalogue products, fixed-size variants, branch stock, images, categories and price proposals. Product and price decisions are separated from retail checkout.

### 2.2 Sequence Diagram — Create Product and Manage Variants

{{r4-sd-fe02-create-product width=100% align=center}}

### 2.3 Sequence Diagram — Submit and Approve Price

{{r4-sd-fe02-price-approval width=100% align=center}}

### 2.4 Sequence Diagram — Publish Product and Read Public Catalogue

{{r4-sd-fe02-publish-catalogue width=100% align=center}}

