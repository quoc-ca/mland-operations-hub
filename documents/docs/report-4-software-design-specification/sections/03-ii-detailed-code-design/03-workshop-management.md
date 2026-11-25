## 3. Workshop Management (FE-03)

**Implementation status:** Workshop booking, operations and settlement are separate backend boundaries under the FE-03 group. OTA connectivity remains gated by partner readiness.

### 3.1 Class Diagram

The design groups package/session/location, booking/ticket, check-in and settlement concepts while preserving the one-way facade boundaries between booking, operations, settlement and payment.

### 3.2 Sequence Diagram — Select Session, Hold Capacity and Create Booking

### 3.3 Sequence Diagram — Verify Workshop Payment and Confirm Booking

### 3.4 Sequence Diagram — Check In and Settle Ticket

