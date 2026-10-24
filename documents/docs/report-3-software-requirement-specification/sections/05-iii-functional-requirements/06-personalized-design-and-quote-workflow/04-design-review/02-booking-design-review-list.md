#### 6.4.2 Booking Design Review List

![](assets/screens/54-booking-design-review-list.png)

This screen allows authorized Staff or Managers to:

- Browse design requests within the current reviewer's authority.
- As Staff, access requests estimated at or below 3,000,000 VND; as Manager, access requests above that threshold.
- Open a request's current details for review.

| Field Name | Description |
| --- | --- |
| Booking / Member / package / state | Read-only review-context columns; show only records within the reviewer's scope. |
| Open File | Opens the selected authorized request and its retained evidence. |
| Assessment / final price | Generic input requiring screen-specific meaning; a design assessment must not silently become an approved order balance. |
| Return / Approve | Decision controls requiring current action authority and matching reviewed terms; they cannot mark payment paid. |
| Manager-final-approval notice | Applies to final custom amount. Staff may still perform an eligible design review within the separate review threshold. |
