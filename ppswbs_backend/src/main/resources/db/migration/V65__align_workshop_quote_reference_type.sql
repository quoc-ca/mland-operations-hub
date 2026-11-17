-- MemberWorkshopBookingQuote.quoteRef is a String mapped as VARCHAR(36).
ALTER TABLE member_workshop_booking_quotes
    MODIFY COLUMN quote_ref VARCHAR(36) NOT NULL;
