package com.example.messages.store;

import java.time.Instant;

/** What message-service knows about one message. {@code receivedAt} is null until its record is consumed. */
public record Message(String reference, String text, MessageStatus status, Instant submittedAt, Instant receivedAt) {}
