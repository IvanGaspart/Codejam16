package codejam16.larpscan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codejam16.larpscan.service.ApLocations.ApLocation;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/**
 * Joins small in-memory AP CSVs, then checks the committed ones load.
 */
class ApLocationsTests {

  private static final String BSSIDS_HEADER = "ID,floor_number,bssid\n";
  private static final String GPS_HEADER = "ID,latitude,longitude,floor_number\n";

  @Test
  void joinsOnIdAndFloorKeyedByRadio() {
    ApLocations locations = new ApLocations(
        csv(BSSIDS_HEADER + "1,3,44:48:C1:A4:1A:50\n1,5,aa:bb:cc:dd:ee:f0\n2,3,11:22:33:44:55:60\n"),
        csv(GPS_HEADER + "1,45.5,-73.5,3\r\n1,45.6,-73.6,5\r\n9,45.7,-73.7,3\r\n\r\n")
    );

    assertEquals(2, locations.size()); // AP 2 (no GPS) and AP 9 (no BSSID) are skipped
    assertEquals(Optional.of(new ApLocation(3, 45.5, -73.5)),
        locations.find(ApLocations.radioKey("44:48:c1:a4:1a:ff"), 3));
    assertEquals(Optional.of(new ApLocation(5, 45.6, -73.6)),
        locations.find(ApLocations.radioKey("aa:bb:cc:dd:ee:00"), 5));
    assertTrue(locations.find(ApLocations.radioKey("44:48:c1:a4:1a:50"), 5).isEmpty());
    assertTrue(locations.find(ApLocations.radioKey("11:22:33:44:55:60"), 3).isEmpty());
  }

  @Test
  void keepsApsSharingAKeyOnDifferentFloorsApart() {
    // Real collision in the committed data: 44:48:c1:a4:34:20 (floor 3) and :34:e0 (floor 5)
    ApLocations locations = new ApLocations(
        csv(BSSIDS_HEADER + "14,3,44:48:c1:a4:34:20\n3,5,44:48:c1:a4:34:e0\n"),
        csv(GPS_HEADER + "14,45.5,-73.5,3\n3,45.6,-73.6,5\n")
    );

    assertEquals(2, locations.size());
    assertEquals(Optional.of(new ApLocation(3, 45.5, -73.5)), locations.find("44:48:c1:a4:34", 3));
    assertEquals(Optional.of(new ApLocation(5, 45.6, -73.6)), locations.find("44:48:c1:a4:34", 5));
  }

  @Test
  void mergesApsSharingAKeyAndFloorIntoTheirCentroid() {
    ApLocations locations = new ApLocations(
        csv(BSSIDS_HEADER + "1,3,44:48:c1:a4:34:20\n2,3,44:48:c1:a4:34:e0\n"),
        csv(GPS_HEADER + "1,45.5,-73.5,3\n2,45.7,-73.7,3\n")
    );

    assertEquals(1, locations.size());
    ApLocation merged = locations.find("44:48:c1:a4:34", 3).orElseThrow();
    assertEquals(45.6, merged.latitude(), 1e-9);
    assertEquals(-73.6, merged.longitude(), 1e-9);
  }

  @Test
  void radioKeyIsTheFirstFiveOctets() {
    assertEquals("44:48:c1:a4:1a", ApLocations.radioKey("44:48:c1:a4:1a:52"));
  }

  @Test
  void rejectsWrongHeader() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> new ApLocations(csv("id,floor,bssid\n"), csv(GPS_HEADER)));

    assertTrue(e.getMessage().contains("expected header 'ID,floor_number,bssid'"), e.getMessage());
  }

  @Test
  void rejectsNonNumericLatitudeWithLineNumber() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> new ApLocations(csv(BSSIDS_HEADER), csv(GPS_HEADER + "1,north,-73.5,3\n")));

    assertTrue(e.getMessage().contains("line 2: latitude is not a number"), e.getMessage());
  }

  @Test
  void rejectsInvalidBssid() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> new ApLocations(csv(BSSIDS_HEADER + "1,3,not-a-bssid\n"), csv(GPS_HEADER)));

    assertTrue(e.getMessage().contains("invalid bssid 'not-a-bssid'"), e.getMessage());
  }

  @Test
  void loadsCommittedCsvs() {
    ApLocations locations = new ApLocations();

    assertEquals(16, locations.size());
    assertTrue(locations.find("44:48:c1:a4:1a", 3).isPresent());
    assertTrue(locations.find("44:48:c1:a4:34", 3).isPresent());
    assertTrue(locations.find("44:48:c1:a4:34", 5).isPresent());
  }

  private static Resource csv(String content) {
    return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
  }
}
