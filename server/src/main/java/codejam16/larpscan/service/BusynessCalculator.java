package codejam16.larpscan.service;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.domain.Floor;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.repository.FloorRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes unsmoothed per-Floor Busyness and coverage from each Scanner's latest batch in
 * {@code WINDOW} (RQ-02, RQ-10; see "Busyness" in {@code docs/SPEC.md}). This is the only place the
 * raw calculation lives; the {@code /floors} endpoint and the snapshot job both build on it.
 *
 * <p>Each Scanner's latest batch is chosen among <em>all</em> its batches in the window, including
 * ones with an unknown BSSID. A Scanner whose latest batch has an unknown BSSID therefore counts on
 * no Floor, even if an older batch in the window was on a known one: it has moved somewhere the
 * server cannot place.
 *
 * <p>Device estimate per Floor: each Scanner's RSSIs are split into strata, {@code c} devices at
 * {@code rssi ≥ CLOSE_RSSI} (spread over the close disc of radius {@code CLOSE_D}) and {@code b} at
 * {@code FAR_RSSI ≤ rssi < CLOSE_RSSI} (spread over the annulus out to {@code FAR_D}), for a weight
 * {@code w = c + FAR_DOWNSCALE_FACTOR × b}. Positioned Scanners are merged by integrating, over
 * the union of their far discs, the sum of their device densities divided by how many far discs
 * cover each point, so devices heard by several Scanners count once (see {@link #union}).
 * Positionless Scanners (no trilaterated position) may overlap anything, so the Floor's estimate
 * is {@code max(union, max w of positionless Scanners)}.
 *
 * <p>Rounding: {@code estimatedPeople} and {@code busynessPct} are both rounded half up, and the
 * percentage is computed from the unrounded people estimate, so it is not rounded twice. A Floor
 * with non-positive capacity reports {@code 0}%.
 */
@Service
public class BusynessCalculator {

  /** Integration grid step in metres. */
  static final double GRID_STEP_M = 0.25;

  private final BatchRepository batches;
  private final FloorRepository floors;
  private final AccessPointRepository accessPoints;
  private final LarpProperties properties;

  public BusynessCalculator(
      BatchRepository batches,
      FloorRepository floors,
      AccessPointRepository accessPoints,
      LarpProperties properties
  ) {
    this.batches = batches;
    this.floors = floors;
    this.accessPoints = accessPoints;
    this.properties = properties;
  }

  /**
   * Computes the unsmoothed Busyness of every Floor in a Building at instant {@code at}, using the
   * batches received in {@code (at − WINDOW, at]}.
   *
   * @param buildingId building identifier
   * @param at the instant to compute for
   * @return one entry per Floor of the Building, ordered by floor number (empty if it has none)
   */
  @Transactional(readOnly = true)
  public List<FloorBusyness> calculate(String buildingId, Instant at) {
    Map<String, Integer> floorByBssid = accessPoints.findByBuildingId(buildingId).stream()
        .collect(Collectors.toMap(AccessPoint::getBssid, AccessPoint::getFloor));

    Map<Integer, List<Batch>> latestByFloor = new HashMap<>();
    for (Batch latest : latestPerScanner(at)) {
      Integer floor = floorByBssid.get(latest.getBssid());
      if (floor == null) {
        continue;
      }
      latestByFloor.computeIfAbsent(floor, f -> new ArrayList<>()).add(latest);
    }

    return floors.findByBuildingIdOrderByFloorAsc(buildingId).stream()
        .map(f -> {
          List<Batch> onFloor = latestByFloor.getOrDefault(f.getFloor(), List.of());
          return toBusyness(f, onFloor.size(), estimateDevices(onFloor));
        })
        .toList();
  }

  /**
   * Estimated distinct devices on one Floor from its Scanners' latest batches.
   *
   * @param onFloor latest batch of each Scanner on the Floor
   * @return {@code max(union of positioned Scanners, max weight of positionless ones)}
   */
  double estimateDevices(List<Batch> onFloor) {
    List<Batch> positioned = new ArrayList<>();
    double positionless = 0;
    for (Batch b : onFloor) {
      if (b.getLatitude() != null && b.getLongitude() != null) {
        positioned.add(b);
      } else {
        positionless = Math.max(positionless, close(b) + properties.farDownscaleFactor() * far(b));
      }
    }
    return Math.max(union(positioned), positionless);
  }

  /**
   * {@code ∫ (Σ_s ρ_s(x)) / k(x) dx} over the union of the Scanners' far discs, where
   * {@code ρ_s} is Scanner {@code s}'s device density and {@code k(x)} the number of far discs
   * covering {@code x}. Without overlap this is {@code Σ w_s}. Computed per Scanner on a
   * {@value #GRID_STEP_M} m grid over its own far disc as {@code w_s × Σ(ρ_s/k) / Σ ρ_s}, so the
   * grid's discretization error cancels out.
   */
  private double union(List<Batch> positioned) {
    int n = positioned.size();
    if (n == 0) {
      return 0;
    }
    double closeD = properties.closeD();
    double farD = properties.farD();
    double alpha = properties.farDownscaleFactor();
    double lat0 = positioned.get(0).getLatitude();
    double lon0 = positioned.get(0).getLongitude();
    double[] xs = new double[n];
    double[] ys = new double[n];
    for (int i = 0; i < n; i++) {
      Batch b = positioned.get(i);
      double[] p = ScannerLocator.project(b.getLatitude(), b.getLongitude(), lat0, lon0);
      xs[i] = p[0];
      ys[i] = p[1];
    }

    int cells = (int) Math.ceil(2 * farD / GRID_STEP_M);
    double total = 0;
    for (int s = 0; s < n; s++) {
      int c = close(positioned.get(s));
      int b = far(positioned.get(s));
      double closeDensity = c / (Math.PI * closeD * closeD);
      double farDensity = alpha * b / (Math.PI * (farD * farD - closeD * closeD));
      double num = 0;
      double den = 0;
      for (int i = 0; i < cells; i++) {
        double dx = -farD + (i + 0.5) * GRID_STEP_M;
        for (int j = 0; j < cells; j++) {
          double dy = -farD + (j + 0.5) * GRID_STEP_M;
          double r2 = dx * dx + dy * dy;
          if (r2 > farD * farD) {
            continue;
          }
          double rho = r2 <= closeD * closeD ? closeDensity : farDensity;
          if (rho == 0) {
            continue;
          }
          int k = 0;
          for (int t = 0; t < n; t++) {
            double ex = xs[s] + dx - xs[t];
            double ey = ys[s] + dy - ys[t];
            if (ex * ex + ey * ey <= farD * farD) {
              k++;
            }
          }
          num += rho / k;
          den += rho;
        }
      }
      if (den > 0) {
        total += (c + alpha * b) * num / den;
      }
    }
    return total;
  }

  /** Devices in the close stratum: {@code rssi ≥ CLOSE_RSSI}. */
  private int close(Batch batch) {
    int count = 0;
    for (int rssi : batch.getRssis()) {
      if (rssi >= properties.closeRssi()) {
        count++;
      }
    }
    return count;
  }

  /** Devices in the far stratum: {@code FAR_RSSI ≤ rssi < CLOSE_RSSI}. */
  private int far(Batch batch) {
    int count = 0;
    for (int rssi : batch.getRssis()) {
      if (rssi >= properties.farRssi() && rssi < properties.closeRssi()) {
        count++;
      }
    }
    return count;
  }

  /** Each Scanner's most recent batch in the window; ties on receive time go to the higher id. */
  private Collection<Batch> latestPerScanner(Instant at) {
    Instant after = at.minusSeconds(properties.windowSeconds());
    Comparator<Batch> byRecency =
        Comparator.comparing(Batch::getReceivedAt).thenComparing(Batch::getId);
    Map<UUID, Batch> latest = new HashMap<>();
    for (Batch b : batches.findByReceivedAtGreaterThanAndReceivedAtLessThanEqual(after, at)) {
      latest.merge(b.getScannerId(), b, (x, y) -> byRecency.compare(x, y) >= 0 ? x : y);
    }
    return latest.values();
  }

  private FloorBusyness toBusyness(Floor floor, int scanners, double devices) {
    if (scanners == 0) {
      return new FloorBusyness(
          floor.getBuildingId(), floor.getFloor(), floor.getCapacity(), false, 0, 0, 0.0, 0, 0
      );
    }
    double people = devices / properties.k();
    int pct = pctOf(people, floor.getCapacity());
    return new FloorBusyness(
        floor.getBuildingId(),
        floor.getFloor(),
        floor.getCapacity(),
        true,
        scanners,
        devices,
        people,
        (int) Math.round(people),
        pct
    );
  }

  /** {@code min(100, 100 × people / capacity)} rounded half up; {@code 0} for non-positive capacity. */
  static int pctOf(double people, int capacity) {
    return capacity > 0 ? (int) Math.min(100, Math.round(100 * people / capacity)) : 0;
  }
}
