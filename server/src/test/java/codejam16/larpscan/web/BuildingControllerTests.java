package codejam16.larpscan.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class BuildingControllerTests {

  private static final ZoneId TROTTIER_ZONE = ZoneId.of("America/Toronto");

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void listsBuildings() throws Exception {
    mockMvc.perform(get("/api/buildings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("trottier"))
        .andExpect(jsonPath("$[0].name").value("Trottier Building"));
  }

  @Test
  void listsFloorsForTrottier() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].floor").value(1))
        .andExpect(jsonPath("$[0].capacity").value(120))
        .andExpect(jsonPath("$[0].covered").value(true))
        .andExpect(jsonPath("$[0].estimatedPeople").value(34))
        .andExpect(jsonPath("$[0].busynessPct").value(28))
        .andExpect(jsonPath("$[0].level").value("QUIET"))
        .andExpect(jsonPath("$[3].floor").value(4))
        .andExpect(jsonPath("$[3].covered").value(false))
        .andExpect(jsonPath("$[3].estimatedPeople").value(0))
        .andExpect(jsonPath("$[3].busynessPct").value(0))
        .andExpect(jsonPath("$[3].level").value("NO_DATA"));
  }

  @Test
  void rejectsUnknownBuildingWithNotFound() throws Exception {
    mockMvc.perform(get("/api/buildings/unknown/floors"))
        .andExpect(status().isNotFound());
  }

  @Test
  void getsFloorHistoryWithExplicitSince() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors/1/history")
        .param("since", "2026-11-21T14:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].at").value("2026-11-21T14:00:00Z"))
        .andExpect(jsonPath("$[0].busynessPct").value(24))
        .andExpect(jsonPath("$[0].covered").value(true))
        .andExpect(jsonPath("$[2].at").value("2026-11-21T14:02:00Z"))
        .andExpect(jsonPath("$[2].busynessPct").value(28))
        .andExpect(jsonPath("$[2].covered").value(true));
  }

  @Test
  void getsFloorHistoryDefaultsSinceToTorontoMidnight() throws Exception {
    String expectedMidnight =
        LocalDate.now(TROTTIER_ZONE).atStartOfDay(TROTTIER_ZONE).toInstant().toString();

    mockMvc.perform(get("/api/buildings/trottier/floors/2/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].at").value(expectedMidnight))
        .andExpect(jsonPath("$[0].covered").value(true));
  }

  @Test
  void getsFloorHistoryTreatsBlankSinceAsTorontoMidnight() throws Exception {
    String expectedMidnight =
        LocalDate.now(TROTTIER_ZONE).atStartOfDay(TROTTIER_ZONE).toInstant().toString();

    mockMvc.perform(get("/api/buildings/trottier/floors/2/history")
        .param("since", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].at").value(expectedMidnight));
  }

  @Test
  void getsFloorHistoryForUncoveredFloor() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors/4/history")
            .param("since", "2026-11-21T14:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].at").value("2026-11-21T14:00:00Z"))
        .andExpect(jsonPath("$[0].busynessPct").value(0))
        .andExpect(jsonPath("$[0].covered").value(false));
  }

  @Test
  void rejectsUnknownBuildingForHistoryWithNotFound() throws Exception {
    mockMvc.perform(get("/api/buildings/unknown/floors/1/history"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void rejectsZeroFloorWithNotFound() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors/0/history"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void rejectsNegativeFloorWithNotFound() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors/-1/history"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void rejectsOutOfRangeFloorWithNotFound() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors/6/history"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void rejectsUnknownBuildingAndUnknownFloorWithNotFound() throws Exception {
    mockMvc.perform(get("/api/buildings/unknown/floors/99/history"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void rejectsNonNumericFloorWithBadRequest() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors/abc/history"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail", containsString("floor")));
  }

  @Test
  void rejectsOverflowFloorWithBadRequest() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors/999999999999999/history"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail", containsString("floor")));
  }

  @Test
  void rejectsMalformedSinceWithBadRequest() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors/1/history")
            .param("since", "not-a-timestamp"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail", containsString("since")));
  }

  @Test
  void rejectsDateOnlySinceWithBadRequest() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors/1/history")
            .param("since", "2026-11-21"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail", containsString("since")));
  }

  @Test
  void rejectsOffsetlessDateTimeSinceWithBadRequest() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors/1/history")
            .param("since", "2026-11-21T14:00:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail", containsString("since")));
  }
}
