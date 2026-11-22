package codejam16.larpscan.service;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.domain.Floor;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.repository.FloorRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>Rounding: {@code estimatedPeople} and {@code busynessPct} are both rounded half up, and the
 * percentage is computed from the unrounded people estimate, so it is not rounded twice. A Floor
 * with non-positive capacity reports {@code 0}%.
 */
@Service
public class BusynessCalculator {

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

    Map<Integer, Integer> devicesByFloor = new HashMap<>();
    Set<Integer> coveredFloors = new HashSet<>();
    for (Batch latest : latestPerScanner(at)) {
      Integer floor = floorByBssid.get(latest.getBssid());
      if (floor == null) {
        continue;
      }
      coveredFloors.add(floor);
      devicesByFloor.merge(floor, latest.getRssis().length, Math::max);
    }

    return floors.findByBuildingIdOrderByFloorAsc(buildingId).stream()
        .map(f -> toBusyness(
            f,
            coveredFloors.contains(f.getFloor()),
            devicesByFloor.getOrDefault(f.getFloor(), 0)
        ))
        .toList();
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

  private FloorBusyness toBusyness(Floor floor, boolean covered, int devices) {
    if (!covered) {
      return new FloorBusyness(
          floor.getBuildingId(), floor.getFloor(), floor.getCapacity(), false, 0, 0, 0
      );
    }
    double people = devices / properties.k();
    int pct = floor.getCapacity() > 0
        ? (int) Math.min(100, Math.round(100 * people / floor.getCapacity()))
        : 0;
    return new FloorBusyness(
        floor.getBuildingId(),
        floor.getFloor(),
        floor.getCapacity(),
        true,
        devices,
        (int) Math.round(people),
        pct
    );
  }
}
