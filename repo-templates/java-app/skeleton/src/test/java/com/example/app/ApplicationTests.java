package com.example.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTests {

  @Autowired private MockMvc mockMvc;

  @Test
  void livenessReportsOk() throws Exception {
    mockMvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  void readinessReportsOk() throws Exception {
    mockMvc.perform(get("/readyz")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  void rootReportsTheServiceIdentity() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service").value("${{ .Inputs.serviceName }}"))
        // Always reports something, even 'dev'.
        .andExpect(jsonPath("$.release").isNotEmpty());
  }

  @Test
  void contextLoads() {
    assertThat(mockMvc).isNotNull();
  }
}
