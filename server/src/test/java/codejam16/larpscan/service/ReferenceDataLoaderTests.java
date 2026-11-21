package codejam16.larpscan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Floor;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.FloorRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;

/**
 * Parses small in-memory CSVs and reloads them into H2, then checks the committed CSVs load.
 */
@DataJpaTest
@ActiveProfiles("test") //enable test profile
class ReferenceDataLoaderTests {

  private static final Set<Integer> FLOORS_0_TO_2 = Set.of(0, 1, 2);

  @Autowired private TestEntityManager em;
  @Autowired private FloorRepository floors;
  @Autowired private AccessPointRepository accessPoints;

  private ReferenceDataLoader loader;

  @BeforeEach
  void setUp() {
    loader = new ReferenceDataLoader(floors, accessPoints);
  }

  @Test
  void parsesFloorsAfterHeader() {
    List<Floor> parsed = ReferenceDataLoader.parseFloors(csv("floor,capacity\n0,80\n1,120\n"));

    assertEquals(2, parsed.size());
    assertEquals("trottier", parsed.get(0).getBuildingId());
    assertEquals(0, parsed.get(0).getFloor());
    assertEquals(80, parsed.get(0).getCapacity());
    assertEquals(1, parsed.get(1).getFloor());
    assertEquals(120, parsed.get(1).getCapacity());
  }

  @Test
  void parsesAccessPointsWithCrlfAndLowercasesBssid() {
    List<AccessPoint> parsed = ReferenceDataLoader.parseAccessPoints(
        csv("bssid,floor\r\n44:48:C1:A4:2B:60,0\r\naa:bb:cc:dd:ee:ff,2\r\n\r\n"), FLOORS_0_TO_2
    );

    assertEquals(2, parsed.size());
    assertEquals("44:48:c1:a4:2b:60", parsed.get(0).getBssid());
    assertEquals("trottier", parsed.get(0).getBuildingId());
    assertEquals(0, parsed.get(0).getFloor());
    assertEquals("aa:bb:cc:dd:ee:ff", parsed.get(1).getBssid());
    assertEquals(2, parsed.get(1).getFloor());
  }

  @Test
  void rejectsWrongHeader() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ReferenceDataLoader.parseFloors(csv("level,seats\n0,80\n")));

    assertTrue(e.getMessage().contains("expected header 'floor,capacity'"), e.getMessage());
  }

  @Test
  void rejectsNonIntegerFloorWithLineNumber() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ReferenceDataLoader.parseFloors(csv("floor,capacity\n0,80\nthree,120\n")));

    assertTrue(e.getMessage().contains("line 3: floor is not an integer"), e.getMessage());
  }

  @Test
  void rejectsRowWithWrongColumnCount() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ReferenceDataLoader.parseAccessPoints(
            csv("bssid,floor\naa:bb:cc:dd:ee:ff,1,extra\n"), FLOORS_0_TO_2));

    assertTrue(e.getMessage().contains("line 2: expected 2 columns but got 3"), e.getMessage());
  }

  @Test
  void rejectsInvalidBssid() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ReferenceDataLoader.parseAccessPoints(
            csv("bssid,floor\nnot-a-bssid,1\n"), FLOORS_0_TO_2));

    assertTrue(e.getMessage().contains("invalid bssid 'not-a-bssid'"), e.getMessage());
  }

  @Test
  void rejectsAccessPointOnUnknownFloor() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ReferenceDataLoader.parseAccessPoints(
            csv("bssid,floor\naa:bb:cc:dd:ee:ff,9\n"), FLOORS_0_TO_2));

    assertTrue(e.getMessage().contains("floor 9 is not in floors.csv"), e.getMessage());
  }

  @Test
  void rejectsDuplicateBssidAfterLowercasing() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> ReferenceDataLoader.parseAccessPoints(
            csv("bssid,floor\naa:bb:cc:dd:ee:ff,1\nAA:BB:CC:DD:EE:FF,2\n"), FLOORS_0_TO_2));

    assertTrue(e.getMessage().contains("duplicate bssid aa:bb:cc:dd:ee:ff"), e.getMessage());
  }

  @Test
  void reloadReplacesExistingRowsInsteadOfDuplicating() {
    floors.save(new Floor("trottier", 0, 999));
    floors.save(new Floor("trottier", 7, 50));
    accessPoints.save(new AccessPoint("aa:bb:cc:dd:ee:ff", "trottier", 7));
    accessPoints.save(new AccessPoint("11:22:33:44:55:66", "trottier", 0));
    em.flush();

    loader.reload(
        csv("floor,capacity\n0,80\n1,120\n"),
        csv("bssid,floor\naa:bb:cc:dd:ee:ff,1\n")
    );
    em.flush();
    em.clear();

    List<Floor> allFloors = floors.findAll();
    allFloors.sort(Comparator.comparingInt(Floor::getFloor));
    assertEquals(2, allFloors.size());
    assertEquals(80, allFloors.get(0).getCapacity());
    assertEquals(1, allFloors.get(1).getFloor());

    List<AccessPoint> allAps = accessPoints.findAll();
    assertEquals(1, allAps.size());
    assertEquals("aa:bb:cc:dd:ee:ff", allAps.get(0).getBssid());
    assertEquals(1, allAps.get(0).getFloor());
  }

  @Test
  void malformedCsvLeavesExistingRowsUntouched() {
    floors.save(new Floor("trottier", 0, 999));
    em.flush();

    assertThrows(IllegalStateException.class, () -> loader.reload(
        csv("floor,capacity\n0,80\n"),
        csv("bssid,floor\nbad,0\n")
    ));

    assertEquals(1, floors.count());
    assertEquals(999, floors.findAll().get(0).getCapacity());
  }

  @Test
  void loadsCommittedCsvs() throws IOException {
    ClassPathResource apsCsv = new ClassPathResource("data/trottier.csv");

    loader.reload(new ClassPathResource("data/floors.csv"), apsCsv);
    em.flush();
    em.clear();

    assertEquals(6, floors.count());
    assertEquals(dataLineCount(apsCsv), accessPoints.count());
  }

  private static Resource csv(String content) {
    return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
  }

  /** Non-blank lines after the header. */
  private static long dataLineCount(Resource csv) throws IOException {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
      return reader.lines().skip(1).filter(line -> !line.isBlank()).count();
    }
  }
}
