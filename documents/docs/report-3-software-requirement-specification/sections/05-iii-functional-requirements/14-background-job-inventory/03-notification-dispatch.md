### 14.3 Notification Dispatch

**Trigger:** A committed workflow creates a pending email task or an eligible retry becomes due.

**Purpose:** Deliver approved security/workflow emails without changing the accepted business result.

**Processing steps**

1. Read the committed task and approved template/recipient snapshot. The account service supplies the intended recipient and current invitation reference; booking and retail supply their own approved recipients.
2. Render and send through configured Mland SMTP, obtaining a current setup URL only transiently for an internal invitation. Persist no raw setup link or secret.
3. Apply the policy of the message type. General requests make at most three definite attempts; booking requests use their separate two-attempt policy. Internal invitations make at most two definite attempts, initially and after about one minute. A possible SMTP acceptance becomes UNKNOWN_OUTCOME and is not retried automatically.
4. Record the actual delivery result. Mail failure does not reverse an accepted account, sale or booking; a new internal account stays PENDING_SETUP until setup succeeds.

**Failure behavior**

| Scenario | System behavior |
| --- | --- |
| Temporary dependency or processing failure | Record a safe failure and use the approved retry policy; no false success or duplicate business effect. |
| Stale, duplicate or conflicting evidence | Recheck current facts and return no change or a safe rejection; preserve accepted business history. |

**Source:** [Background Job Inventory](../../03-i-overall-requirements/05-system-fuctionalities/03-background-job-inventory.md).
