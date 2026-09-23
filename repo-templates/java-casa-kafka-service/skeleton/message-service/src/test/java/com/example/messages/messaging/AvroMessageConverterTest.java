package com.example.messages.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.support.MessageBuilder;

import com.example.messages.TestMessages;
import com.example.messages.events.MessageSubmitted;

class AvroMessageConverterTest {

    private final AvroMessageConverter converter = new AvroMessageConverter();
    private final MessageHeaders avroHeaders =
            new MessageHeaders(Map.of(MessageHeaders.CONTENT_TYPE, AvroMessageConverter.APPLICATION_AVRO));

    @Test
    void writesAvroBinaryAndReadsTheSameRecordBack() {
        MessageSubmitted event = TestMessages.event("MSG-ROUNDTRP");

        Message<?> written = converter.toMessage(event, avroHeaders);
        assertThat(written).isNotNull();
        assertThat(written.getPayload()).isInstanceOf(byte[].class);

        Object read = converter.fromMessage(written, MessageSubmitted.class);
        assertThat(read).isEqualTo(event);
    }

    @Test
    void refusesARecordWithARequiredFieldMissing() {
        // The no-argument constructor skips the builder's own checks, as a
        // hand-built or badly mapped record would.
        MessageSubmitted invalid = new MessageSubmitted();
        invalid.setMessage("no reference");
        invalid.setSubmittedAt(TestMessages.event("x").getSubmittedAt());

        assertThatThrownBy(() -> converter.toMessage(invalid, avroHeaders))
                .isInstanceOf(MessageConversionException.class)
                .hasMessageContaining("com.example.messages.events.MessageSubmitted");
    }

    @Test
    void refusesAPayloadThatIsNotAvro() {
        Message<byte[]> garbage = MessageBuilder.withPayload(new byte[] {(byte) 0xff, 0x01})
                .setHeader(MessageHeaders.CONTENT_TYPE, AvroMessageConverter.APPLICATION_AVRO)
                .build();

        assertThatThrownBy(() -> converter.fromMessage(garbage, MessageSubmitted.class))
                .isInstanceOf(MessageConversionException.class);
    }
}
