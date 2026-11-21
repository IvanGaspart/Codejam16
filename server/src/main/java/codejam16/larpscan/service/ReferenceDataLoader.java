package codejam16.larpscan.service;

import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Floor;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.FloorRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces the Floor and Access Point tables with {@code data/floors.csv} and
 * {@code data/trottier.csv} at startup, in one transaction (RQ-03). Any malformed row aborts
 * startup with a message naming the file and line.
 */
@Service
public class ReferenceDataLoader implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ReferenceDataLoader.class);

  /** The only Building for now (RQ-09). */
  static final String BUILDING_ID = "trottier";

  private static final String FLOORS_HEADER = "floor,capacity";
  private static final String APS_HEADER = "bssid,floor";
  private static final Pattern BSSID = Pattern.compile("([0-9a-f]{2}:){5}[0-9a-f]{2}");

  private final FloorRepository floors;
  private final AccessPointRepository accessPoints;

  public ReferenceDataLoader(FloorRepository floors, AccessPointRepository accessPoints) {
    this.floors = floors;
    this.accessPoints = accessPoints;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    reload(new ClassPathResource("data/floors.csv"), new ClassPathResource("data/trottier.csv"));
  }

  /**
   * Deletes every Floor and Access Point row, then inserts the rows parsed from the two CSVs.
   * Both files are parsed before anything is deleted.
   */
  @Transactional
  public void reload(Resource floorsCsv, Resource accessPointsCsv) {
    List<Floor> newFloors = parseFloors(floorsCsv);
    Set<Integer> floorNumbers = new HashSet<>();
    newFloors.forEach(f -> floorNumbers.add(f.getFloor()));
    List<AccessPoint> newAccessPoints = parseAccessPoints(accessPointsCsv, floorNumbers);

    // Flush the deletes first: Hibernate otherwise runs inserts before deletes and would hit the
    // BSSID primary key and the (building_id, floor) unique constraint.
    accessPoints.deleteAll();
    floors.deleteAll();
    floors.flush();
    floors.saveAll(newFloors);
    accessPoints.saveAll(newAccessPoints);

    log.info("Loaded {} floors and {} access points for building={}",
        newFloors.size(), newAccessPoints.size(), BUILDING_ID);
  }

  static List<Floor> parseFloors(Resource csv) {
    List<Floor> result = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();
    for (Row row : readRows(csv, FLOORS_HEADER)) {
      int floor = row.parseInt(0, "floor");
      int capacity = row.parseInt(1, "capacity");
      if (capacity <= 0) {
        throw row.error("capacity must be positive");
      }
      if (!seen.add(floor)) {
        throw row.error("duplicate floor " + floor);
      }
      result.add(new Floor(BUILDING_ID, floor, capacity));
    }
    return result;
  }

  static List<AccessPoint> parseAccessPoints(Resource csv, Set<Integer> knownFloors) {
    List<AccessPoint> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Row row : readRows(csv, APS_HEADER)) {
      String bssid = row.fields[0].toLowerCase(Locale.ROOT);
      if (!BSSID.matcher(bssid).matches()) {
        throw row.error("invalid bssid '" + row.fields[0] + "'");
      }
      int floor = row.parseInt(1, "floor");
      if (!knownFloors.contains(floor)) {
        throw row.error("floor " + floor + " is not in floors.csv");
      }
      if (!seen.add(bssid)) {
        throw row.error("duplicate bssid " + bssid);
      }
      result.add(new AccessPoint(bssid, BUILDING_ID, floor));
    }
    return result;
  }

  /** Reads a two-column CSV, checking the header and skipping blank lines. */
  private static List<Row> readRows(Resource csv, String expectedHeader) {
    String file = csv.getFilename() != null ? csv.getFilename() : csv.getDescription();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
      String header = reader.readLine();
      if (header == null || !header.strip().equals(expectedHeader)) {
        throw new IllegalStateException(
            file + ": expected header '" + expectedHeader + "' but got '" + header + "'");
      }
      List<Row> rows = new ArrayList<>();
      int lineNumber = 1;
      String line;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        Row row = new Row(file, lineNumber, line, line.strip().split(",", -1));
        if (row.fields.length != 2) {
          throw row.error("expected 2 columns but got " + row.fields.length);
        }
        for (int i = 0; i < row.fields.length; i++) {
          row.fields[i] = row.fields[i].strip();
        }
        rows.add(row);
      }
      return rows;
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + file, e);
    }
  }

  /** One data line of a CSV, kept with its position for error messages. */
  private record Row(String file, int lineNumber, String line, String[] fields) {

    int parseInt(int index, String column) {
      try {
        return Integer.parseInt(fields[index]);
      } catch (NumberFormatException e) {
        throw error(column + " is not an integer");
      }
    }

    IllegalStateException error(String reason) {
      return new IllegalStateException(
          file + " line " + lineNumber + ": " + reason + " in '" + line + "'");
    }
  }
}
