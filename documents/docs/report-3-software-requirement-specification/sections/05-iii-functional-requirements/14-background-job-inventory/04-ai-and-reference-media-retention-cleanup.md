### 14.4 AI and Reference Media Retention Cleanup

**Trigger:** Daily or after an approved immediate cleanup request.

**Purpose:** Retire AI/reference material according to its purpose and approved retention.

**Processing steps**

1. Remove Gemini text data beyond the maximum 30-day retention and AI image input after processing.
2. Identify eligible rejected/withdrawn references or references reaching their approved retention boundary; do not treat catalogue media as AI input.
3. Delete eligible data/copies safely, retain minimized cleanup evidence and record failures for retry; unspecified storage controls remain TBD.

**Failure behavior**

| Scenario | System behavior |
| --- | --- |
| Temporary dependency or processing failure | Record a safe failure and use the approved retry policy; no false success or duplicate business effect. |
| Stale, duplicate or conflicting evidence | Recheck current facts and return no change or a safe rejection; preserve accepted business history. |

**Source:** [Background Job Inventory](../../03-i-overall-requirements/05-system-fuctionalities/03-background-job-inventory.md).
