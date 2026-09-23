package com.example.messages;

import java.time.Instant;

import com.example.messages.events.MessageSubmitted;

public final class TestMessages {

    private TestMessages() {}

    public static MessageSubmitted event(String reference) {
        return MessageSubmitted.newBuilder()
                .setReference(reference)
                .setMessage("Hello from another instance")
                .setSubmittedAt(Instant.parse("2026-09-01T10:15:30.123Z"))
                .build();
    }
}
