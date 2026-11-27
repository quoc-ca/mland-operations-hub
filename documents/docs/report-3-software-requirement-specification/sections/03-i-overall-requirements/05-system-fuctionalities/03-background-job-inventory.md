### 5.3 Background Job Inventory

All jobs run inside the application server and are enabled by default. A run that fails is logged, and the next scheduled run picks up the remaining work. Intervals are measured from the end of the previous run.

#### 5.3.1 Release Expired Workshop Booking Holds (JOB-01)

***Trigger***: Scheduled — every 30 seconds

***Purpose***: Releases booking groups whose 12-minute payment hold ended without a confirmed payment.

***Processing steps***:

1. Find up to 50 groups in Pending Payment whose hold has expired, oldest first.
2. Hand any waiting confirmation or mismatched payment evidence to the late-payment review.
3. Release the reserved seats and return the voucher and points.
4. Set the group and its tickets to Expired and close open payment attempts.
5. Queue the "hold expired" email.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Release of one group fails | That group alone rolls back and the failure is audited. The job retries 3 times, 1 minute apart, then alerts Managers once and retries every 5 minutes. Seats stay held meanwhile. |
| No expired groups | Job completes immediately with no action. |

:::

#### 5.3.2 Recover Workshop Payments from VNPay (JOB-02)

***Trigger***: Scheduled — every 15 seconds

***Purpose***: Asks VNPay for the payment status of a booking group when no payment notification (IPN) has arrived.

***Processing steps***:

1. Find open payment attempts of groups still in Pending Payment.
2. Skip attempts whose hold expired or that already have a verified notification.
3. Send one status query (QueryDR) at minute 10 and one at minute 11 of the hold.
4. Apply only a signed, successful answer whose amount and reference match exactly.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| VNPay unreachable or answer pending | The answer is ignored and the hold is never extended. An unresolved case goes to the late-payment review after expiry. |
| Merchant query settings missing | The query is skipped and a warning is logged. |

:::

#### 5.3.3 Retry Workshop Booking Confirmation (JOB-03)

***Trigger***: Scheduled — every 1 minute

***Purpose***: Retries confirming a booking group whose payment was verified but whose confirmation failed.

***Processing steps***:

1. Find up to 50 waiting retries that are due.
2. Close the retry if the group is already confirmed.
3. Hand the payment to the late-payment review if the hold has expired.
4. Otherwise confirm the group again; repeated confirmation has no extra effect.
5. On failure, schedule the next attempt 1 minute later, up to 10 attempts.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Confirmation fails again | The failure reason is recorded and retried 1 minute later. |
| 10 attempts used | No further attempts; the case moves to the late-payment review when the hold expires. |

:::

#### 5.3.4 Resend Failed Booking Confirmation Email (JOB-04)

***Trigger***: Scheduled — every 30 seconds

***Purpose***: Resends the confirmation email of a confirmed group after delivery failed, and alerts Managers when resends run out.

***Processing steps***:

1. Find confirmed groups with an email delivery failure in the last 3 hours.
2. Resend after 5 minutes, 30 minutes and 2 hours from the last failure.
3. Record each resend in the audit log.
4. Alert Managers and mark the case for manual contact when resends run out or no valid access link exists.
5. Move overdue late-payment results to manual contact.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Resend of one group fails | That group alone rolls back and is retried on the next run. Progress is kept in the audit log, so it survives restarts. |
| No failed deliveries | Job completes immediately with no action. |

:::

#### 5.3.5 Expire Booking Access Links and Email Snapshots (JOB-05)

***Trigger***: Scheduled — every 1 minute

***Purpose***: Expires booking-group Magic Links and removes expired links and one-time codes from stored emails.

***Processing steps***:

1. Find up to 100 active Magic Links whose lifetime has ended.
2. Set each link to Expired and remove it from the stored email copies.
3. Clear every stored email copy whose retention time has ended.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Database error | The whole batch rolls back and is retried on the next run. |
| Nothing expired | Job completes immediately with no action. |

:::

#### 5.3.6 Start Workshop Sessions (JOB-06)

***Trigger***: Scheduled — every 10 seconds

***Purpose***: Moves published or sales-paused sessions to In Progress at their scheduled start time.

***Processing steps***:

1. Find published or sales-paused sessions whose start time has passed.
2. Lock each session, check its state again and set it to In Progress.
3. Record the automatic start in the audit log.
4. Log an error and count a metric when the start is more than 5 minutes late.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| One session fails | That session alone rolls back, an error is logged, and it is retried on the next run. |
| No session due | Job completes immediately with no action. |

:::

#### 5.3.7 Alert Unresolved Session Cancellation (JOB-07)

***Trigger***: Scheduled — every 10 seconds

***Purpose***: Alerts Managers once about a session still awaiting cancellation 24 hours after its scheduled end.

***Processing steps***:

1. Find sessions in Cancellation Requested whose end time passed more than 24 hours ago.
2. Skip sessions that were already alerted.
3. Send the alert email to every active Manager.
4. Record the alert in the audit log only when every email was accepted.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| No active Manager, or an email is rejected | No alert is recorded; the job tries again on the next run without sending duplicates. |
| One session fails | That session alone rolls back and is retried on the next run. |

:::

#### 5.3.8 Assign Staff to Upcoming Sessions (JOB-08)

***Trigger***: Scheduled — every 5 minutes, first run 1 minute after start-up

***Purpose***: Assigns every active Staff member of a location to the location's upcoming sessions.

***Processing steps***:

1. Find sessions from today onward that can still receive Staff.
2. Get the active Staff list of each session's location.
3. Add an assignment for each Staff member without one; a revoked assignment is never restored.
4. Record each new assignment in the audit log.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Error part-way | Assignments already saved are kept; a warning is logged and the next run adds the rest. |
| Audit write fails | The assignment is kept and a warning is logged. |

:::

#### 5.3.9 Finalize Ticket Attendance (JOB-09)

***Trigger***: Scheduled — every 1 minute to detect; every 1 second to publish

***Purpose***: Records when a ticket's attendance becomes final and informs settlement and loyalty.

***Processing steps***:

1. Evaluate the attendance outcome of each active ticket.
2. Record one "attendance finalized" event for each ticket whose outcome is final.
3. Publish up to 50 unpublished events, claiming each so it is published once.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Error during detection or publishing | The run rolls back and is repeated on the next run. |
| No ticket ready | Job completes immediately with no action. |

:::

#### 5.3.10 Hand Over Session Cancellations to Settlement (JOB-10)

***Trigger***: Scheduled — every 1 second

***Purpose***: Passes cancelled sessions and their affected bookings to settlement for remedies and refunds.

***Processing steps***:

1. Find up to 50 unpublished cancellation handovers.
2. Read the affected booking references.
3. Publish the cancellation request to settlement and mark it published.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Publishing fails | The whole batch rolls back and is repeated 1 second later. |
| Nothing pending | Job completes immediately with no action. |

:::

#### 5.3.11 Refund Unanswered Cancellation Offers (JOB-11)

***Trigger***: Scheduled — every 1 minute

***Purpose***: Turns a Mland cancellation offer the buyer did not answer in time into a full refund.

***Processing steps***:

1. Find offers still awaiting an answer after their deadline.
2. Choose the full refund for each, as the system.
3. Close each cancellation case when no offer is still awaiting an answer, and record it in the audit log.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Error while processing offers | An error is logged; unprocessed offers are retried on the next run. |
| Operations refuses to close a case | The case stays open and is retried on the next run. |

:::

#### 5.3.12 Expire Ticket Change Requests (JOB-12)

***Trigger***: Scheduled — every 1 minute

***Purpose***: Expires pending ticket change requests on time so the buyer is told promptly.

***Processing steps***:

1. Find pending change requests whose deadline has passed.
2. Lock each request and check its state again.
3. Set it to Expired, record it in the audit log and send the "expired" email.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| One request fails | That request alone rolls back, an error is logged and it is retried on the next run. |
| Nothing expired | Job completes immediately with no action. |

:::

#### 5.3.13 Reconcile Ticket Balance VNPay Payments (JOB-13)

***Trigger***: Scheduled — every 1 minute

***Purpose***: Settles ticket balance payments made through VNPay that are still waiting for verification.

***Processing steps***:

1. Find up to 100 VNPay balance collections in Pending Verification.
2. Fail a collection when a verified failure notification exists.
3. After the payment link expires, query VNPay at most once every 5 minutes for 24 hours.
4. Apply a signed successful answer as payment; fail the collection on a signed failure.
5. After 24 hours, fail it automatically on a signed "no payment" answer and record it in the audit log.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| VNPay unreachable or unclear answer | A warning is logged and the collection is queried again later. |
| Merchant query settings missing | The query is skipped and a warning is logged. |

:::

#### 5.3.14 Apply Workshop Loyalty Outcome (JOB-14)

***Trigger***: Scheduled — every 1 minute

***Purpose***: Applies the final loyalty-points result of a workshop ticket once attendance and payment are final.

***Processing steps***:

1. Find final bills whose loyalty result is not yet applied.
2. End the point earning with no points for a final no-show.
3. Grant points for attendance only when the bill is finalized with a zero balance; guest bookings earn no points.
4. Return voucher and points for a valid cancellation.
5. Mark the bill as processed; a bill whose attendance is not final is checked again later.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Error on one bill | Settlement and loyalty for that bill roll back together, a warning is logged, and it is retried on the next run. |
| Nothing to process | Job completes immediately with no action. |

:::

#### 5.3.15 Dispatch Prepared Refunds (JOB-15)

***Trigger***: Scheduled — every 1 minute

***Purpose***: Moves prepared refund parts into the bank-transfer refund process.

***Processing steps***:

1. Find up to 100 refund parts in Prepared.
2. Open a bank refund request for each and set the part to Pending Bank Refund.
3. Reject a customer cancellation that is not refundable.
4. Leave a case with no refund route for a Manager to handle.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| One part fails | A warning is logged and the part is retried on the next run. |
| No refund route | An error is logged and the case waits for a Manager. |

:::

#### 5.3.16 Complete Verified Refunds (JOB-16)

***Trigger***: Scheduled — every 5 minutes

***Purpose***: Closes refund cases once a Manager has verified every bank transfer.

***Processing steps***:

1. Find up to 100 refund cases waiting for transfer verification.
2. Resolve each case whose refund parts are all verified.
3. Record it in the audit log and notify the customer.
4. Close the open ticket bill and update the booking where applicable.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| One case fails | A warning is logged; the case, and for a bill correction the remaining cases, wait for the next run. |
| Nothing verified | Job completes immediately with no action. |

:::

#### 5.3.17 Remind Payers to Submit Refund Account (JOB-17)

***Trigger***: Scheduled — every 1 hour

***Purpose***: Reminds payers on day 3 and day 7 that Mland still needs their bank account for a refund.

***Processing steps***:

1. Find refund requests still waiting for an account for at least 3 days.
2. Choose the day-7 reminder if due, otherwise the day-3 reminder.
3. Issue a fresh refund-account link and email it.
4. Record the reminder in the audit log and on the request.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| A reminder fails | The batch is not saved and every reminder is retried on the next hourly run. |
| No request due | Job completes immediately with no action. |

:::

#### 5.3.18 Clean Up Refund-Account Link Data (JOB-18)

***Trigger***: Scheduled — every 1 hour

***Purpose***: Deletes old refund-link submission records and removes expired link tokens from stored emails.

***Processing steps***:

1. Delete submission records 30 days after their link expired.
2. Remove the tokens of links that expired unused.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Step 1 fails | Step 2 is skipped in that run; both are retried on the next run. |
| Nothing expired | Both steps change nothing. |

:::

#### 5.3.19 Delete Unused Refund Receipt Images (JOB-19)

***Trigger***: Scheduled — every 15 minutes

***Purpose***: Deletes refund receipt images that were not attached to a recorded transfer within 24 hours.

***Processing steps***:

1. Mark up to 100 unattached images older than 24 hours for deletion.
2. Delete up to 100 marked images from cloud storage.
3. Set each deleted image to Deleted; attached images are never touched.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Cloud storage delete fails | A warning is logged; the image stays marked and is retried on the next run. |
| No image eligible | Job completes immediately with no action. |

:::

#### 5.3.20 Dispatch and Expire Settlement Notifications (JOB-20)

***Trigger***: Scheduled — every 1 minute

***Purpose***: Sends pending settlement notifications to the notification module and deletes expired notification content.

***Processing steps***:

1. Delete settlement notification records whose content has expired.
2. Find up to 100 pending records that have not expired, oldest first.
3. Pass each to the notification module and mark it dispatched.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Dispatch fails | The batch rolls back and is sent again on the next run; the notification module ignores duplicates. |
| Delete fails | Dispatch is skipped in that run and both steps are retried. |

:::

#### 5.3.21 Expire Unstarted Retail Checkouts (JOB-21)

***Trigger***: Scheduled — every 30 seconds

***Purpose***: Expires accepted checkouts whose hold ended before any payment was started (10 minutes for VNPay, 72 hours for counter cash).

***Processing steps***:

1. Find up to 100 checkouts with expired holds and no payment started.
2. Lock the cart and checkout and check the state again.
3. Release the held product quantity and return the items to the cart.
4. Mark the checkout expired unpaid; for cash, only unpaid branch orders expire.
5. Release the held voucher and points.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| One checkout fails | That checkout alone rolls back, a warning is logged and it is retried on the next run. |
| No expired checkout | Job completes immediately with no action. |

:::

#### 5.3.22 Reconcile Started Retail VNPay Payments (JOB-22)

***Trigger***: Scheduled — every 30 seconds, only when VNPay is enabled

***Purpose***: Settles retail checkouts whose VNPay payment was started but had no result when the hold ended.

***Processing steps***:

1. Find up to 100 checkouts with expired holds and a started VNPay payment.
2. Expire the checkout and return the items when a verified failure exists.
3. Otherwise query VNPay at most once every 5 minutes.
4. Apply a signed successful answer as payment; expire the checkout on a signed failure.
5. After 60 minutes without an answer, send the checkout to manual reconciliation.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| VNPay unreachable or error | A warning is logged and the checkout is retried on the next run. |
| Merchant query settings missing | The query is skipped. |

:::

#### 5.3.23 Deliver Queued Notifications (JOB-23)

***Trigger***: Scheduled — every 30 seconds

***Purpose***: Sends queued emails, including account invitations, through the Mland SMTP Mail Gateway.

***Processing steps***:

1. Find up to 10 due requests that are pending or scheduled for retry.
2. Claim each request and freeze its content.
3. For an invitation, get the setup link; cancel it if the invitation expired.
4. Send the email and record the attempt without personal data.
5. Mark the request Sent, or schedule a retry.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Temporary sending error | Retried after 1 minute, then after 5 minutes; at most 3 attempts. Invitations allow 2 attempts, 1 minute apart. |
| Permanent error (unknown recipient, authentication) | The request fails immediately with no retry. |
| Server stops during sending | At the next start-up the request is set to retry, or to Unknown Outcome if sending had started. |

:::

#### 5.3.24 Retry Failed Booking and Settlement Emails (JOB-24)

***Trigger***: Scheduled — every 5 minutes, first run 1 minute after start-up

***Purpose***: Retries booking and settlement emails whose first send failed.

***Processing steps***:

1. Find up to 50 failed emails from the last 24 hours with fewer than 2 attempts.
2. Claim each email so only one server sends it.
3. Rebuild the email and send it again.
4. Mark it Sent, Failed or Skipped.
5. Report a booking email that failed twice to JOB-04.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Send fails again | The email stays Failed; no further retry after 2 attempts or 24 hours. |
| Missing email content | The email is marked Failed or Skipped. |

:::

#### 5.3.25 Clean Up Old Notification Data (JOB-25)

***Trigger***: Scheduled — daily at 2:00 AM

***Purpose***: Removes expired setup credentials and personal data from notifications finished more than 90 days ago.

***Processing steps***:

1. Remove expired setup credentials from notification requests.
2. Find requests that ended (Sent, Failed or Cancelled) more than 90 days ago.
3. Keep a minimal audit record for each.
4. Replace the recipient and content data with a scrubbed marker and delete the stored email body.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Database error | The whole sweep rolls back and runs again the next night. |
| Nothing old enough | Job completes with no change. |

:::

#### 5.3.26 Delete AI Chat Data and Temporary Design Images (JOB-26)

***Trigger***: Scheduled — daily at 2:00 AM

***Purpose***: Deletes Basic AI Support text after 30 days and removes temporary design reference images.

***Processing steps***:

1. Delete AI chat records whose 30-day retention has ended.
2. Find temporary reference images not yet deleted.
3. Delete each file from storage and mark the image deleted.
4. Log the number of records and images removed.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| One image cannot be deleted | A warning is logged and the image is retried the next night. |
| Database error | The run rolls back and is repeated the next night. |

:::

#### 5.3.27 Delete Unused Catalogue Images (JOB-27)

***Trigger***: Scheduled — every 1 hour, first run 10 minutes after start-up

***Purpose***: Deletes catalogue images that no product uses.

***Processing steps***:

1. Find up to 500 images never attached for 24 hours or unused for 30 days.
2. Lock each image and confirm no product still uses it.
3. Mark it for deletion and record it in the audit log.
4. Delete it from cloud storage and mark it Deleted.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Cloud storage unavailable | The image stays marked for deletion and is retried on the next run. |
| Other error | The run stops; remaining images are processed on the next run. |

:::

#### 5.3.28 Re-check Unavailable Catalogue Images (JOB-28)

***Trigger***: Scheduled — every 30 minutes, first run 5 minutes after start-up

***Purpose***: Restores catalogue images that can be read again from cloud storage.

***Processing steps***:

1. Find up to 100 images marked Unavailable.
2. Check whether cloud storage can read each image.
3. Set a readable image to Ready and record the change in the audit log.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Cloud storage unavailable | The image is left unchanged and checked again on the next run. |
| No unavailable image | Job completes immediately with no action. |

:::

#### 5.3.29 Clean Up Expired Product Imports (JOB-29)

***Trigger***: Scheduled — every 5 minutes, first run 1 minute after start-up

***Purpose***: Removes uploaded rows and errors of expired product import batches; batch summary and audit are kept.

***Processing steps***:

1. Find expired import batches, 100 at a time.
2. Set a validated, unconfirmed batch to Expired.
3. Clear the uploaded data and error list.
4. Repeat until no expired batch remains.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Error on a batch | The run stops; batches already cleaned stay cleaned and the rest are processed on the next run. |
| Nothing expired | Job completes immediately with no action. |

:::

#### 5.3.30 Expire Guest Design Sessions (JOB-30)

***Trigger***: Scheduled — every 5 minutes

***Purpose***: Ends guest ring design sessions whose lifetime has passed.

***Processing steps***:

1. Set every active guest design session past its expiry time to Expired.
2. Log the number of sessions expired.

***Failure behavior***:

::: {.docx-table width="100%" columns="28%,72%" repeat-header="false"}

| Scenario | System behavior |
| --- | --- |
| Database error | The update rolls back and is repeated on the next run. |
| No expired session | Job completes immediately with no action. |

:::
