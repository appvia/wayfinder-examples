package com.example.messages.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /api/messages}. Surrounding spaces are dropped. */
public record MessageRequest(@NotBlank @Size(max = 500) String message) {

    public MessageRequest {
        message = message == null ? null : message.strip();
    }
}
