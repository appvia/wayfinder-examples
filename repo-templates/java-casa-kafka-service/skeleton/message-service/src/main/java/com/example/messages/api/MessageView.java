package com.example.messages.api;

import java.time.Instant;

import com.example.messages.store.Message;
import com.example.messages.store.MessageStatus;

public record MessageView(String reference, String message, MessageStatus status, Instant submittedAt, Instant receivedAt) {

    static MessageView of(Message message) {
        return new MessageView(
                message.reference(), message.text(), message.status(), message.submittedAt(), message.receivedAt());
    }
}
