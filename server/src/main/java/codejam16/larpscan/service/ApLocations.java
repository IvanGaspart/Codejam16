package codejam16.larpscan.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * In-memory GPS positions of the Trottier Access Point radios, joined at startup from
 * {@code data/ap_bssid_trottier.csv} ({@code ID,floor_number,bssid}) and
 * {@code data/ap_gps_trottier.csv} ({@code ID,latitude,longitude,floor_number}) on
 * {@code (ID, floor_number)}. Rows without a partner in the other file are skipped; a malformed
 * row aborts startup with a message naming the file and line.
 *
 * <p>Locations are keyed by {@linkplain #radioKey radio key} and floor: one AP broadcasts several
 * SSIDs whose BSSIDs share the first five octets, so any of them finds the AP. Different APs on
 * different floors can share a key (e.g. {@code 44:48:c1:a4:34} on floors 3 and 5), so a lookup
 * also names the floor. APs sharing a key <em>and</em> a floor are logged and merged into their
 * centroid.
 */
@Component
public class ApLocations {

  private static final Logger log = LoggerFactory.getLogger(ApLocations.class);

  private static final String BSSIDS_HEADER = "ID,floor_number,bssid";
  private static final String GPS_HEADER = "ID,latitude,longitude,floor_number";
  private static final Pattern BSSID = Pattern.compile("([0-9a-f]{2}:){5}[0-9a-f]{2}");

  /**
   * Where one Access Point radio is.
   *
   * @param floor floor number within the Building
   * @param latitude WGS84 latitude in degrees
   * @param longitude WGS84 longitude in degrees
   */
  public record ApLocation(int floor, double latitude, double longitude) {}

  /** Radio key → one location per floor. */
  private final Map<String, List<ApLocation>> byRadio;

  /** Loads the committed CSVs from the classpath. */
  public ApLocations() {
    this(new ClassPathResource("data/ap_bssid_trottier.csv"),
        new ClassPathResource("data/ap_gps_trottier.csv"));
  }

  /** Loads the given CSVs; for tests. */
  ApLocations(Resource bssidsCsv, Resource gpsCsv) {
    this.byRadio = join(bssidsCsv, gpsCsv);
  }

  /**
   * Radio key of a BSSID: its first five octets (the last octet varies per SSID of one AP).
   *
   * @param bssid lowercase colon-separated BSSID
   * @return everything before the last colon, e.g. {@code 44:48:c1:a4:1a}
   */
  public static String radioKey(String bssid) {
    return bssid.substring(0, 14);
  }

  /**
   * Looks up an AP on one floor.
   *
   * @param radioKey the AP's {@linkplain #radioKey key}
   * @param floor floor number within the Building
   * @return the AP's location, if one with this key is known on {@code floor}
   */
  public Optional<ApLocation> find(String radioKey, int floor) {
    return byRadio.getOrDefault(radioKey, List.of()).stream()
        .filter(loc -> loc.floor() == floor)
        .findFirst();
  }

  /** Number of located APs, counting each (key, floor) once. */
  public int size() {
    return byRadio.values().stream().mapToInt(List::size).sum();
  }

  static Map<String, List<ApLocation>> join(Resource bssidsCsv, Resource gpsCsv) {
    Map<String, String> bssidById = new HashMap<>();
    for (String[] f : readRows(bssidsCsv, BSSIDS_HEADER, (row, fields) -> {
      parseInt(row, fields[0], "ID");
      parseInt(row, fields[1], "floor_number");
      String bssid = fields[2].toLowerCase(Locale.ROOT);
      if (!BSSID.matcher(bssid).matches()) {
        throw row.error("invalid bssid '" + fields[2] + "'");
      }
      fields[2] = bssid;
    })) {
      if (bssidById.put(idAndFloor(f[0], f[1]), f[2]) != null) {
        throw new IllegalStateException(
            bssidsCsv.getFilename() + ": duplicate ID,floor_number " + f[0] + "," + f[1]);
      }
    }

    Map<String, Map<Integer, List<ApLocation>>> grouped = new HashMap<>();
    int unmatched = 0;
    List<String[]> gpsRows = readRows(gpsCsv, GPS_HEADER, (row, fields) -> {
      parseInt(row, fields[0], "ID");
      parseDouble(row, fields[1], "latitude");
      parseDouble(row, fields[2], "longitude");
      parseInt(row, fields[3], "floor_number");
    });
    for (String[] f : gpsRows) {
      int floor = Integer.parseInt(f[3]);
      String bssid = bssidById.remove(idAndFloor(f[0], f[3]));
      if (bssid == null) {
        unmatched++;
        continue;
      }
      grouped.computeIfAbsent(radioKey(bssid), k -> new HashMap<>())
          .computeIfAbsent(floor, fl -> new ArrayList<>())
          .add(new ApLocation(floor, Double.parseDouble(f[1]), Double.parseDouble(f[2])));
    }
    unmatched += bssidById.size();

    Map<String, List<ApLocation>> result = new HashMap<>();
    int located = 0;
    for (Map.Entry<String, Map<Integer, List<ApLocation>>> byKey : grouped.entrySet()) {
      List<ApLocation> perFloor = new ArrayList<>();
      for (Map.Entry<Integer, List<ApLocation>> sameFloor : byKey.getValue().entrySet()) {
        List<ApLocation> aps = sameFloor.getValue();
        if (aps.size() > 1) {
          log.warn("{} APs share radio key {} on floor {}; using their centroid",
              aps.size(), byKey.getKey(), sameFloor.getKey());
        }
        perFloor.add(new ApLocation(
            sameFloor.getKey(),
            aps.stream().mapToDouble(ApLocation::latitude).average().orElseThrow(),
            aps.stream().mapToDouble(ApLocation::longitude).average().orElseThrow()
        ));
      }
      located += perFloor.size();
      result.put(byKey.getKey(), List.copyOf(perFloor));
    }
    log.info("Loaded {} AP locations ({} rows without a partner skipped)", located, unmatched);
    return Map.copyOf(result);
  }

  /** Join key, normalized so {@code 05} and {@code 5} match. */
  private static String idAndFloor(String id, String floor) {
    return Integer.parseInt(id) + "," + Integer.parseInt(floor);
  }

  private interface RowCheck {
    void check(Row row, String[] fields);
  }

  /** Reads a CSV with an exact header and column count, validating each row as it goes. */
  private static List<String[]> readRows(Resource csv, String expectedHeader, RowCheck check) {
    String file = csv.getFilename() != null ? csv.getFilename() : csv.getDescription();
    int columns = expectedHeader.split(",").length;
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
      String header = reader.readLine();
      if (header == null || !header.strip().equals(expectedHeader)) {
        throw new IllegalStateException(
            file + ": expected header '" + expectedHeader + "' but got '" + header + "'");
      }
      List<String[]> rows = new ArrayList<>();
      int lineNumber = 1;
      String line;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        Row row = new Row(file, lineNumber, line);
        String[] fields = line.strip().split(",", -1);
        if (fields.length != columns) {
          throw row.error("expected " + columns + " columns but got " + fields.length);
        }
        for (int i = 0; i < fields.length; i++) {
          fields[i] = fields[i].strip();
        }
        check.check(row, fields);
        rows.add(fields);
      }
      return rows;
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + file, e);
    }
  }

  private static void parseInt(Row row, String value, String column) {
    try {
      Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw row.error(column + " is not an integer");
    }
  }

  private static void parseDouble(Row row, String value, String column) {
    try {
      Double.parseDouble(value);
    } catch (NumberFormatException e) {
      throw row.error(column + " is not a number");
    }
  }

  /** Position of a data line, for error messages. */
  private record Row(String file, int lineNumber, String line) {

    IllegalStateException error(String reason) {
      return new IllegalStateException(
          file + " line " + lineNumber + ": " + reason + " in '" + line + "'");
    }
  }
}
