package com.example.messages.store;

public enum MessageStatus {
    /** Accepted over HTTP and published to Kafka. */
    SUBMITTED,
    /** Read back off Kafka by this service's consumer. */
    RECEIVED
}
