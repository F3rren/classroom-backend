package com.classroom.booking.dto;

import com.classroom.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The rule that used to be written four times in BookingController, now written once.
 *
 * The error codes are asserted rather than only the messages: they are what a client
 * branches on, they have been exposed since before this class existed, and moving the rule
 * here had to leave them untouched.
 */
class BookingPeriodUnitTest {

    private static String inTwoHours() {
        return LocalDateTime.now().plusHours(2).withNano(0).toString();
    }

    private static String inThreeHours() {
        return LocalDateTime.now().plusHours(3).withNano(0).toString();
    }

    @Test
    void aWellFormedPeriodIsRead() {
        BookingPeriod period = BookingPeriod.parse("2026-12-25T14:30:00", "2026-12-25T16:30:00");

        assertThat(period.start()).isEqualTo(LocalDateTime.of(2026, 12, 25, 14, 30));
        assertThat(period.end()).isEqualTo(LocalDateTime.of(2026, 12, 25, 16, 30));
    }

    @Test
    void anUnreadableStartIsRefusedWithItsOwnCode() {
        InvalidRequestException ex = catchThrowableOfType(
                () -> BookingPeriod.parse("non-una-data", "2026-12-25T16:30:00"),
                InvalidRequestException.class);

        assertThat(ex.getErrorCode()).isEqualTo("INVALID_START_DATE");
        // The example is now on every endpoint: two of the four copies did not have it, so
        // the same mistake got two different degrees of help depending on where you sent it.
        assertThat(ex.getUserMessage()).contains("YYYY-MM-DDTHH:MM:SS", "es: 2024-12-25T14:30:00");
        // The rejected value belongs in the technical message, which goes to the log: it is
        // most of the diagnosis, and it means nothing to the person reading the answer.
        assertThat(ex.getMessage()).contains("non-una-data");
        assertThat(ex.getUserMessage()).doesNotContain("non-una-data");
    }

    @Test
    void anUnreadableEndIsRefusedWithItsOwnCode() {
        InvalidRequestException ex = catchThrowableOfType(
                () -> BookingPeriod.parse("2026-12-25T14:30:00", "nemmeno-questa"),
                InvalidRequestException.class);

        assertThat(ex.getErrorCode()).isEqualTo("INVALID_END_DATE");
        assertThat(ex.getUserMessage()).contains("es: 2024-12-25T16:30:00");
    }

    @Test
    void theStartIsCheckedBeforeTheEnd() {
        // Both are wrong: the answer names the first one, so a caller fixing them one at a
        // time works front to back instead of being sent to the end of the request.
        InvalidRequestException ex = catchThrowableOfType(
                () -> BookingPeriod.parse("sbagliata", "anche-questa"),
                InvalidRequestException.class);

        assertThat(ex.getErrorCode()).isEqualTo("INVALID_START_DATE");
    }

    @Test
    void anEndBeforeItsStartIsRefused() {
        InvalidRequestException ex = catchThrowableOfType(
                () -> BookingPeriod.parse("2026-12-25T16:30:00", "2026-12-25T14:30:00"),
                InvalidRequestException.class);

        assertThat(ex.getErrorCode()).isEqualTo("INVALID_DATE_RANGE");
        assertThat(ex.getUserMessage()).isEqualTo("La data di fine deve essere successiva alla data di inizio.");
    }

    @Test
    void aPeriodEndingWhereItStartsIsAccepted() {
        // isBefore and not isAfter-or-equal: a zero-length period has always been allowed
        // through, and this pins that the move did not tighten the rule by accident.
        assertThat(BookingPeriod.parse("2026-12-25T14:30:00", "2026-12-25T14:30:00")).isNotNull();
    }

    @Test
    void aMissingValueIsA400AndNotANullPointer() {
        // Unreachable through the controller - @NotBlank on the DTO, and the query parameter
        // is required - but LocalDateTime.parse(null) throws NPE, which would surface as a
        // 500. Cheaper to answer it than to rely on two other files not changing.
        assertThatThrownBy(() -> BookingPeriod.parse(null, "2026-12-25T16:30:00"))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(ex -> assertThat(((InvalidRequestException) ex).getErrorCode())
                        .isEqualTo("INVALID_START_DATE"));
    }

    @Test
    void aValueCarryingANewlineCannotForgeALogLine() {
        // The rejected value is quoted into a log line and comes from the caller. Logback
        // does not escape it, so a newline would end the line and let whoever sent it write
        // a second, forged one.
        InvalidRequestException ex = catchThrowableOfType(
                () -> BookingPeriod.parse("x\nWARN  REQ_000 FakeLogger : forged", "2026-12-25T16:30:00"),
                InvalidRequestException.class);

        assertThat(ex.getMessage()).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    void aVeryLongValueIsTruncatedBeforeReachingTheLog() {
        InvalidRequestException ex = catchThrowableOfType(
                () -> BookingPeriod.parse("x".repeat(500), "2026-12-25T16:30:00"),
                InvalidRequestException.class);

        assertThat(ex.getMessage()).hasSizeLessThan(120).endsWith("...'");
    }

    // ==================== the past check, which is opt-in ====================

    @Test
    void aPeriodInTheFuturePassesThePastCheck() {
        BookingPeriod period = BookingPeriod.parse(inTwoHours(), inThreeHours());

        assertThat(period.requireNotInThePast("qualsiasi messaggio")).isSameAs(period);
    }

    @Test
    void aPeriodAlreadyBegunIsRefusedWithTheCallersOwnMessage() {
        BookingPeriod period = BookingPeriod.parse("2020-01-01T10:00:00", "2020-01-01T12:00:00");

        InvalidRequestException ex = catchThrowableOfType(
                () -> period.requireNotInThePast("Non puoi prenotare un'aula per una data già trascorsa."),
                InvalidRequestException.class);

        assertThat(ex.getErrorCode()).isEqualTo("PAST_DATE");
        // The caller supplies the sentence because the two endpoints wanting this check say
        // genuinely different things - creating a booking, changing one - unlike the
        // differences this class removed.
        assertThat(ex.getUserMessage()).isEqualTo("Non puoi prenotare un'aula per una data già trascorsa.");
    }

    @Test
    void aPeriodInThePastIsPerfectlyValidWhenNobodyAsksAboutIt() {
        // blockRoom and checkAvailability never call requireNotInThePast, and that is a
        // decision rather than an omission: blocking a room busy right now is legitimate,
        // and asking whether one was free yesterday is a fair question. One of the four
        // copies had lost this check silently, which is what made it impossible to tell.
        assertThat(BookingPeriod.parse("2020-01-01T10:00:00", "2020-01-01T12:00:00")).isNotNull();
    }
}
