package codejam16.larpscan.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.service.ScannerLocator.Position;
import codejam16.larpscan.web.dto.BatchRequest.ApReading;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

/**
 * Trilaterates against three APs on floor 3 laid out in local metres around {@code (LAT0, LON0)}:
 * AP 1 at the origin, AP 2 10 m east, AP 3 10 m north; AP 4 sits 10 m east on floor 5.
 */
class ScannerLocatorTests {

  private static final double LAT0 = 45.5;
  private static final double LON0 = -73.5;
  private static final double EPS = 1e-9;

  private static final String AP1 = "aa:aa:aa:aa:01:10";
  private static final String AP2 = "aa:aa:aa:aa:02:20";
  private static final String AP3 = "aa:aa:aa:aa:03:30";
  private static final String AP4 = "aa:aa:aa:aa:04:40";

  private final ScannerLocator locator = new ScannerLocator(apLocations(), locatorProps());

  @Test
  void solvesThreeCirclesExactly() {
    // Distances from (3, 4)
    double[] xy = ScannerLocator.trilaterate(
        new double[] {0, 10, 0}, new double[] {0, 0, 10},
        new double[] {5, Math.sqrt(65), Math.sqrt(45)});

    assertArrayEquals(new double[] {3, 4}, xy, EPS);
  }

  @Test
  void rejectsCollinearCentresAndPointsOutsideTheBox() {
    assertNull(ScannerLocator.trilaterate(
        new double[] {0, 5, 10}, new double[] {0, 0, 0}, new double[] {3, 3, 3}));
    // Distances from (20, 20)
    assertNull(ScannerLocator.trilaterate(
        new double[] {0, 10, 0}, new double[] {0, 0, 10},
        new double[] {Math.sqrt(800), Math.sqrt(500), Math.sqrt(500)}));
  }

  @Test
  void locatesFromThreeApsAndIgnoresOtherFloorsAndSsids() {
    // Equal RSSIs => equal distances => circumcentre (5, 5), not the centroid (3.3, 3.3)
    Position p = locator.locate(3, List.of(
        new ApReading(AP1, -60), new ApReading("aa:aa:aa:aa:01:ff", -70),
        new ApReading(AP2, -60), new ApReading(AP3, -60), new ApReading(AP4, -30)
    )).orElseThrow();

    assertArrayEquals(new double[] {5, 5}, local(p), 1e-6);
  }

  @Test
  void fallsBackToWeightedCentroidWhenTheSolutionLeavesTheBox() {
    // d = 1, 1, 21.5 m: the circle equations solve to (5, -18.2), south of all three APs
    Position p = locator.locate(3, List.of(
        new ApReading(AP1, -40), new ApReading(AP2, -40), new ApReading(AP3, -80)
    )).orElseThrow();

    double[] ds = {1, 1, locator.distance(-80)};
    double[] xs = {0, 10, 0};
    double[] ys = {0, 0, 10};
    assertNull(ScannerLocator.trilaterate(xs, ys, ds));
    assertArrayEquals(ScannerLocator.weightedCentroid(xs, ys, ds), local(p), 1e-6);
  }

  @Test
  void singleApGivesItsOwnPosition() {
    Position p = locator.locate(3, List.of(new ApReading(AP2, -55))).orElseThrow();

    assertArrayEquals(new double[] {10, 0}, local(p), 1e-6);
  }

  @Test
  void resolvesAKeySharedAcrossFloorsByTheScannersFloor() {
    ApLocations shared = new ApLocations(
        new ByteArrayResource(("ID,floor_number,bssid\n14,3,44:48:c1:a4:34:20\n"
            + "3,5,44:48:c1:a4:34:e0\n").getBytes(StandardCharsets.UTF_8)),
        new ByteArrayResource(("ID,latitude,longitude,floor_number\n14,45.5,-73.5,3\n"
            + "3,45.6,-73.6,5\n").getBytes(StandardCharsets.UTF_8)));
    ScannerLocator sharedLocator = new ScannerLocator(shared, locatorProps());
    List<ApReading> heard = List.of(new ApReading("44:48:c1:a4:34:e3", -50));

    assertEquals(new Position(45.5, -73.5), sharedLocator.locate(3, heard).orElseThrow());
    assertEquals(new Position(45.6, -73.6), sharedLocator.locate(5, heard).orElseThrow());
  }

  @Test
  void noLocatedApOnTheFloorGivesNoPosition() {
    assertTrue(locator.locate(3, List.of(new ApReading(AP4, -40))).isEmpty());
    assertTrue(locator.locate(3, List.of()).isEmpty());
  }

  @Test
  void convertsRssiToDistanceWithThePathLossModel() {
    assertEquals(1, locator.distance(-40), EPS);
    assertEquals(10, locator.distance(-70), EPS);
  }

  private static LarpProperties locatorProps() {
    return new LarpProperties("key", new LarpProperties.Cors("http://x"), 120, 2.5, 60000, 5,
        3.0, -65, 8.0, -80, 0.8, -40, 3.0);
  }

  private static double[] local(Position p) {
    return ScannerLocator.project(p.latitude(), p.longitude(), LAT0, LON0);
  }

  private static ApLocations apLocations() {
    Position east = ScannerLocator.unproject(10, 0, LAT0, LON0);
    Position north = ScannerLocator.unproject(0, 10, LAT0, LON0);
    String bssids = "ID,floor_number,bssid\n1,3," + AP1 + "\n2,3," + AP2 + "\n3,3," + AP3
        + "\n4,5," + AP4 + "\n";
    String gps = "ID,latitude,longitude,floor_number\n"
        + "1," + LAT0 + "," + LON0 + ",3\n"
        + "2," + east.latitude() + "," + east.longitude() + ",3\n"
        + "3," + north.latitude() + "," + north.longitude() + ",3\n"
        + "4," + east.latitude() + "," + east.longitude() + ",5\n";
    return new ApLocations(
        new ByteArrayResource(bssids.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayResource(gps.getBytes(StandardCharsets.UTF_8)));
  }
}
