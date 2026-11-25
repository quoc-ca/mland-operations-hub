### 1.3 Data Protection

::: {.docx-table width="100%" columns="20%,30%,10%,40%" repeat-header="false"}

|Data Category|At Rest|In Transit|Notes|
|---|---|---|---|
|Identity and credentials|Firebase manages passwords. Mland stores Account role, status and an invitation-secret digest, not a second password or session store.|HTTPS|Passwords, ID tokens and provider secrets are excluded from logs and audit. A setup link is returned only for its authorized setup flow or Admin's controlled recovery, with no-store responses.|
|Profiles and addresses|Owner data is stored in MySQL. Checkout copies the selected delivery address into the accepted order snapshot.|HTTPS|Only the owner and authorized business functions may read personal details.|
|Images and receipts|Public catalogue images and private reference or refund evidence have separate access rules. Private refund images use authenticated Cloudinary storage.|HTTPS|Validate file type and size. Private files are served only after an access check; public media must not expose private references.|
|Booking and mail credentials|Keep the token hash needed for booking-link checks. Any approved booking delivery credential is kept only while valid; an internal invitation stores only its secret digest and key version.|HTTPS / protected SMTP|Expired or revoked credentials are removed. General previews and logs do not expose links or QR payloads.|
|Payment and audit|Store verified payment outcomes and limited business evidence; do not store card details.|HTTPS|VNPay outcomes require signed verification. Business audit records are retained for five years.|
|Delivery recipient data|Keep recipient details during fulfilment.|HTTPS|Delete or irreversibly obscure recipient, contact, address, and note data 30 days after actual GHTK handoff; keep limited order evidence.|
|Notification and chat data|Remove recipient and rendered message details 90 days after delivery reaches a final state. Retain consultation transcripts for three years.|HTTPS / protected SMTP|Persist only a safe invitation reference and template rendition with a placeholder, never its expanded URL or secret. Keep audit metadata separate from raw messages and credentials.|
|AI input|Keep AI text logs for at most 30 days. Do not retain AI input images after processing.|HTTPS|AI output is advice; it does not approve a business decision.|

:::
