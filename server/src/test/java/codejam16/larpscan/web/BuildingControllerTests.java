package codejam16.larpscan.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.domain.Floor;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.repository.FloorRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

  private static final String AP_FLOOR_1 = "44:48:c1:a4:1a:50";

  @Autowired private WebApplicationContext context;
  @Autowired private FloorRepository floors;
  @Autowired private AccessPointRepository accessPoints;
  @Autowired private BatchRepository batches;
  @Autowired private Clock clock;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    clearTables();
  }

  @AfterEach
  void tearDown() {
    clearTables();
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
    floors.save(new Floor("trottier", 4, 80));
    floors.save(new Floor("trottier", 1, 120));
    accessPoints.save(new AccessPoint(AP_FLOOR_1, "trottier", 1));
    Instant recent = clock.instant().minusSeconds(5);
    batches.save(new Batch(UUID.randomUUID(), AP_FLOOR_1, recent, recent, new int[85]));

    mockMvc.perform(get("/api/buildings/trottier/floors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].floor").value(1))
        .andExpect(jsonPath("$[0].capacity").value(120))
        .andExpect(jsonPath("$[0].covered").value(true))
        .andExpect(jsonPath("$[0].estimatedPeople").value(34))
        .andExpect(jsonPath("$[0].busynessPct").value(28))
        .andExpect(jsonPath("$[0].level").value("QUIET"))
        .andExpect(jsonPath("$[1].floor").value(4))
        .andExpect(jsonPath("$[1].covered").value(false))
        .andExpect(jsonPath("$[1].estimatedPeople").value(0))
        .andExpect(jsonPath("$[1].busynessPct").value(0))
        .andExpect(jsonPath("$[1].level").value("NO_DATA"));
  }

  @Test
  void rejectsUnknownBuildingWithNotFound() throws Exception {
    mockMvc.perform(get("/api/buildings/unknown/floors"))
        .andExpect(status().isNotFound());
  }

  private void clearTables() {
    batches.deleteAll();
    accessPoints.deleteAll();
    floors.deleteAll();
  }
}
