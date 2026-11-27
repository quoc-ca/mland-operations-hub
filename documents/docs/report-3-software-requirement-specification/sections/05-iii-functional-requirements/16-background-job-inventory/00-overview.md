## 16. Background Job Inventory

The system runs 30 scheduled jobs. Section I.5.3 Background Job Inventory specifies each job's processing steps and failure behavior; this table is the summary.

::: {.docx-table width="100%" columns="12%,48%,40%" repeat-header="false"}

| Job ID | Job Name | Trigger |
| --- | --- | --- |
| JOB-01 | Release Expired Workshop Booking Holds | Every 30 seconds |
| JOB-02 | Recover Workshop Payments from VNPay | Every 15 seconds |
| JOB-03 | Retry Workshop Booking Confirmation | Every 1 minute |
| JOB-04 | Resend Failed Booking Confirmation Email | Every 30 seconds |
| JOB-05 | Expire Booking Access Links and Email Snapshots | Every 1 minute |
| JOB-06 | Start Workshop Sessions | Every 10 seconds |
| JOB-07 | Alert Unresolved Session Cancellation | Every 10 seconds |
| JOB-08 | Assign Staff to Upcoming Sessions | Every 5 minutes |
| JOB-09 | Finalize Ticket Attendance | Every 1 minute; publish every 1 second |
| JOB-10 | Hand Over Session Cancellations to Settlement | Every 1 second |
| JOB-11 | Refund Unanswered Cancellation Offers | Every 1 minute |
| JOB-12 | Expire Ticket Change Requests | Every 1 minute |
| JOB-13 | Reconcile Ticket Balance VNPay Payments | Every 1 minute |
| JOB-14 | Apply Workshop Loyalty Outcome | Every 1 minute |
| JOB-15 | Dispatch Prepared Refunds | Every 1 minute |
| JOB-16 | Complete Verified Refunds | Every 5 minutes |
| JOB-17 | Remind Payers to Submit Refund Account | Every 1 hour |
| JOB-18 | Clean Up Refund-Account Link Data | Every 1 hour |
| JOB-19 | Delete Unused Refund Receipt Images | Every 15 minutes |
| JOB-20 | Dispatch and Expire Settlement Notifications | Every 1 minute |
| JOB-21 | Expire Unstarted Retail Checkouts | Every 30 seconds |
| JOB-22 | Reconcile Started Retail VNPay Payments | Every 30 seconds, only when VNPay is enabled |
| JOB-23 | Deliver Queued Notifications | Every 30 seconds |
| JOB-24 | Retry Failed Booking and Settlement Emails | Every 5 minutes |
| JOB-25 | Clean Up Old Notification Data | Daily at 2:00 AM |
| JOB-26 | Delete AI Chat Data and Temporary Design Images | Daily at 2:00 AM |
| JOB-27 | Delete Unused Catalogue Images | Every 1 hour |
| JOB-28 | Re-check Unavailable Catalogue Images | Every 30 minutes |
| JOB-29 | Clean Up Expired Product Imports | Every 5 minutes |
| JOB-30 | Expire Guest Design Sessions | Every 5 minutes |

:::
