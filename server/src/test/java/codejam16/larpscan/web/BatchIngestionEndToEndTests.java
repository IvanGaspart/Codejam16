package codejam16.larpscan.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.repository.BatchRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Posts batches through the real controller and checks what reaches the database.
 */
@SpringBootTest
@ActiveProfiles("test") //enable test profile
class BatchIngestionEndToEndTests {

  private static final String TEST_API_KEY = "test-api-key";

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private ApiKeyFilter apiKeyFilter;

  @Autowired
  private BatchRepository batches;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters(apiKeyFilter)
        .build();
    batches.deleteAll();
  }

  @Test
  void acceptedBatchIsStored() throws Exception {
    String payload = """
        {
          "scannerId": "3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab",
          "bssid": "44:48:c1:a4:1a:50",
          "sentAt": "2026-11-21T14:03:15Z",
          "sightings": [-67, -72]
        }
        """;

    mockMvc.perform(post("/api/batches")
            .header(ApiKeyFilter.API_KEY_HEADER, TEST_API_KEY)
            .contentType(APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isAccepted());

    List<Batch> all = batches.findAll();
    assertEquals(1, all.size());
    assertEquals("44:48:c1:a4:1a:50", all.get(0).getBssid());
    assertArrayEquals(new int[] {-67, -72}, all.get(0).getRssis());
  }

  @Test
  void rejectedBatchIsNotStored() throws Exception {
    String payload = """
        {
          "scannerId": "3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab",
          "bssid": "44:48:c1:a4:1a:50",
          "sentAt": "2026-11-21T14:03:15Z",
          "sightings": [12]
        }
        """;

    mockMvc.perform(post("/api/batches")
            .header(ApiKeyFilter.API_KEY_HEADER, TEST_API_KEY)
            .contentType(APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isBadRequest());

    assertEquals(0, batches.count());
  }

  @Test
  void unauthorizedBatchIsNotStored() throws Exception {
    String payload = """
        {
          "scannerId": "3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab",
          "bssid": "44:48:c1:a4:1a:50",
          "sentAt": "2026-11-21T14:03:15Z",
          "sightings": [-67, -72]
        }
        """;

    mockMvc.perform(post("/api/batches")
            .header(ApiKeyFilter.API_KEY_HEADER, "wrong-api-key")
            .contentType(APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isUnauthorized());

    assertEquals(0, batches.count());
  }
}
