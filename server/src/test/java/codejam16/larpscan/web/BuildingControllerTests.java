package codejam16.larpscan.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.domain.BusynessSnapshot;
import codejam16.larpscan.domain.Floor;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.repository.BusynessSnapshotRepository;
import codejam16.larpscan.repository.FloorRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
  private static final ZoneId TROTTIER_ZONE = ZoneId.of("America/Toronto");

  @Autowired private WebApplicationContext context;
  @Autowired private FloorRepository floors;
  @Autowired private AccessPointRepository accessPoints;
  @Autowired private BatchRepository batches;
  @Autowired private BusynessSnapshotRepository snapshots;
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

  @Test
  void getsFloorHistoryWithExplicitSince() throws Exception {
    seedTrottierFloors();
    snapshot(1, "2026-11-21T13:59:00Z", 20, true);
    snapshot(1, "2026-11-21T14:02:00Z", 28, true);
    snapshot(1, "2026-11-21T14:00:00Z", 24, true);
    snapshot(1, "2026-11-21T14:01:00Z", 26, true);
    snapshot(2, "2026-11-21T14:01:00Z", 50, true);

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
    seedTrottierFloors();
    Instant midnight = torontoMidnight();
    snapshot(2, midnight.minusSeconds(60).toString(), 40, true);
    snapshot(2, midnight.toString(), 45, true);

    mockMvc.perform(get("/api/buildings/trottier/floors/2/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].at").value(midnight.toString()))
        .andExpect(jsonPath("$[0].covered").value(true));
  }

  @Test
  void getsFloorHistoryTreatsBlankSinceAsTorontoMidnight() throws Exception {
    seedTrottierFloors();
    Instant midnight = torontoMidnight();
    snapshot(2, midnight.minusSeconds(60).toString(), 40, true);
    snapshot(2, midnight.toString(), 45, true);

    mockMvc.perform(get("/api/buildings/trottier/floors/2/history")
        .param("since", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].at").value(midnight.toString()));
  }

  @Test
  void getsFloorHistoryForUncoveredFloor() throws Exception {
    seedTrottierFloors();
    snapshot(4, "2026-11-21T14:00:00Z", 0, false);

    mockMvc.perform(get("/api/buildings/trottier/floors/4/history")
            .param("since", "2026-11-21T14:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].at").value("2026-11-21T14:00:00Z"))
        .andExpect(jsonPath("$[0].busynessPct").value(0))
        .andExpect(jsonPath("$[0].covered").value(false));
  }

  @Test
  void getsEmptyHistoryForGroundFloorWithoutSnapshots() throws Exception {
    seedTrottierFloors();

    mockMvc.perform(get("/api/buildings/trottier/floors/0/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void rejectsUnknownBuildingForHistoryWithNotFound() throws Exception {
    mockMvc.perform(get("/api/buildings/unknown/floors/1/history"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void rejectsNegativeFloorWithNotFound() throws Exception {
    seedTrottierFloors();
    mockMvc.perform(get("/api/buildings/trottier/floors/-1/history"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void rejectsOutOfRangeFloorWithNotFound() throws Exception {
    seedTrottierFloors();
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

  /** Trottier's Floors 0–5, as loaded from {@code floors.csv}. */
  private void seedTrottierFloors() {
    for (int floor = 0; floor <= 5; floor++) {
      floors.save(new Floor("trottier", floor, 100));
    }
  }

  private void snapshot(int floor, String at, int busynessPct, boolean covered) {
    snapshots.save(
        new BusynessSnapshot("trottier", floor, Instant.parse(at), busynessPct, covered)
    );
  }

  private static Instant torontoMidnight() {
    return LocalDate.now(TROTTIER_ZONE).atStartOfDay(TROTTIER_ZONE).toInstant();
  }

  private void clearTables() {
    snapshots.deleteAll();
    batches.deleteAll();
    accessPoints.deleteAll();
    floors.deleteAll();
  }
}
