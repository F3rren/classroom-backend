package com.classroom.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The format of the timestamps the API exposes.
 *
 * The same DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") was declared, identically, in
 * five different classes: ApiEnvelope, LoginResponse, UserSummaryDto, AuthController and
 * BookingController. The pattern is part of the contract towards the client, so five copies
 * are five places it can drift from with nothing to signal it.
 *
 * It lives in util and not inside ApiEnvelope because both DTOs and controllers use it:
 * making it depend on one particular DTO would create a coupling with no reason behind it.
 */
public final class Timestamps {

    private static final DateTimeFormatter API_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Timestamps() {
    }

    /** Formats an instant the way the responses do. Null in, null out. */
    public static String format(LocalDateTime instant) {
        return instant == null ? null : instant.format(API_FORMAT);
    }

    /** The present moment, already formatted. */
    public static String now() {
        return format(LocalDateTime.now());
    }
}
