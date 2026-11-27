## 3. Workshop Management 

### 3.1 Class Diagram

The design groups package/session/location, booking/ticket, check-in and settlement concepts while preserving the one-way facade boundaries between booking, operations, settlement and payment.

#### 3.1.1 Workshop Booking

{{r4-cd-workshop-booking width=100% align=center}}

#### 3.1.2 Workshop Operations

{{r4-cd-workshop-operations width=100% align=center}}

#### 3.1.3 Workshop Settlement and Changes

{{r4-cd-workshop-settlement width=100% align=center}}

### 3.2 Sequence Diagram — Book a Workshop and Pay with VNPay

{{r4-sd-book-workshop-vnpay width=100% align=center}}

### 3.3 Sequence Diagram — Create a Workshop Session

{{r4-sd-create-workshop-session width=100% align=center}}

### 3.4 Sequence Diagram — Check In Participants and Finalize Attendance

{{r4-sd-checkin-finalize-attendance width=100% align=center}}

### 3.5 Sequence Diagram — Settle the Final Payment at the Counter

{{r4-sd-settle-final-payment width=100% align=center}}

### 3.6 Sequence Diagram — Cancel a Workshop Ticket

{{r4-sd-cancel-workshop-ticket width=100% align=center}}
