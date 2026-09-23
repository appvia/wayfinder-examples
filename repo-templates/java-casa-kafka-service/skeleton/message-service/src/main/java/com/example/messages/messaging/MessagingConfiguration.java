package com.example.messages.messaging;

import java.time.Instant;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MessageConverter;

import com.example.messages.events.MessageSubmitted;
import com.example.messages.store.MessageStore;

@Configuration(proxyBeanMethods = false)
public class MessagingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MessagingConfiguration.class);

    /** Spring Cloud Stream uses every MessageConverter bean for bindings whose content type it supports. */
    @Bean
    MessageConverter avroMessageConverter() {
        return new AvroMessageConverter();
    }

    /** Bound to {@code recordMessage-in-0}: reads each MessageSubmitted back off the topic and marks the message RECEIVED. */
    @Bean
    Consumer<MessageSubmitted> recordMessage(MessageStore store) {
        return event -> {
            store.received(event.getReference(), event.getMessage(), event.getSubmittedAt(), Instant.now());
            log.info("Message {} received from Kafka", event.getReference());
        };
    }
}
