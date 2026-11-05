### 2.1 Workshop Booking

*Trigger*:

- A Guest or Member selects a workshop package and initiates a booking by choosing a branch, date, and available time slot.

*End condition*:

- The workshop booking is confirmed after successful verification of the 50% deposit payment. The system generates a booking reference and QR check-in ticket, sends a confirmation email, updates the available capacity in the external registration system, and displays the confirmation to the customer.

*Alternative end conditions*:

- If payment fails or the 15-minute payment window expires, the booking is cancelled or marked as expired, the temporarily held seat is released, and the system displays an appropriate notification.

{{r3-swl-booking-workshop width=100% align=center}}
