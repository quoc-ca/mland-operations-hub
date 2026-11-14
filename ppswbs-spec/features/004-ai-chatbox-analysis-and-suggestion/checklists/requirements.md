# Requirements Checklist: Independent Feature-04

- [ ] Guest Design Request is denied; only authenticated Member ownership is supported.
- [ ] Description-only, image-only and combined request policy is explicit.
- [ ] Human-review behavior is taken from the backend response; the frontend does not invent a review path.
- [ ] Withdrawal/revision commands are not exposed by the current frontend contract.
- [ ] Independent Design Request has no booking/payment/location/session/ticket fields.
- [ ] No Feature-04 code imports or calls Workshop Booking/Operations/Settlement.
- [ ] Account ownership is server-verified and cross-owner access is denied.
- [ ] Image consent is affirmative, versioned and recorded before storage/provider call.
- [ ] Input validation, prompt injection screening and provider output policy validation are explicit.
- [ ] Image limit is 10 MB and 4096×4096; image rate limit is three attempts per five minutes.
- [ ] Duplicate idempotent retries do not consume another image quota slot.
- [ ] Temporary image deletion is required; the frontend does not retain raw image content.
- [ ] AI cannot approve, reject, publish a price or mutate business state.
- [ ] Staff/Manager review scope and separation of duties are explicit.
- [ ] Price suggestion is advisory and Manager-controlled; Admin is technical/audit only.
- [ ] OpenAPI, data model, design, tasks and tests use the independent boundary.
