package codejam16.larpscan.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test") //enable test profile
class BatchControllerTests {

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void acceptsValidBatchWithSightings() throws Exception {
    String payload = """
        {
          "scannerId": "3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab",
          "bssid": "44:48:c1:a4:1a:50",
          "sentAt": "2026-11-21T14:03:15Z",
          "sightings": [-67]
        }
        """;

    mockMvc.perform(post("/api/batches").contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isAccepted());
  }

  @Test
  void acceptsValidBatchWithEmptySightings() throws Exception {
    String payload = """
        {
          "scannerId": "3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab",
          "bssid": "ff:ee:dd:cc:bb:aa",
          "sentAt": "2026-11-21T14:03:15Z",
          "sightings": []
        }
        """;

    mockMvc.perform(post("/api/batches").contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isAccepted());
  }

  @Test
  void rejectsUppercaseOrMalformedMacAddresses() throws Exception {
    String payload = """
        {
          "scannerId": "3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab",
          "bssid": "44:48:C1:A4:1A:50",
          "sentAt": "2026-11-21T14:03:15Z",
          "sightings": [-67]
        }
        """;

    mockMvc.perform(post("/api/batches").contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.bssid").exists());
  }

  @Test
  void rejectsMissingRequiredFieldsAndOutOfRangeRssi() throws Exception {
    String payload = """
        {
          "bssid": "44:48:c1:a4:1a:50",
          "sightings": [12]
        }
        """;

    mockMvc.perform(post("/api/batches").contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.scannerId").exists())
        .andExpect(jsonPath("$.errors.sentAt").exists())
        .andExpect(jsonPath("$.errors['sightings[0]']").exists());
  }

  @Test
  void rejectsMalformedJsonOrInvalidTimestamp() throws Exception {
    String payload = """
        {
          "scannerId": "not-a-uuid",
          "bssid": "44:48:c1:a4:1a:50",
          "sentAt": "2026-11-21T14:03:15Z",
          "sightings": []
        }
        """;

    mockMvc.perform(post("/api/batches").contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isBadRequest());
  }
}
