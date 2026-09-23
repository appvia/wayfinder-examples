package com.example.messages.contracts;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base class for the tests Spring Cloud Contract generates from
 * src/test/resources/contracts. It runs the whole application with the in-memory
 * test binder in place of Kafka.
 */
@SpringBootTest
@Import(TestChannelBinderConfiguration.class)
public abstract class ContractVerifierBase {

    @Autowired WebApplicationContext context;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.webAppContextSetup(context);
    }
}
