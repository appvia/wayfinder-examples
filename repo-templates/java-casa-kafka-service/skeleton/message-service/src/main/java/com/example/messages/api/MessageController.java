package com.example.messages.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.messages.events.MessageSubmitted;
import com.example.messages.messaging.MessageEventPublisher;
import com.example.messages.store.Message;
import com.example.messages.store.MessageStore;
import com.example.messages.store.References;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageStore store;
    private final MessageEventPublisher publisher;

    public MessageController(MessageStore store, MessageEventPublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageCreated submit(@Valid @RequestBody MessageRequest request) {
        // Avro timestamp-millis holds milliseconds, so the stored time is cut to match.
        Instant submittedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Message message = store.submitted(References.next(), request.message(), submittedAt);
        publisher.publish(MessageSubmitted.newBuilder()
                .setReference(message.reference())
                .setMessage(message.text())
                .setSubmittedAt(submittedAt)
                .build());
        return new MessageCreated(message.reference(), message.status());
    }

    @GetMapping("/{reference}")
    public ResponseEntity<MessageView> get(@PathVariable String reference) {
        return ResponseEntity.of(store.find(reference).map(MessageView::of));
    }
}
