# II. Implementation and Schedule

This part records how the tests were executed and what results were recorded in trackers 5.1–5.5. Results are reported exactly as recorded: a test case without execution evidence is counted as *Untested* (unit) or *Pending* (integration and system), not as passed. Execution dates of unit and integration tests are taken from the Maven Surefire report files, which do not store a run timestamp; they are therefore accurate to the day of the report file.

## 1. Test Environment

| Purpose | Tool | Provider | Version |
| --- | --- | --- | --- |
| Manage test cases and test reports | Microsoft Excel, Google Sheets | Microsoft, Google | Latest |
| Write test documentation | Google Docs, Markdown bundle generator | Google, in-house | Latest |
| Run backend unit and integration tests | JDK 21, Maven Wrapper, JUnit 5, Spring Boot Test | Eclipse Adoptium, Apache, VMware | JDK 21, Spring Boot 3.4.1 |
| Test database | MySQL Server | Oracle | 8.x |
| Run frontend tests | Node.js, Vitest | OpenJS Foundation, open source | Node.js 24, Vitest 4.1 |
| Run the web application for system testing | Spring Boot backend, React 19 + Vite 7 frontend | In-house | Project build |
| Payment and shipping integration | VNPay sandbox / payment simulator, GHTK test API | VNPay, GHTK | Sandbox |
| Execute system and acceptance tests | Google Chrome, Browser MCP, Chrome DevTools | Google, open source | Latest |
| IDE | IntelliJ IDEA, Visual Studio Code | JetBrains, Microsoft | Latest |

## 2. Test Milestones

The dates below are the first and last execution dates recorded in each tracker.

| Milestone Task | Start Date | End Date | Status |
| --- | --- | --- | --- |
| Unit Testing | 20/11/2026 | 25/11/2026 | Executed (538 of 538 test cases passed) |
| Integration Testing | 09/11/2026 | 25/11/2026 | Executed (266 of 293 test cases passed) |
| System Testing — Functional | 03/11/2026 | 25/11/2026 | Executed (94 of 125 test cases passed) |
| System Testing — Non-functional | 05/11/2026 | 25/11/2026 | Executed (24 of 27 test cases passed, 2 failed, 1 planned) |
| Acceptance Testing | 04/11/2026 | 25/11/2026 | Executed (14 of 22 UAT scripts passed, 8 pending; 7 exploratory sessions) |

## 3. Test Cases

### 3.1 Unit Test

Unit testing validated backend service functions in isolation for features 001, 002, 003, 004, 006, 007, 008, 010, 011, 012 and 013. Each function sheet lists its conditions, confirmations and one column per test case, mapped to a JUnit test method.

- Document: Report5.1_Unit Test_PPSWBS
- Functions tested: 116
- Total Test Cases: 538
- Coverage: 100.00%
- Successful Coverage: 100.00%

### 3.2 Integration Test

Integration testing checked REST controllers with the security layer, services with the MySQL database, and adapters for VNPay, GHTK, OTA and mail.

- Document: Report5.2_Integration Test_PPSWBS
- Modules: 22
- Total Test Cases: 293
- Coverage: 98.29%
- Successful Coverage: 98.29%

### 3.3 System Test — Functional Requirements

System testing validated end-to-end business workflows in a browser against the local deployment.

- Document: Report5.3_System Test_PPSWBS
- Workflows: 20
- Total Test Cases: 125
- Coverage: 77.60%
- Successful Coverage: 77.60%

### 3.4 System Test — Non-functional Requirements

Non-functional test cases cover security (role boundaries, ownership isolation, Staff location and branch scope, admin route guard, payment callback integrity, reporting access, log protection, UTF-8 integrity, idempotency, rate limiting, image upload security, audit completeness) and performance (response time, image upload, configurator rendering, concurrency, cross-feature wait limits and responsive layout).

- Document: Report-5.4_System Test_NFRs
- Total Test Cases: 27 (18 security, 9 performance)
- Coverage: 96.30%
- Successful Coverage: 96.30%

### 3.5 Acceptance Test

Acceptance test scripts describe the business flows of every feature in business language: accounts, products, promotions and loyalty, workshop booking, operations, settlement and OTA, ring design, custom design request, reviews, consultation, notifications, retail and pickup, and the manager dashboard. A script is Passed when its business flow passed end to end in the referenced execution, and Pending when the flow has steps without execution evidence. Exploratory sessions record free-form observations per business area.

- Document: Report-5.5_Acceptance Test Scripts
- Total Scripts: 22 UAT scripts, 7 exploratory sessions
- Coverage: 63.64%
- Successful Coverage: 63.64%

## 4. Test Reports

### 4.1 Unit Test Report

**UNIT TEST REPORT**

| Item | Value | Item | Value |
| --- | --- | --- | --- |
| Project Name | Personalized Product Sales and Workshop Booking System | Creator | SonPNT, QuocHH, KhuongDT, ThinhBT, DatNT |
| Project Code | PPSWBS | Reviewer |  |
| Document Code | Report5.1_Unit Test_PPSWBS | Issue Date | 25/11/2026 |

- Test coverage: 100.00%
- Test successful coverage: 100.00%
- Normal case: 38.29%
- Abnormal case: 44.24%
- Boundary case: 17.47%

| No | Function code | Passed | Failed | Untested | N | A | B | Total Test Cases |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | ACCOUNT_NORMALIZEPHONENUMBER | 6 | 0 | 0 | 2 | 3 | 1 | 6 |
| 2 | ACCOUNT_CREATEADDRESS | 2 | 0 | 0 | 1 | 0 | 1 | 2 |
| 3 | ACCOUNT_DELETEADDRESS | 2 | 0 | 0 | 0 | 1 | 1 | 2 |
| 4 | ACCOUNT_FINDELIGIBLEROSTER | 2 | 0 | 0 | 1 | 1 | 0 | 2 |
| 5 | ACCOUNT_RESOLVEACTOR | 1 | 0 | 0 | 0 | 0 | 1 | 1 |
| 6 | ACCOUNT_LISTACTIVEMANAGEREMAILS | 4 | 0 | 0 | 1 | 2 | 1 | 4 |
| 7 | ACCOUNT_SHOULDNOTFILTER | 8 | 0 | 0 | 2 | 4 | 2 | 8 |
| 8 | PRODUCT_CREATEDRAFT | 5 | 0 | 0 | 2 | 3 | 0 | 5 |
| 9 | PRODUCT_LISTMANAGEMENT | 8 | 0 | 0 | 5 | 2 | 1 | 8 |
| 10 | PRODUCT_LISTCATEGORIES | 7 | 0 | 0 | 5 | 0 | 2 | 7 |
| 11 | PRODUCT_EXECUTECOMMAND | 8 | 0 | 0 | 2 | 6 | 0 | 8 |
| 12 | PRODUCT_PARSEIMPORTFILE | 8 | 0 | 0 | 0 | 6 | 2 | 8 |
| 13 | PRODUCT_REQUIREBRANCHSCOPE | 7 | 0 | 0 | 2 | 3 | 2 | 7 |
| 14 | PRODUCT_STOREMEDIA | 6 | 0 | 0 | 2 | 4 | 0 | 6 |
| 15 | PRODUCT_PUBLICAVAILABILITY | 8 | 0 | 0 | 3 | 2 | 3 | 8 |
| 16 | LOYALTY_PROGRAMDISCOUNT | 7 | 0 | 0 | 1 | 2 | 4 | 7 |
| 17 | LOYALTY_POINTSLIMIT | 6 | 0 | 0 | 2 | 2 | 2 | 6 |
| 18 | LOYALTY_EARNEDPOINTS | 5 | 0 | 0 | 3 | 1 | 1 | 5 |
| 19 | LOYALTY_RETAILQUOTE | 8 | 0 | 0 | 4 | 3 | 1 | 8 |
| 20 | LOYALTY_WORKSHOPQUOTE | 8 | 0 | 0 | 3 | 2 | 3 | 8 |
| 21 | LOYALTY_ALLOCATEPARTS | 2 | 0 | 0 | 1 | 0 | 1 | 2 |
| 22 | LOYALTY_RELEASEHELD | 5 | 0 | 0 | 2 | 3 | 0 | 5 |
| 23 | LOYALTY_RESTORECANCELLED | 8 | 0 | 0 | 5 | 3 | 0 | 8 |
| 24 | LOYALTY_TICKETVOUCHERSCOPE | 8 | 0 | 0 | 6 | 1 | 1 | 8 |
| 25 | LOYALTY_CLAIMVOUCHER | 4 | 0 | 0 | 1 | 2 | 1 | 4 |
| 26 | WSBOOK_ALLOCATE | 8 | 0 | 0 | 2 | 2 | 4 | 8 |
| 27 | WSBOOK_ALLOCATESHARES | 2 | 0 | 0 | 1 | 1 | 0 | 2 |
| 28 | WSBOOK_PAYINITIATE | 8 | 0 | 0 | 3 | 3 | 2 | 8 |
| 29 | WSBOOK_CONFIRMPAID | 8 | 0 | 0 | 4 | 3 | 1 | 8 |
| 30 | WSBOOK_CONFIRMINTERNAL | 6 | 0 | 0 | 4 | 1 | 1 | 6 |
| 31 | WSBOOK_RELEASEEXPIRED | 5 | 0 | 0 | 3 | 1 | 1 | 5 |
| 32 | WSBOOK_LOOKUPGUEST | 4 | 0 | 0 | 2 | 1 | 1 | 4 |
| 33 | WSBOOK_LATERECEIVE | 7 | 0 | 0 | 2 | 5 | 0 | 7 |
| 34 | WSBOOK_STAFFSCOPE | 6 | 0 | 0 | 2 | 4 | 0 | 6 |
| 35 | WSBOOK_PROJECT | 8 | 0 | 0 | 5 | 2 | 1 | 8 |
| 36 | WSOPS_CHECKIN | 4 | 0 | 0 | 1 | 2 | 1 | 4 |
| 37 | WSOPS_RESERVEGROUP | 8 | 0 | 0 | 1 | 5 | 2 | 8 |
| 38 | WSOPS_RESERVEBOOKING | 2 | 0 | 0 | 1 | 1 | 0 | 2 |
| 39 | WSOPS_SETAVAIL | 5 | 0 | 0 | 2 | 2 | 1 | 5 |
| 40 | WSOPS_CHANGEOPTIONS | 8 | 0 | 0 | 3 | 2 | 3 | 8 |
| 41 | WSOPS_ASSIGNSTAFF | 8 | 0 | 0 | 1 | 6 | 1 | 8 |
| 42 | WSOPS_REMOVESTAFF | 7 | 0 | 0 | 3 | 2 | 2 | 7 |
| 43 | WSOPS_UPDATESLOT | 5 | 0 | 0 | 2 | 2 | 1 | 5 |
| 44 | WSOPS_REPLACEMENT | 7 | 0 | 0 | 3 | 2 | 2 | 7 |
| 45 | WSOPS_UPDATELOCATION | 5 | 0 | 0 | 1 | 4 | 0 | 5 |
| 46 | SETTLE_CHANGETICKETSESSIONS | 8 | 0 | 0 | 3 | 4 | 1 | 8 |
| 47 | SETTLE_GRANTEXTRAALLOWANCE | 2 | 0 | 0 | 1 | 1 | 0 | 2 |
| 48 | SETTLE_CHANGEGROUPTICKETS | 6 | 0 | 0 | 3 | 3 | 0 | 6 |
| 49 | SETTLE_COLLECTCASH | 7 | 0 | 0 | 1 | 4 | 2 | 7 |
| 50 | SETTLE_RECORDGATEWAYATTEMPT | 3 | 0 | 0 | 2 | 0 | 1 | 3 |
| 51 | SETTLE_RECORDBANKTRANSFER | 2 | 0 | 0 | 0 | 2 | 0 | 2 |
| 52 | SETTLE_CASHREVERSAL | 8 | 0 | 0 | 1 | 7 | 0 | 8 |
| 53 | SETTLE_ABSORBUNDERCHARGE | 5 | 0 | 0 | 1 | 3 | 1 | 5 |
| 54 | SETTLE_VOIDUNDERCHARGE | 3 | 0 | 0 | 2 | 1 | 0 | 3 |
| 55 | SETTLE_ADDVARIABLECHARGE | 3 | 0 | 0 | 1 | 1 | 1 | 3 |
| 56 | SETTLE_OPENBANKREFUND | 3 | 0 | 0 | 1 | 1 | 1 | 3 |
| 57 | SETTLE_SUBMITREFUNDACCOUNT | 6 | 0 | 0 | 2 | 4 | 0 | 6 |
| 58 | OTA_RECEIVEINBOUNDEVENT | 5 | 0 | 0 | 3 | 2 | 0 | 5 |
| 59 | OTA_EVALUATEREADINESS | 7 | 0 | 0 | 1 | 4 | 2 | 7 |
| 60 | OTA_TICKETDELIVERYGATE | 4 | 0 | 0 | 1 | 3 | 0 | 4 |
| 61 | OTA_RECORDVOUCHERINCIDENT | 8 | 0 | 0 | 2 | 6 | 0 | 8 |
| 62 | OTA_ACCEPTSNAPSHOT | 4 | 0 | 0 | 1 | 2 | 1 | 4 |
| 63 | OTA_RECORDDELIVERYRESULT | 3 | 0 | 0 | 1 | 2 | 0 | 3 |
| 64 | OTA_RESOLVEWORKITEM | 3 | 0 | 0 | 2 | 1 | 0 | 3 |
| 65 | OTA_REQUESTSYNC | 2 | 0 | 0 | 2 | 0 | 0 | 2 |
| 66 | OTA_STARTADDONPAYMENT | 5 | 0 | 0 | 3 | 2 | 0 | 5 |
| 67 | OTA_CREATEADDONREFUND | 3 | 0 | 0 | 1 | 1 | 1 | 3 |
| 68 | PAYMENT_RECEIVEIPN | 8 | 0 | 0 | 2 | 5 | 1 | 8 |
| 69 | CUSTOMORDER_STARTFULLPAYMENT | 5 | 0 | 0 | 1 | 3 | 1 | 5 |
| 70 | CUSTOMORDER_APPLYVERIFIEDFULLPAYMENT | 4 | 0 | 0 | 1 | 2 | 1 | 4 |
| 71 | CUSTOMORDER_UPDATESTATUS | 2 | 0 | 0 | 1 | 1 | 0 | 2 |
| 72 | CUSTOMORDER_LISTFOROPERATIONS | 2 | 0 | 0 | 2 | 0 | 0 | 2 |
| 73 | RINGDESIGN_RESOLVEGUESTSESSION | 3 | 0 | 0 | 1 | 1 | 1 | 3 |
| 74 | RINGDESIGN_CREATEDRAFT | 2 | 0 | 0 | 1 | 1 | 0 | 2 |
| 75 | RINGDESIGN_SAVEVERSION | 3 | 0 | 0 | 1 | 1 | 1 | 3 |
| 76 | RINGDESIGN_ATTACHTOBOOKING | 2 | 0 | 0 | 1 | 1 | 0 | 2 |
| 77 | RINGDESIGN_VALIDATECOMPATIBILITY | 5 | 0 | 0 | 1 | 1 | 3 | 5 |
| 78 | RINGDESIGN_EVALUATE | 2 | 0 | 0 | 2 | 0 | 0 | 2 |
| 79 | CONSULT_CREATECONVERSATION | 2 | 0 | 0 | 1 | 1 | 0 | 2 |
| 80 | CONSULT_SENDMESSAGE | 1 | 0 | 0 | 0 | 0 | 1 | 1 |
| 81 | CONSULT_CLAIM | 1 | 0 | 0 | 0 | 1 | 0 | 1 |
| 82 | CONSULT_ASSIGN | 1 | 0 | 0 | 1 | 0 | 0 | 1 |
| 83 | CONSULT_RATELIMIT | 1 | 0 | 0 | 0 | 0 | 1 | 1 |
| 84 | NOTIF_SUBMITINTENT | 5 | 0 | 0 | 2 | 3 | 0 | 5 |
| 85 | NOTIF_DELIVERSINGLE | 5 | 0 | 0 | 1 | 2 | 2 | 5 |
| 86 | NOTIF_CREATEDRAFT | 3 | 0 | 0 | 1 | 2 | 0 | 3 |
| 87 | NOTIF_RESEND | 4 | 0 | 0 | 2 | 2 | 0 | 4 |
| 88 | NOTIF_CANCEL | 2 | 0 | 0 | 1 | 1 | 0 | 2 |
| 89 | NOTIF_REDELIVER | 2 | 0 | 0 | 2 | 0 | 0 | 2 |
| 90 | NOTIF_RENDITION | 2 | 0 | 0 | 2 | 0 | 0 | 2 |
| 91 | NOTIF_BOOKINGDELIVER | 6 | 0 | 0 | 1 | 5 | 0 | 6 |
| 92 | NOTIF_RETRYFAILED | 5 | 0 | 0 | 1 | 3 | 1 | 5 |
| 93 | REVIEW_CREATE | 7 | 0 | 0 | 4 | 2 | 1 | 7 |
| 94 | REVIEW_EDIT | 3 | 0 | 0 | 1 | 2 | 0 | 3 |
| 95 | REVIEW_MODERATE | 3 | 0 | 0 | 2 | 1 | 0 | 3 |
| 96 | REVIEW_REPLY | 2 | 0 | 0 | 1 | 1 | 0 | 2 |
| 97 | REVIEW_GUESTHANDLE | 2 | 0 | 0 | 0 | 2 | 0 | 2 |
| 98 | REVIEW_PUBLICLIST | 6 | 0 | 0 | 4 | 1 | 1 | 6 |
| 99 | RETAIL_ADDCARTLINE | 6 | 0 | 0 | 2 | 2 | 2 | 6 |
| 100 | RETAIL_UPDATECARTLINE | 2 | 0 | 0 | 1 | 1 | 0 | 2 |
| 101 | RETAIL_VALIDATEQUOTE | 6 | 0 | 0 | 2 | 3 | 1 | 6 |
| 102 | RETAIL_PREVIEWQUOTE | 8 | 0 | 0 | 4 | 3 | 1 | 8 |
| 103 | RETAIL_TRANSITIONSHIPMENT | 8 | 0 | 0 | 2 | 5 | 1 | 8 |
| 104 | RETAIL_LISTSHIPMENTS | 2 | 0 | 0 | 1 | 1 | 0 | 2 |
| 105 | RETAIL_CREATESHIPMENTS | 3 | 0 | 0 | 1 | 1 | 1 | 3 |
| 106 | RETAIL_CANCELCHECKOUT | 3 | 0 | 0 | 1 | 2 | 0 | 3 |
| 107 | RETAIL_RECONCILEPAYMENT | 4 | 0 | 0 | 2 | 1 | 1 | 4 |
| 108 | RETAIL_APPLYPAYMENT | 2 | 0 | 0 | 0 | 1 | 1 | 2 |
| 109 | RETAIL_SPLITDISCOUNT | 4 | 0 | 0 | 2 | 1 | 1 | 4 |
| 110 | RETAIL_SELECTCARTLINES | 3 | 0 | 0 | 2 | 1 | 0 | 3 |
| 111 | FULFIL_COLLECTCASH | 3 | 0 | 0 | 1 | 2 | 0 | 3 |
| 112 | DASHBOARD_EXECUTIVEREPORT | 6 | 0 | 0 | 2 | 3 | 1 | 6 |
| 113 | DASHBOARD_TREND | 4 | 0 | 0 | 2 | 1 | 1 | 4 |
| 114 | DASHBOARD_AUTHORIZE | 3 | 0 | 0 | 1 | 2 | 0 | 3 |
| 115 | DASHBOARD_RESOLVEPERIOD | 2 | 0 | 0 | 1 | 0 | 1 | 2 |
| 116 | DASHBOARD_TOPSELLERS | 4 | 0 | 0 | 1 | 2 | 1 | 4 |
|  | Sub total | 538 | 0 | 0 | 206 | 238 | 94 | 538 |

All 538 unit test cases passed. The 25 test cases that had no earlier Surefire result (for example `CustomOrderServiceTest` and `ExecutiveTopSellerServiceTest`) were executed on 25/11/2026 and passed.

### 4.2 Integration Test Report

**INTEGRATION TEST REPORT**

| Item | Value | Item | Value |
| --- | --- | --- | --- |
| Project Name | Personalized Product Sales and Workshop Booking System | Creator | SonPNT, QuocHH, KhuongDT, ThinhBT, DatNT |
| Project Code | PPSWBS | Reviewer |  |
| Document Code | Report5.2_Integration Test_PPSWBS | Issue Date | 25/11/2026 |

- Test coverage: 98.29%
- Test successful coverage: 98.29%

| No | Module code | Passed | Failed | Pending | N/A | Number of test cases |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Account bootstrap, identity gate and self-service profile | 9 | 0 | 0 | 0 | 9 |
| 2 | Member address book and internal account administration | 11 | 0 | 0 | 0 | 11 |
| 3 | 002 Product Management - review, price and publication | 10 | 0 | 0 | 0 | 10 |
| 4 | 002 Product Management - Excel import and public catalogue | 8 | 0 | 0 | 0 | 8 |
| 5 | 006 Loyalty & Promotion - promotion programmes | 11 | 0 | 0 | 0 | 11 |
| 6 | 006 Loyalty & Promotion - points policy, wallet and retail checkout | 9 | 0 | 0 | 0 | 9 |
| 7 | 003.1 Workshop Booking | 16 | 0 | 0 | 0 | 16 |
| 8 | 003.1 Workshop Booking | 14 | 0 | 0 | 0 | 14 |
| 9 | 003.2 Workshop Operations | 19 | 0 | 0 | 0 | 19 |
| 10 | 003.2 Workshop Operations | 16 | 0 | 0 | 0 | 16 |
| 11 | 003.3 Refund queue, folio collection and ticket change endpoints | 20 | 0 | 0 | 0 | 20 |
| 12 | 003.3 Cash reversal, cross-location change and refund account on MySQL | 14 | 0 | 0 | 0 | 14 |
| 13 | 003.4 OTA add-on quote and direct bill | 20 | 0 | 0 | 0 | 20 |
| 14 | 003.4 OTA inbound booking and cancellation | 19 | 0 | 0 | 0 | 19 |
| 15 | 004 AI support, design request, human review and price proposal APIs | 13 | 0 | 2 | 0 | 15 |
| 16 | 012 Workshop ring design REST API | 9 | 0 | 3 | 0 | 12 |
| 17 | 013 Consultation chat client-API contract | 6 | 0 | 0 | 0 | 6 |
| 18 | 007 Notification Management - facade, worker, templates, operations | 9 | 0 | 0 | 0 | 9 |
| 19 | 011 Review Management - REST controller | 8 | 0 | 0 | 0 | 8 |
| 20 | 008 Retail Order & Fulfillment - cart, checkout and holds | 20 | 0 | 0 | 0 | 20 |
| 21 | 008 Retail Order & Fulfillment - shipment and pickup | 15 | 0 | 0 | 0 | 15 |
| 22 | 010 Manager Dashboard | 12 | 0 | 0 | 0 | 12 |
|  | Sub total | 288 | 0 | 5 | 0 | 293 |

Notes on the recorded results:

- The 10 test cases that failed on 20/11/2026 and 23/11/2026 came from MySQL-backed integration tests whose Spring application context or test fixture failed (`GroupBookingDiscountMySqlIT`: application context failed to load; `RetailCatalogueMySqlIntegrationTest`: fixture conflict "Variant is not eligible for the selected location"). They were re-run on 26/11/2026 on a dedicated MySQL test database and passed (`GroupBookingDiscountMySqlIT` 6/6, `RetailCatalogueMySqlIntegrationTest` 32/32). WB_TC15 was renamed to match the current rule that a member voucher stacks on the automatic discount (`memberVoucherStacksOnTheAutomaticDiscount`).
- 007 Notification Management passed in Round 1 (09/11/2026 and 14/11/2026). Its Round 2 run on 20/11/2026 failed because the application context did not load (`mailHealthContributor`). After the test configuration disabled the mail health check, Round 3 on 26/11/2026 passed (`NotificationManagementIT` 9/9). Rounds 2 and 3 are recorded in the tracker and are not included in the Round 1 totals above.
- The 12 test cases of 010 Manager Dashboard were executed on 26/11/2026 with the new `ExecutiveReportApiTest` (19/19), `ExecutiveReportReadOnlyMySqlTest` (1/1), `ExecutiveTrendServiceTest` (4/4) and `ExecutiveTopSellerServiceTest` (4/4) and passed.
- The 5 pending test cases belong to ITC records of 004 (AI_TC14, AI_TC15) and 012 (RD_TC10 to RD_TC12). The new `CustomOrderIntegrationTest` and `WorkshopRingDesignIntegrationTest` passed on 26/11/2026, but they cover order listing, access and status transition, and design options, create and archive; they do not exercise the quote approval, full VNPay payment, guest expiry, branch pricing or booking-lock behaviour these cases describe. The 013 API-client tests and the MockMvc tests of 011 and 012 were executed on 25/11/2026 and passed.

### 4.3 System Test Report

**SYSTEM TEST REPORT**

| Item | Value | Item | Value |
| --- | --- | --- | --- |
| Project Name | Personalized Product Sales and Workshop Booking System | Creator | SonPNT, QuocHH, KhuongDT, ThinhBT, DatNT |
| Project Code | PPSWBS | Reviewer |  |
| Document Code | Report5.3_System Test_PPSWBS | Issue Date | 25/11/2026 |

- Test coverage: 77.60%
- Test successful coverage: 77.60%

| No | Module code | Passed | Failed | Pending | N/A | Number of test cases |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Member sign-in, profile, address book and sign-out | 8 | 0 | 0 | 0 | 8 |
| 2 | Admin provisioning and governance of internal and carrier accounts | 4 | 0 | 2 | 0 | 6 |
| 3 | Product creation, review and public catalogue | 8 | 0 | 0 | 0 | 8 |
| 4 | Excel import, categories and product deletion | 6 | 0 | 0 | 0 | 6 |
| 5 | Promotion setup and member vouchers | 7 | 0 | 0 | 0 | 7 |
| 6 | Benefits at retail checkout and workshop booking | 7 | 0 | 0 | 0 | 7 |
| 7 | Workshop discovery, booking hold and VNPay deposit | 5 | 0 | 1 | 0 | 6 |
| 8 | Backstage session operations and ticket check-in | 4 | 0 | 1 | 0 | 5 |
| 9 | Ticket folio collection and finalization after check-in | 3 | 0 | 1 | 0 | 4 |
| 10 | Customer ticket session change and change-request queue | 0 | 0 | 7 | 0 | 7 |
| 11 | OTA provider booking, replay and cancellation | 0 | 0 | 4 | 0 | 4 |
| 12 | OTA add-on direct bill and payout reconciliation | 0 | 0 | 4 | 0 | 4 |
| 13 | Custom design request, human review and full payment | 8 | 0 | 0 | 0 | 8 |
| 14 | Workshop ring design configuration and save | 8 | 0 | 0 | 0 | 8 |
| 15 | Customer-staff consultation chat | 7 | 0 | 0 | 0 | 7 |
| 16 | Template lifecycle and delivery operations by back-office users | 8 | 0 | 0 | 0 | 8 |
| 17 | Customer review creation, moderation and manager reply | 6 | 0 | 0 | 0 | 6 |
| 18 | Retail online order with VNPay and GHTK handover | 5 | 0 | 1 | 0 | 6 |
| 19 | Multi-branch store pickup with cash at counter | 3 | 0 | 2 | 0 | 5 |
| 20 | Manager reviews executive revenue dashboard | 0 | 0 | 5 | 0 | 5 |
|  | Sub total | 97 | 0 | 28 | 0 | 125 |

Only Round 1 has been executed; Rounds 2 and 3 are recorded as N/A. The ring design workflow case that failed in the JOURNEY-PHASE-4 run on 17/11/2026 (ST_RD_04) passed in the re-run on 25/11/2026 after BUG-E2E-0016 and BUG-E2E-0017 were closed, and the two Staff close-conversation cases of consultation chat (ST_CC_06, ST_CC_07) were executed on 25/11/2026 and passed. The 28 pending test cases have no browser execution record yet; they include the ticket change flow (blocked by an open integration defect), the OTA workflows (verified only by frontend component tests) and the Manager Dashboard.

### 4.4 Non-functional Test Report

**NON-FUNCTIONAL TEST REPORT**

| Item | Value | Item | Value |
| --- | --- | --- | --- |
| Project Name | Personalized Product Sales and Workshop Booking System | Creator | SonPNT, QuocHH, KhuongDT, ThinhBT, DatNT |
| Project Code | PPSWBS | Reviewer |  |
| Document Code | Report-5.4_System Test_NFRs | Issue Date | 25/11/2026 |

- Test coverage: 96.30%
- Test successful coverage: 96.30%

| No | Test category | Passed | Failed | Planned / TBD | Number of test cases |
| --- | --- | --- | --- | --- | --- |
| 1 | Security | 18 | 0 | 0 | 18 |
| 2 | Performance | 8 | 0 | 1 | 9 |
|  | Sub total | 26 | 0 | 1 | 27 |

**Security test cases**

| Test Case ID | Scenario | Role / Session | Priority | Status | Defect ID |
| --- | --- | --- | --- | --- | --- |
| ST-NFR-SEC-001 | Admin cannot mutate business invoice | Admin session | High | Passed |  |
| ST-NFR-SEC-002 | Guest protected-action boundary | Guest session | High | Passed |  |
| ST-NFR-SEC-003 | Member ownership isolation | Member session | High | Passed |  |
| ST-NFR-SEC-004 | Staff location scope | Staff session | High | Passed |  |
| ST-NFR-SEC-005 | Manager global authority | Manager session | High | Passed |  |
| ST-NFR-SEC-006 | PII and secret log protection | Any protected request | High | Passed |  |
| ST-NFR-SEC-007 | Vietnamese UTF-8 integrity | Member/Staff/Manager sessions | High | Passed |  |
| ST-NFR-SEC-008 | Idempotency and replay protection | Authenticated Member | High | Passed | BUG-E2E-0019 (closed) |
| ST-NFR-SEC-009 | Image upload security | Member session | High | Passed |  |
| ST-NFR-SEC-010 | Audit completeness | Staff/Manager session | Medium | Passed |  |
| ST-NFR-SEC-011 | Admin-only screens blocked for other roles | Member session | High | Passed | BUG-E2E-0001 (closed) |
| ST-NFR-SEC-012 | Guest blocked from Feature-004 request creation | Guest session | High | Passed |  |
| ST-NFR-SEC-013 | Payment callback integrity | Payment gateway callback | High | Passed |  |
| ST-NFR-SEC-014 | Staff branch scope on catalogue stock | Staff/Manager session | High | Passed |  |
| ST-NFR-SEC-015 | Workshop group operation scope | Staff/Manager/Admin/Member | High | Passed |  |
| ST-NFR-SEC-016 | Manager reporting access | Anonymous/Staff/Manager | Medium | Passed |  |
| ST-NFR-SEC-017 | Image submission rate limit | Authenticated Member | Medium | Passed |  |
| ST-NFR-SEC-018 | Write command without Idempotency-Key | Authenticated Member | Medium | Passed |  |

**Performance test cases**

| Test Case ID | Scenario | Target | Actual | Status | Defect ID |
| --- | --- | --- | --- | --- | --- |
| ST-NFR-PERF-001 | Booking availability response under approved load | < 2s under 100 concurrent users | TBD | Planned / TBD |  |
| ST-NFR-PERF-002 | Member design save/evaluate response | < 3s | 2.88s - 2.90s | Passed |  |
| ST-NFR-PERF-003 | Review queue and consultation sidebar load | < 3s without layout shift | 2.02s (scroll top preserved) | Passed |  |
| ST-NFR-PERF-004 | Concurrent review/payment/chat commands | No duplicate business mutation | 1 transaction recorded | Passed |  |
| ST-NFR-PERF-005 | Cross-feature wait limit | No transition > 30s | All steps 2.02s - 26.64s <= 30s | Passed |  |
| ST-NFR-PERF-006 | Custom design request page load | < 3s | DOMContentLoaded 2.07s; load 2.08s | Passed |  |
| ST-NFR-PERF-007 | Upload and quality-check 5 images | Per-image feedback, no duplicate requests | Quality labels for 5 images within 3.0s; one POST on submit (202) | Passed |  |
| ST-NFR-PERF-008 | Ring configurator rapid changes | All items rendered; limits kept | 9 rapid add clicks: total capped at 10/10; preview shows 10 stones | Passed |  |
| ST-NFR-PERF-009 | Responsive public screens on mobile | No horizontal overflow at 360px and 375px | 10 of 10 public pages fit; /reviews scrollWidth equals the viewport width | Passed | BUG-E2E-0020 (closed) |

Notes on the recorded results:

- ST-NFR-SEC-008 failed in the re-run on 25/11/2026 because a changed payload with the same Idempotency-Key was not reported as a conflict (BUG-E2E-0019). After the fix, it was re-run on 26/11/2026 with `DesignRequestIT` (13/13), including the case that expects 409 for a changed payload, and passed.
- ST-NFR-PERF-009 failed on 25/11/2026 because the public reviews page scrolled horizontally on 360px and 375px viewports (BUG-E2E-0020). After the stylesheet fix, it was re-run on 26/11/2026 in browser device emulation and the page no longer scrolls horizontally at either width.
- ST-NFR-PERF-001 stays planned until a load-test harness for 100 concurrent users is available.

### 4.5 Acceptance Test Report

**ACCEPTANCE TEST REPORT**

| Item | Value | Item | Value |
| --- | --- | --- | --- |
| Project Name | Personalized Product Sales and Workshop Booking System | Creator | SonPNT, QuocHH, KhuongDT, ThinhBT, DatNT |
| Project Code | PPSWBS | Reviewer |  |
| Document Code | Report-5.5_Acceptance Test Scripts | Issue Date | 25/11/2026 |

- Test coverage: 63.64%
- Test successful coverage: 63.64%

| Script ID | BF-ID | Title | Actor / Role | Priority | Result |
| --- | --- | --- | --- | --- | --- |
| UAT-BOOK-001 | BF-BOOK | Guest workshop booking | Guest | High | Passed |
| UAT-RET-001 | BF-RETAIL | Ready-ring retail | Member | High | Passed |
| UAT-DES-001 | BF-DESIGN | Member ring design and recommendation | Member | High | Passed |
| UAT-DES-002 | BF-CUSTOM | Custom design request | Member | High | Passed |
| UAT-DES-003 | BF-CUSTOM-CLARIFY | Clarification and custom order | Member/Staff/Manager | High | Passed |
| UAT-REV-001 | BF-REVIEW | Public review and Member review | Guest/Member | Medium | Passed |
| UAT-CHAT-001 | BF-CONSULTATION | Design consultation | Member/Staff/Manager | High | Passed |
| UAT-OPS-001 | BF-OPS | Operational role boundary | Staff/Manager/Admin | High | Passed |
| UAT-ACC-001 | BF-ACCOUNT | Member account self-service | Member | High | Passed |
| UAT-ACC-002 | BF-ACCOUNT-ADMIN | Internal and carrier account administration | Admin | High | Pending |
| UAT-PRD-001 | BF-PRODUCT | Product publication | Staff/Manager/Member | High | Passed |
| UAT-PRD-002 | BF-PRODUCT-IMPORT | Excel import and catalogue upkeep | Manager | Medium | Passed |
| UAT-LOY-001 | BF-PROMOTION | Promotion programmes and vouchers | Manager/Member | High | Passed |
| UAT-LOY-002 | BF-LOYALTY | Benefits at checkout and booking | Member | High | Passed |
| UAT-WOP-001 | BF-WORKSHOP-OPS | Backstage session and ticket check-in | Staff | High | Pending |
| UAT-SET-001 | BF-SETTLEMENT | Folio collection after check-in | Staff | High | Pending |
| UAT-SET-002 | BF-TICKET-CHANGE | Ticket session change | Member/Staff/Manager | Medium | Pending |
| UAT-OTA-001 | BF-OTA | OTA booking, replay and cancellation | OTA provider/Staff | Medium | Pending |
| UAT-OTA-002 | BF-OTA-ADDON | OTA add-on direct bill | Staff/OTA customer | Medium | Pending |
| UAT-NOT-001 | BF-NOTIFICATION | Notification templates and delivery | Manager/Admin | Medium | Passed |
| UAT-RET-002 | BF-PICKUP | Multi-branch store pickup with cash | Member/Staff | High | Pending |
| UAT-DASH-001 | BF-DASHBOARD | Executive revenue dashboard | Manager | Medium | Pending |
|  |  | Sub total |  |  | 14 Passed / 0 Failed / 8 Pending |

The 8 pending scripts depend on workflows that are not fully executed in tracker 5.3: account administration, workshop check-in window, folio collection, ticket session change (blocked by BUG-INT-0004), OTA booking and add-on billing, multi-branch pickup and the manager dashboard.

**Exploratory sessions**

| Date | Tester | Area explored | Observation | Severity | Defect ID |
| --- | --- | --- | --- | --- | --- |
| 25/11/2026 | Ngô Tiến Đạt | UX-UTF8 / all bilingual screens | Vietnamese/English switching works seamlessly; no mojibake; fonts render correctly. | Pass |  |
| 25/11/2026 | Ngô Tiến Đạt | BF-DESIGN / Feature-012 | Preview restoration works; 10 gemstone limit enforced; difficulty calculation triggers correct package. | Pass |  |
| 25/11/2026 | Ngô Tiến Đạt | BF-CUSTOM / Feature-004 | Mandatory image consent enforced; blur warning displayed; clarification loop preserves revision. | Pass |  |
| 25/11/2026 | Ngô Tiến Đạt | BF-CONSULTATION / Feature-013 | Stable chat width; initial scroll at top; polling does not jitter viewport; post-close disabled. | Pass |  |
| 25/11/2026 | Ngô Tiến Đạt | BF-OPS / Staff-Manager | Staff/Manager custom orders tabs display 2 orders each; radio transitions open confirmation popup. | Pass |  |
| 25/11/2026 | Ngô Tiến Đạt | BF-PAYMENT / VNPay | VNPay Sandbox callback transitions order to PAID without duplicate creation; full payment only. | Pass |  |
| 25/11/2026 | KhuongDT (Claude-assisted) | BF-REVIEW / Public reviews on mobile (360-375px) | Public /reviews page scrolls horizontally on 360px and 375px viewports (scrollWidth 385px, caused by .review-container); other public pages … | Minor | BUG-E2E-0020 |
