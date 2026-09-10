package com.classroom.booking.dto;

import com.classroom.exception.InvalidRequestException;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * The two instants a booking spans, parsed and checked once.
 *
 * It exists because the same block of about thirty-five lines - two try/catch around
 * LocalDateTime.parse, then end-after-start, then for some of them not-in-the-past - was
 * written four times in BookingController: bookRoom, updateBooking, blockRoom and
 * checkAvailability.
 *
 * The four copies had ALREADY drifted, which is the part worth recording. Two of them
 * explained the expected format with an example and two did not, so the same mistake was
 * answered with two different degrees of help depending on which endpoint you hit; and
 * checkAvailability had quietly lost the past check altogether. Nobody could have told
 * whether either difference was deliberate. Here there is one wording, and the past check is
 * something a caller asks for explicitly - see requireNotInThePast - so its absence is a
 * visible decision rather than an omission.
 *
 * The refusals travel as InvalidRequestException, which GlobalExceptionHandler already turns
 * into a 400 with the same error codes these endpoints have always returned. The controller
 * therefore stops building error responses by hand for them.
 */
public record BookingPeriod(LocalDateTime start, LocalDateTime end) {

    /**
     * The example is kept, and is now on every endpoint rather than on two of four. It is
     * the whole difference between a caller who fixes the request and one who guesses.
     */
    private static final String START_FORMAT_MESSAGE =
            "La data di inizio deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T14:30:00)";

    private static final String END_FORMAT_MESSAGE =
            "La data di fine deve essere nel formato YYYY-MM-DDTHH:MM:SS (es: 2024-12-25T16:30:00)";

    /** How much of a rejected value is worth quoting back. Beyond this it is not a date. */
    private static final int MAX_QUOTED_LENGTH = 40;

    /**
     * Reads the two instants and checks they make a period.
     *
     * @throws InvalidRequestException with INVALID_START_DATE, INVALID_END_DATE or
     *         INVALID_DATE_RANGE - the same three codes the four hand-written copies used.
     */
    public static BookingPeriod parse(String rawStart, String rawEnd) {
        LocalDateTime start = parseOne(rawStart, "INVALID_START_DATE", "start", START_FORMAT_MESSAGE);
        LocalDateTime end = parseOne(rawEnd, "INVALID_END_DATE", "end", END_FORMAT_MESSAGE);

        if (end.isBefore(start)) {
            throw new InvalidRequestException("INVALID_DATE_RANGE",
                    "Invalid time range: end " + end + " precedes start " + start,
                    "La data di fine deve essere successiva alla data di inizio.");
        }
        return new BookingPeriod(start, end);
    }

    /**
     * Refuses a period that has already begun.
     *
     * The userMessage is the caller's because the two endpoints that want this check say
     * different things - one is about creating a booking, the other about changing one - and
     * that difference is real, unlike the ones this class removed. Blocking a room and
     * checking availability do not call it at all: an admin blocking a room that is busy
     * right now is a legitimate thing to want.
     */
    public BookingPeriod requireNotInThePast(String userMessage) {
        if (start.isBefore(LocalDateTime.now())) {
            throw new InvalidRequestException("PAST_DATE",
                    "Start time in the past: " + start, userMessage);
        }
        return this;
    }

    private static LocalDateTime parseOne(String raw, String errorCode, String which, String userMessage) {
        // null is unreachable through the controller - the DTO carries @NotBlank and the
        // query parameter is required - but LocalDateTime.parse(null) throws NPE, which
        // would surface as a 500 rather than as this 400. Cheaper to answer it than to rely
        // on two other files not changing.
        if (raw == null) {
            throw new InvalidRequestException(errorCode, "Missing " + which + " date", userMessage);
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException(errorCode,
                    "Unparseable " + which + " date: '" + forLog(raw) + "'", userMessage);
        }
    }

    /**
     * The rejected value, made safe to write on a log line.
     *
     * It is quoted because knowing WHAT was sent is most of the diagnosis, and it comes from
     * the caller: a value carrying a newline would otherwise end the line and let whoever
     * sent it write a second, forged one. Logback's pattern does not escape it, so it is
     * escaped here, at the one place this value is now formatted.
     */
    private static String forLog(String raw) {
        String flattened = raw.replaceAll("[\\p{Cntrl}]", "?");
        return flattened.length() <= MAX_QUOTED_LENGTH
                ? flattened
                : flattened.substring(0, MAX_QUOTED_LENGTH) + "...";
    }
}
