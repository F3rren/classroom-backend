package com.classroom.dto;

/** A minimal response carrying one message, for writes with no payload worth returning. */
public record MessageResponse(String message) {
}
