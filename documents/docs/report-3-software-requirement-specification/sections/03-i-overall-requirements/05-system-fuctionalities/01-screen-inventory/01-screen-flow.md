#### a. Screen Flow

<!-- This part shows the system screens and the relationship among screens. -->

```mermaid
flowchart TD
    Home[Home Page]
    Packages[Workshop Package List]
    Package[Workshop Package Details]
    Location[Location and Session Selection]
    Booking[Booking Information Form]
    Path[Design Path Selection]
    Summary[Booking Summary]
    PaymentRedirect[Payment Gateway Redirect]
    PaymentPage[Payment Page]
    PaymentResult[Payment Result]
    Confirmed[Booking Details / QR Ticket]
    Lookup[Booking Lookup]

    Existing[Existing Ring Design Selection]
    Config[Ring Configuration]
    Components[Ring Components]
    Attachment[Ring Attachment]
    Engraving[Ring Engraving]
    Consent[Image Upload Consent Form]
    Upload[Reference Image Upload]
    Analysis[AI Feature Analysis]
    Review[Booking Design Review Status]
    Decision{Human review result}
    Clarify[Clarification Required]
    Rejected[Rejected Design Guidance]

    Home --> Packages --> Package --> Location --> Booking --> Path
    Path --> Existing --> Review
    Path --> Config --> Components
    Components --> Attachment --> Review
    Components --> Engraving --> Review
    Path -->|Member only| Consent --> Upload --> Analysis --> Review
    Review --> Decision
    Decision -->|Accepted by Staff/Manager| Summary
    Decision -->|Clarification Required| Clarify --> Path
    Decision -->|Rejected by Staff/Manager| Rejected --> Path
    Summary -->|Confirm booking and deposit| PaymentRedirect --> PaymentPage --> PaymentResult
    PaymentResult -->|Verified success| Confirmed
    PaymentResult -->|Failed and hold active| PaymentRedirect
    PaymentResult -->|Pending| Lookup
    PaymentResult -->|Expired| Packages
    Home --> Lookup -->|Verified lookup proof| Confirmed
```

The diagram uses the following rules:

- A package, location, session, and booking information are selected before the design path.
- Guest may use existing/configured design paths but cannot enter the consent, upload, or AI-analysis branch.
- Every valid image-analysis request reaches Staff or Manager review. AI never creates an Accepted or Rejected business decision.
- `Clarification Required` and `Rejected` return to a new design version; an old decision cannot be reused after a material design change.
- `Booking Details / QR Ticket` is reachable only after verified payment success. Browser return alone is not payment confirmation.
- `Booking Lookup` is an independent entry path for pending or confirmed bookings; it is not a mandatory step after Booking Summary.
