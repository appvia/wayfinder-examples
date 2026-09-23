package com.example.messages.messaging;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.stereotype.Component;

import com.example.messages.events.MessageSubmitted;

/** Sends MessageSubmitted records to the {@code messageSubmitted-out-0} binding. */
@Component
public class MessageEventPublisher {

    static final String BINDING = "messageSubmitted-out-0";

    private final StreamBridge streamBridge;

    public MessageEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void publish(MessageSubmitted event) {
        if (!streamBridge.send(BINDING, event, AvroMessageConverter.APPLICATION_AVRO)) {
            throw new MessageConversionException("Kafka did not accept MessageSubmitted " + event.getReference());
        }
    }
}
