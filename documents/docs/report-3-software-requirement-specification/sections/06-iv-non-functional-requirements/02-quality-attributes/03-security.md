### 2.3 Security

These requirements protect identity, authority, payment evidence and purpose-limited data, based on Report 2 constraints and Report 3 account/product/order use cases. Missing policy values remain NFR-OPEN-05/06.

#### 2.3.1 Authentication and authorization

| ID | Requirement | Verification criterion |
| --- | --- | --- |
| NFR-SEC-01 | Protected access shall require verified identity and a currently active account. Resolve status/permissions from Mland records, not client/provider role assertions. | Invalid/expired tokens and suspended accounts grant no protected access. Registration creates Member only; sign-in cannot elevate role. Lock/role/RBAC changes affect subsequent checks, including existing sessions. See UC1/UC2 and UC8–UC10. |
| NFR-SEC-02 | Server authorization shall check action, ownership, branch, delegation and state on direct requests and at final mutation. | Member A cannot access Member B's private records. Staff cannot act outside branch; Manager title/price authority alone grants no delegated catalogue/retail action. Guest cannot submit booking images, retail checkout or custom orders. See UC5/UC6, UC11–UC14, UC43–UC53 and Permission Matrix. |
| NFR-SEC-03 | Admin support shall disclose only technical diagnostics and grant no customer impersonation or business mutation/approval. | UC48 permits internal order/attempt reference, branch code, timestamps, states and sanitized codes only. Exclude cart/product/options, customer identity/contact/address/notes and authentication data. No paid flag, product maintenance or retail handover authority; account/RBAC/configuration uses its own functions. |
| NFR-SEC-04 | Recovery shall use time-limited single-use tokens/links and non-disclosing unknown-account responses. Secure local credentials without retrievable plaintext; use provider recovery/change for managed credentials. | Reject invalid/expired/reused tokens; do not reveal account existence. Never email passwords to Staff/Admin or log them. Invalidate applicable sessions after changes under approved policy. See UC3/UC4; exact values remain NFR-OPEN-05. |
| NFR-SEC-05 | Booking lookup/QR/reference handling shall disclose permitted fields only; a reference/scan cannot authorize payment, attendance mutation or retail pickup. | UC19 omits QR for unconfirmed bookings; UC20 requires Staff verification. UC52 needs current signed-in owner and actual handover; screenshot/reference alone/third-party collection is insufficient. Extra Guest lookup protections remain NFR-OPEN-05. |

#### 2.3.2 Payment, credentials and input protection

| ID | Requirement | Verification criterion |
| --- | --- | --- |
| NFR-SEC-06 | Only valid signed IPN/eligible signed QueryDR shall authorize success. Verify signature, exact reference, frozen amount/currency, successful transaction status, original attempt/hold binding and duplicates before effects. | Test tampering, mismatches, unknown attempts, API success without transaction success, duplicates and late evidence. Browser returns/human paid claims grant no settlement authority. Signed callbacks need no live Member session and grant no human role. See Report 2 C-01, UC18/UC46 and timing/reliability criteria. |
| NFR-SEC-07 | Encrypt stored integration secrets, mask ordinary display and exclude logs/errors. Only authorized Admin may change/rotate approved credentials. | Inspect protected credential storage. UC55 audits integration/actor/outcome without secret values. Client responses, repository and diagnostics expose no signing secret, SMTP password, private API credential or token. Public provider keys need approved provider restrictions rather than server-secret treatment. |
| NFR-SEC-08 | Validate fields/files/provider responses before use. Protected fields and untrusted AI/user text shall not grant authority or execute as application instructions. | Reject unauthorized role/status/price/payment fields, unknown variants, invalid quantities and malformed callbacks. Render untrusted text safely. UC29/UC30 enforce configured length/type/size/quality/rate limits; missing values remain NFR-OPEN-05/06. |
| NFR-SEC-09 | Product media shall enforce shared limits and separate public assets/private references. | UC12/UC13: at most 10 images/product, 5 MB each, JPEG/PNG/WebP, distinct positions, alternative text 1–200 characters. Deny private-image access without authority. Reference removal cannot delete shared/history media. These are not automatically AI-input limits. |
| NFR-SEC-10 | External requests shall contain only purpose-required data. AI advice cannot replace human price/review authority. | UC29 sends permitted public support context; UC30 permitted booking-image context; UC31 configured catalogue pricing inputs. Exclude credentials, unrelated private orders and custom-manufacturing decision requests. Preserve normalized decision references without extending raw AI retention. |

Encrypted transport follows NFR-IF-11. Merchant-specific signature/status mappings and credential validation need sandbox/UAT evidence before operation; selecting VNPay is not proof of completed integration.

#### 2.3.3 Privacy and data retention

General Terms and Privacy Policy shall state image-processing purposes, these periods, Google attribution and recipient handling under Report 2 A-03. Respect required consent/policy acceptance before image transfer without adding a separate feature-specific checkbox. Report 2's explicit privacy decision governs presentation where older use-case wording implies a separate checkbox.

| ID | Data class | Required retention / disposal | Verification criterion |
| --- | --- | --- | --- |
| NFR-DAT-01 | Application Gemini text prompts/responses. | At most 30 days. | Age synthetic records and verify deletion from stores/applicable copies. Five-year audit does not preserve raw text. |
| NFR-DAT-02 | AI images, including booking analysis and independent classification. | None after processing; remove temporary input after success/failure. | Inspect temporary files/objects, logs and retry payloads. Keep permitted non-image routing/analysis evidence only. Application rules do not prove provider zero retention; terms/settings need go-live evidence. |
| NFR-DAT-03 | Custom-manufacturing reference images. | Until pickup/actual GHTK handoff only; delete rejected/withdrawn images immediately after processing. | Exercise lifecycle triggers and remove private objects/applicable copies. These references differ from transient AI input; AI cannot decide custom orders. |
| NFR-DAT-04 | GHTK recipient name/phone/address and applicable notes/copies. | Keep necessary data while handoff is pending; delete/irreversibly obscure 30 days after original actual handoff. Remove obsolete data when editable retail orders switch to pickup. | Test pending beyond 30 days, actual/duplicate handoff and retirement. Never reset original time. Details/history/logs/audit cannot expose retired values; keep minimized custody/payment evidence. See UC48/UC51/UC53. |
| NFR-DAT-05 | VNPay references and required business evidence. | Five years under Report 2 policy, with Finance/Legal confirmation before go-live. Never store card data. | Preserve traceability without card data, secrets, expired AI content or retired recipient data. Five years is not a blanket personal-data period. |
| NFR-DAT-06 | Member cart. | Expire 30 days after last Member edit; reads/automated processing do not reset it. | Boundary yields empty cart without changing accepted orders/holds/history. Paid cleanup removes unchanged purchased lines only; preserve later edits/additions. See UC44/UC46. |
| NFR-DAT-07 | Failed product uploads unlinked to catalogue. | Remove after 24 hours; exclude referenced/shared/history media from orphan cleanup. | Test failed upload/save and referenced-file cases in UC12/UC13. No partial catalogue group remains; legitimate references stay usable. |

Cleanup covers application stores, temporary objects and applicable copies. Record failures without private-content dumps; failure grants no retention extension. Daily jobs support enforcement but authorize no overdue disclosure. Backup/restore must respect retirement/expiry; schedules remain NFR-OPEN-02. Unspecified account/profile/booking-contact periods remain NFR-OPEN-06 rather than inheriting a default.

Places rating/review content cannot persist in logs/provider payloads under NFR-IF-06. Attribution and links do not authorize storage.

#### 2.3.4 Auditability and safe logging

| ID | Requirement | Verification criterion |
| --- | --- | --- |
| NFR-SEC-11 | Retain attributable outcomes for protected account/configuration changes, product maintenance/price approval/application/publication and retail actions/reads. Preserve payment/exception, design-review, custom-final-amount, check-in, pickup and handoff evidence. | Verify actual `SUCCESS`, `REJECTED` or `FAILED` outcomes, including no-change duplicates without repeated effects. Record known actor, action, safe target/reference, applicable branch, occurrence time, outcome/reason, correlation and permitted before/after context. Basis: Audit Log entity, UC1–UC14, UC18/UC20, UC43–UC55 and Report 2 retention. |
| NFR-SEC-12 | Business evidence shall be append-only in normal operations, protected from ordinary edit/delete while controlled expiry/privacy disposal remains enforceable. Sanitize diagnostics. | Attempt unauthorized mutation and inspect logs/errors for credentials, tokens, card data, private images and retired recipient values. Preserve normalized facts/original timestamps. Cleanup cannot erase required minimized evidence; audit cannot hide copies of shorter-lived data. |

Allowlist necessary before/after fields instead of dumping requests/accounts/recipients. Price approval binds exact proposal/variant/options/amount/currency, deciding actor and time. Pickup/handoff keeps minimized reference/verification/custody facts under UC52/UC53, without identity-document copies or pickup OTP. Logging implementation, alert thresholds and unrelated technical-log retention require approved operating policy.
