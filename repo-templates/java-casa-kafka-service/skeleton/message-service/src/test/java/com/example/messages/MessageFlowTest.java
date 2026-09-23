package com.example.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;

import com.example.messages.events.MessageSubmitted;
import com.example.messages.messaging.AvroMessageConverter;
import com.jayway.jsonpath.JsonPath;

/** Submits a message over HTTP, checks what reaches the topic is Avro binary, and plays it back to the consumer. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestChannelBinderConfiguration.class)
class MessageFlowTest {

    private static final String TOPIC = "messages.message-submitted";

    @Autowired MockMvc mvc;
    @Autowired OutputDestination output;
    @Autowired InputDestination input;

    @Test
    void submittedMessageIsPublishedAsAvroAndMarkedReceivedWhenConsumed() throws Exception {
        String body = mvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"  Hello, Kafka  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(body, "$.reference");
        assertThat(reference).matches("MSG-[A-Z0-9]{8}");

        Message<byte[]> sent = output.receive(5000, TOPIC);
        assertThat(sent).as("a record on " + TOPIC).isNotNull();
        assertThat(String.valueOf(sent.getHeaders().get(MessageHeaders.CONTENT_TYPE))).startsWith("application/avro");

        // Decode the bytes with nothing but the schema, to show they are plain Avro binary.
        MessageSubmitted event = new SpecificDatumReader<>(MessageSubmitted.class)
                .read(null, DecoderFactory.get().binaryDecoder(sent.getPayload(), null));
        assertThat(event.getReference()).isEqualTo(reference);
        assertThat(event.getMessage()).isEqualTo("Hello, Kafka");

        // The test binder already hands what is sent on a destination to the consumer
        // bound to the same destination, as Kafka would; sending the captured bytes
        // again shows the consumer reads the Avro binary it was given.
        input.send(MessageBuilder.withPayload(sent.getPayload())
                        .setHeader(MessageHeaders.CONTENT_TYPE, AvroMessageConverter.APPLICATION_AVRO.toString())
                        .build(),
                TOPIC);

        mvc.perform(get("/api/messages/{ref}", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.message").value("Hello, Kafka"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty())
                .andExpect(jsonPath("$.receivedAt").isNotEmpty());
    }

    @Test
    void messageFromAnotherInstanceIsStoredAsReceived() throws Exception {
        byte[] bytes = (byte[]) new AvroMessageConverter()
                .toMessage(TestMessages.event("MSG-ELSEWHER"), new MessageHeaders(Map.of()))
                .getPayload();

        input.send(MessageBuilder.withPayload(bytes)
                        .setHeader(MessageHeaders.CONTENT_TYPE, AvroMessageConverter.APPLICATION_AVRO.toString())
                        .build(),
                TOPIC);

        mvc.perform(get("/api/messages/MSG-ELSEWHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.submittedAt").value("2026-09-01T10:15:30.123Z"));
    }

    @Test
    void blankMessageIsRefusedNamingTheField() throws Exception {
        mvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON).content("{\"message\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").isNotEmpty());
    }

    @Test
    void unknownReferenceIsNotFound() throws Exception {
        mvc.perform(get("/api/messages/MSG-NOTFOUND")).andExpect(status().isNotFound());
    }

    @Test
    void healthzReportsTheRelease() throws Exception {
        mvc.perform(get("/api/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.release").value("dev"));
    }
}
