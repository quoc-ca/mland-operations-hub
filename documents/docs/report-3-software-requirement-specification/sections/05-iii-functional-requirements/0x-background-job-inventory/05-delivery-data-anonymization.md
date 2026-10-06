### x.5 Delivery Data Anonymization Job (`DELIVERY_DATA_ANONYMIZATION`)

*Trigger*: Daily scheduled run for retail or custom orders whose GHTK handoff was completed at least 30 days ago.

*Purpose*: Remove or irreversibly obscure recipient and delivery-address data after the approved operational retention period while preserving business and payment audit evidence.

*Processing steps*:

    1. Select orders with a recorded manual GHTK handoff date at least 30 days in the past.
    2. Replace recipient name, phone number, and delivery address with irreversible masked values.
    3. Preserve the order, fulfilment status, handoff date, payment reference, and required audit evidence.
    4. Record the anonymization result without retaining the original delivery data in job logs.

*Failure behavior*:

|Scenario|System behavior|
|---|---|
|Order has no completed handoff|Do not anonymize delivery data; evaluate it again after a valid handoff is recorded.|
|Anonymization transaction fails|Roll back the partial update and retry on the next scheduled run.|
|Audit or payment evidence is referenced by delivery data|Mask only personal delivery fields and preserve the required non-personal evidence.|
