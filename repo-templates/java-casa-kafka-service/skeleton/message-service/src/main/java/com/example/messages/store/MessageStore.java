package com.example.messages.store;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * Messages held in memory. The service runs as a single task, so a restart forgets
 * every message; a real service keeps them in a database.
 */
@Component
public class MessageStore {

    private final ConcurrentMap<String, Message> messages = new ConcurrentHashMap<>();

    public Message submitted(String reference, String text, Instant submittedAt) {
        Message message = new Message(reference, text, MessageStatus.SUBMITTED, submittedAt, null);
        messages.put(reference, message);
        return message;
    }

    /**
     * Marks a message RECEIVED. A reference this instance never saw, for example one
     * another instance accepted, is stored as RECEIVED.
     */
    public Message received(String reference, String text, Instant submittedAt, Instant receivedAt) {
        return messages.merge(
                reference,
                new Message(reference, text, MessageStatus.RECEIVED, submittedAt, receivedAt),
                (known, incoming) -> new Message(reference, known.text(), MessageStatus.RECEIVED, known.submittedAt(), receivedAt));
    }

    public Optional<Message> find(String reference) {
        return Optional.ofNullable(messages.get(reference));
    }
}
