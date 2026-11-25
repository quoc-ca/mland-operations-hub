## 7. Notification Management (FE-07)

**Implementation status:** Specification and backend notification boundaries exist; provider delivery and retry behavior require environment verification.

### 7.1 Class Diagram

The module stores approved templates, durable notification requests and delivery attempts. Business modules create requests after their own transaction commits; SMTP delivery cannot change the business result.

### 7.2 Sequence Diagram — Create Notification after Business Commit

### 7.3 Sequence Diagram — Render and Send Email

### 7.4 Sequence Diagram — Retry Temporary Delivery Failure

