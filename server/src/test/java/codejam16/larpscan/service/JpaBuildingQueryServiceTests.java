package codejam16.larpscan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.domain.BusynessLevel;
import codejam16.larpscan.domain.Floor;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.repository.FloorRepository;
import codejam16.larpscan.web.dto.BuildingResponse;
import codejam16.larpscan.web.dto.FloorBusynessResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class JpaBuildingQueryServiceTests {

  private static final Instant NOW = Instant.parse("2026-11-21T14:03:16Z");
  private static final String AP_FLOOR_1 = "44:48:c1:a4:1a:50";
  private static final UUID SCANNER = UUID.fromString("3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab");

  @Autowired private BatchRepository batches;
  @Autowired private FloorRepository floors;
  @Autowired private AccessPointRepository accessPoints;

  private JpaBuildingQueryService service;

  @BeforeEach
  void setUp() {
    floors.save(new Floor("trottier", 2, 90));
    floors.save(new Floor("trottier", 1, 120));
    accessPoints.save(new AccessPoint(AP_FLOOR_1, "trottier", 1));
    LarpProperties props =
        new LarpProperties("key", new LarpProperties.Cors("http://x"), 120, 2.5, 60000, 5);
    service = new JpaBuildingQueryService(
        new BusynessCalculator(batches, floors, accessPoints, props),
        Clock.fixed(NOW, ZoneOffset.UTC)
    );
  }

  @Test
  void listsTrottier() {
    assertEquals(
        List.of(new BuildingResponse("trottier", "Trottier Building")),
        service.listBuildings()
    );
  }

  @Test
  void listsFloorsAtTheClockInstant() {
    Instant recent = NOW.minusSeconds(30);
    batches.save(new Batch(SCANNER, AP_FLOOR_1, recent, recent, new int[85]));

    assertEquals(
        List.of(
            new FloorBusynessResponse(1, 120, true, 34, 28, BusynessLevel.QUIET),
            new FloorBusynessResponse(2, 90, false, 0, 0, BusynessLevel.NO_DATA)
        ),
        service.listFloors("trottier")
    );
  }

  @Test
  void rejectsUnknownBuilding() {
    assertThrows(BuildingNotFoundException.class, () -> service.listFloors("redpath"));
  }
}
