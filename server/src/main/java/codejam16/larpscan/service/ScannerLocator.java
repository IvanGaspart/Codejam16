package codejam16.larpscan.service;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.service.ApLocations.ApLocation;
import codejam16.larpscan.web.dto.BatchRequest.ApReading;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Trilaterates a Scanner from the Wi-Fi Access Points it hears on its own Floor.
 *
 * <p>Each AP's RSSI becomes a distance with the log-distance path-loss model
 * {@code d = 10^((AP_RSSI_AT_1M − rssi) / (10 × PATH_LOSS_EXPONENT))} metres. With the 3 strongest
 * located radios the circle equations are solved exactly. When those radios are near-collinear or
 * the solution falls outside their bounding box, and with only 1 or 2 radios, the position is the
 * centroid of the radios weighted by {@code 1 / d}.
 */
@Component
public class ScannerLocator {

  /** Mean Earth radius in metres, for the local equirectangular projection. */
  static final double EARTH_RADIUS_M = 6_371_000;

  /** Below this {@code |det|} (m²) the three radios are treated as collinear. */
  private static final double MIN_DETERMINANT = 1e-6;

  /**
   * A WGS84 position.
   *
   * @param latitude degrees
   * @param longitude degrees
   */
  public record Position(double latitude, double longitude) {}

  private final ApLocations apLocations;
  private final LarpProperties properties;

  public ScannerLocator(ApLocations apLocations, LarpProperties properties) {
    this.apLocations = apLocations;
    this.properties = properties;
  }

  /**
   * Estimates where a Scanner is from the APs it heard.
   *
   * @param floor the Scanner's Floor; only radios located on it are used
   * @param aps APs heard by the Scanner (any SSID of a radio; the strongest per radio is kept)
   * @return the position, or empty when no heard radio is located on {@code floor}
   */
  public Optional<Position> locate(int floor, List<ApReading> aps) {
    Map<String, Integer> rssiByRadio = new HashMap<>();
    for (ApReading ap : aps) {
      rssiByRadio.merge(ApLocations.radioKey(ap.bssid()), ap.rssi(), Math::max);
    }
    List<Anchor> anchors = rssiByRadio.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
        .flatMap(e -> apLocations.find(e.getKey(), floor).stream()
            .map(loc -> new Anchor(loc, distance(e.getValue()))))
        .limit(3)
        .toList();
    if (anchors.isEmpty()) {
      return Optional.empty();
    }

    ApLocation origin = anchors.get(0).location();
    double[] xs = new double[anchors.size()];
    double[] ys = new double[anchors.size()];
    double[] ds = new double[anchors.size()];
    for (int i = 0; i < anchors.size(); i++) {
      ApLocation loc = anchors.get(i).location();
      double[] p = project(loc.latitude(), loc.longitude(), origin.latitude(), origin.longitude());
      xs[i] = p[0];
      ys[i] = p[1];
      ds[i] = anchors.get(i).distance();
    }

    double[] xy = anchors.size() == 3 ? trilaterate(xs, ys, ds) : null;
    if (xy == null) {
      xy = weightedCentroid(xs, ys, ds);
    }
    return Optional.of(unproject(xy[0], xy[1], origin.latitude(), origin.longitude()));
  }

  /** Path-loss distance in metres for an AP RSSI. */
  double distance(int rssi) {
    return Math.pow(10,
        (properties.apRssiAt1m() - rssi) / (10 * properties.pathLossExponent()));
  }

  /**
   * Exact intersection of three circles, or {@code null} if the centres are near-collinear or the
   * point lies outside their bounding box.
   */
  static double[] trilaterate(double[] xs, double[] ys, double[] ds) {
    // Subtracting circle 0 from circles 1 and 2 leaves two linear equations a·x + b·y = c.
    double a1 = 2 * (xs[1] - xs[0]);
    double b1 = 2 * (ys[1] - ys[0]);
    double c1 = sq(ds[0]) - sq(ds[1]) + sq(xs[1]) - sq(xs[0]) + sq(ys[1]) - sq(ys[0]);
    double a2 = 2 * (xs[2] - xs[0]);
    double b2 = 2 * (ys[2] - ys[0]);
    double c2 = sq(ds[0]) - sq(ds[2]) + sq(xs[2]) - sq(xs[0]) + sq(ys[2]) - sq(ys[0]);
    double det = a1 * b2 - a2 * b1;
    if (Math.abs(det) < MIN_DETERMINANT) {
      return null;
    }
    double x = (c1 * b2 - c2 * b1) / det;
    double y = (a1 * c2 - a2 * c1) / det;
    boolean inBox = x >= min(xs) && x <= max(xs) && y >= min(ys) && y <= max(ys);
    return inBox ? new double[] {x, y} : null;
  }

  static double[] weightedCentroid(double[] xs, double[] ys, double[] ds) {
    double x = 0;
    double y = 0;
    double total = 0;
    for (int i = 0; i < xs.length; i++) {
      double w = 1 / ds[i];
      x += w * xs[i];
      y += w * ys[i];
      total += w;
    }
    return new double[] {x / total, y / total};
  }

  /** Equirectangular projection to local metres {@code (east, north)} around an origin. */
  static double[] project(double lat, double lon, double lat0, double lon0) {
    return new double[] {
        Math.toRadians(lon - lon0) * Math.cos(Math.toRadians(lat0)) * EARTH_RADIUS_M,
        Math.toRadians(lat - lat0) * EARTH_RADIUS_M
    };
  }

  /** Inverse of {@link #project}. */
  static Position unproject(double x, double y, double lat0, double lon0) {
    return new Position(
        lat0 + Math.toDegrees(y / EARTH_RADIUS_M),
        lon0 + Math.toDegrees(x / (EARTH_RADIUS_M * Math.cos(Math.toRadians(lat0))))
    );
  }

  private static double sq(double v) {
    return v * v;
  }

  private static double min(double[] vs) {
    return Arrays.stream(vs).min().orElseThrow();
  }

  private static double max(double[] vs) {
    return Arrays.stream(vs).max().orElseThrow();
  }

  private record Anchor(ApLocation location, double distance) {}
}
