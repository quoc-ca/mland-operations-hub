# Quickstart Validation

1. Seed one published package and one configured session with capacity for two
   participants.
2. Browse packages and open details.
3. Create a valid Guest booking and verify a unique code,
   `PENDING_PAYMENT/UNPAID`, 50% deposit and a hold no longer than 15 minutes.
4. Start payment; the gateway test double handles QR/OTP.
5. Deliver one valid signed callback and verify exactly one transaction,
   `CONFIRMED/DEPOSIT_PAID`, QR ticket, notification intent and external handoff.
6. Look up the booking with the approved lookup proof.

Failure checks:

- invalid signature/amount/currency/reference never confirms or settles;
- duplicate callback creates no second effect;
- failed/cancelled attempt remains retryable before hold expiry;
- expiry releases the exact hold once and rejects late success;
- competing requests cannot oversell the last participant capacity;
- notification failure leaves booking confirmed and retryable;
- unauthorized Guest lookup returns a safe result without data disclosure.

From `ppswbs_backend`, implementation validation must include `mvn test`,
MySQL migration/integration evidence, payment adapter contract tests and Spring
Modulith boundary verification.
