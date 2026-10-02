users
roles
loyalty_points

products
categories
orders
order_items
product_imgs
payments
delivery_infors

promotions

workshop_registration
slots
workshop_packages
worshop_exceptions

## Custom design request

### custom_design_requests

| Column | Type | Constraints / description |
| --- | --- | --- |
| `custom_design_request_id` | BIGINT | Primary key. |
| `member_user_id` | BIGINT | Required foreign key to `users`. Only a Member can submit a request. |
| `request_description` | TEXT | Required description entered by the Member. |
| `img_url` | TEXT | Required reference-image URL or object-storage path. Each request stores exactly one image. |
| `status` | VARCHAR(20) | Required; defaults to `need_review`. Allowed values: `need_review`, `accepted`, `rejected`. |
| `reviewed_by_user_id` | BIGINT | Nullable foreign key to `users`; set when the request is accepted or rejected. |
| `reviewed_at` | DATETIME | Nullable; set when the request is accepted or rejected. |
| `review_reason` | TEXT | Required when `status` is `rejected`; not required when `status` is `accepted`. |
| `created_at` | DATETIME | Required submission timestamp. |
| `updated_at` | DATETIME | Required last-update timestamp. |

Indexes:

- `(status, created_at)` for the review queue.
- `(member_user_id, created_at)` for a Member's request history.

Rules:

- Lifecycle: `need_review` -> `accepted` or `rejected`.
- A Member cannot edit a request description or its reference images after submission. A rejected request cannot be reviewed again or resubmitted; the Member creates a new request instead.
- This table has no relationship to `ring_designs`, `orders`, pricing, payment, fulfilment, guest requests, or AI analysis.

ring_designs
gemstones
materials
attachments
shape
