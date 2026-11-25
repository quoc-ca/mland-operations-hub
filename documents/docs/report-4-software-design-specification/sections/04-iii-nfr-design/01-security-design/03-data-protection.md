### 1.3 Data Protection

::: {.docx-table width="100%" columns="20%,30%,10%,40%" repeat-header="false"}

|Data Category|At Rest|In Transit|Notes|
|---|---|---|---|
|Identity and credentials|Firebase manages passwords. Mland stores Account role and status, not a second password or session store.|HTTPS|Passwords, ID tokens, provider secrets, and setup links are excluded from responses, logs, and audit.|
|Profiles and addresses|Owner data is stored in MySQL. Checkout copies the selected delivery address into the accepted order snapshot.|HTTPS|Only the owner and authorized business functions may read personal details.|
|Images and receipts|Public catalogue images and private reference or refund evidence have separate access rules. Private refund images use authenticated Cloudinary storage.|HTTPS|Validate file type and size. Private files are served only after an access check; public media must not expose private references.|
|Booking and mail credentials|Keep the token hash needed for booking-link checks. Keep raw delivery credentials only while they are valid for sending.|HTTPS / protected SMTP|Expired or revoked credentials are removed. Previews and logs do not expose links or QR payloads.|
|Payment and audit|Store verified payment outcomes and limited business evidence; do not store card details.|HTTPS|VNPay outcomes require signed verification. Business audit records are retained for five years.|
|Delivery recipient data|Keep recipient details during fulfilment.|HTTPS|Delete or irreversibly obscure recipient, contact, address, and note data 30 days after actual GHTK handoff; keep limited order evidence.|
|Notification and chat data|Remove recipient and rendered message details 90 days after delivery reaches a final state. Retain consultation transcripts for three years.|HTTPS / protected SMTP|Keep audit metadata separate from raw messages and credentials.|
|AI input|Keep AI text logs for at most 30 days. Do not retain AI input images after processing.|HTTPS|AI output is advice; it does not approve a business decision.|

:::
