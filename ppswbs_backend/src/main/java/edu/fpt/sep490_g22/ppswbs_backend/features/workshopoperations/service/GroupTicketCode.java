package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ticket code of a booking group ticket, as customers and the booking screens know it: the line reference of the
 * group ("GRP-5E849940018F442D8C9C-L2") followed by "-T" and the person's number. Operations keeps its own readable
 * code (TK-…) for the same ticket, with the line reference in {@code bookingReference} and the number in
 * {@code ticketSequence}, so a group ticket code can be translated without asking the booking module.
 * Temporary bridge (owner decision 2026-11-17) until the Operations code is replaced by this one.
 */
public record GroupTicketCode(String bookingReference, int ticketSequence) {

    private static final Pattern PATTERN = Pattern.compile("^(.+-L\\d+)-T(\\d+)$");

    public static Optional<GroupTicketCode> parse(String code) {
        if (code == null) return Optional.empty();
        Matcher matcher = PATTERN.matcher(code.trim());
        if (!matcher.matches()) return Optional.empty();
        try {
            return Optional.of(new GroupTicketCode(matcher.group(1), Integer.parseInt(matcher.group(2))));
        } catch (NumberFormatException tooLarge) {
            return Optional.empty();
        }
    }
}
