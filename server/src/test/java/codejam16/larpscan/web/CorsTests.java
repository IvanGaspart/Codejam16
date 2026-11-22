package codejam16.larpscan.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codejam16.larpscan.repository.BatchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Verifies CORS policy on {@code /api/**}: Lovable and local dev origins may read public
 * {@code GET} endpoints, while unknown origins and cross-origin {@code POST}s are rejected (RQ-06).
 */
@SpringBootTest
@ActiveProfiles("test")
class CorsTests {

  private static final String LOVABLE_APP_ORIGIN = "https://larp-scan.lovable.app";
  private static final String LOVABLE_PREVIEW_ORIGIN = "https://preview--larp-scan.lovableproject.com";
  private static final String LOCAL_DEV_ORIGIN = "http://localhost:5173";
  private static final String DISALLOWED_ORIGIN = "https://evil.example.com";

  @Autowired private WebApplicationContext context;
  @Autowired private ApiKeyFilter apiKeyFilter;
  @Autowired private BatchRepository batches;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters(apiKeyFilter)
        .build();
  }

  @AfterEach
  void tearDown() {
    batches.deleteAll();
  }

  @Test
  void allowsLovableAppOriginOnGetEndpoints() throws Exception {
    mockMvc.perform(get("/api/buildings")
            .header(ORIGIN, LOVABLE_APP_ORIGIN))
        .andExpect(status().isOk())
        .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, LOVABLE_APP_ORIGIN));
  }

  @Test
  void allowsLovablePreviewOriginOnNestedFloorEndpoints() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors")
            .header(ORIGIN, LOVABLE_PREVIEW_ORIGIN))
        .andExpect(status().isOk())
        .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, LOVABLE_PREVIEW_ORIGIN));
  }

  @Test
  void allowsLocalhostOriginForFrontendDevelopment() throws Exception {
    mockMvc.perform(get("/api/buildings")
            .header(ORIGIN, LOCAL_DEV_ORIGIN))
        .andExpect(status().isOk())
        .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_DEV_ORIGIN));
  }

  @Test
  void allowsPreflightOptionsForGetFromLovableOrigin() throws Exception {
    mockMvc.perform(options("/api/buildings/trottier/floors/1/history")
            .header(ORIGIN, LOVABLE_APP_ORIGIN)
            .header(ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, LOVABLE_APP_ORIGIN))
        .andExpect(header().string(ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")));
  }

  @Test
  void rejectsDisallowedOriginOnGetEndpoints() throws Exception {
    mockMvc.perform(get("/api/buildings")
            .header(ORIGIN, DISALLOWED_ORIGIN))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void rejectsPreflightOptionsForPostEvenFromLovableOrigin() throws Exception {
    mockMvc.perform(options("/api/batches")
            .header(ORIGIN, LOVABLE_APP_ORIGIN)
            .header(ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsCrossOriginPostEvenWithValidApiKey() throws Exception {
    String payload = """
        {
          "scannerId": "3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab",
          "bssid": "44:48:c1:a4:1a:50",
          "sentAt": "2026-11-21T14:03:15Z",
          "sightings": [-67]
        }
        """;

    mockMvc.perform(post("/api/batches")
            .header(ORIGIN, LOVABLE_APP_ORIGIN)
            .header(ApiKeyFilter.API_KEY_HEADER, "test-api-key")
            .contentType(APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isForbidden());
  }
}
