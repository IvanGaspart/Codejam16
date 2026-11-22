package codejam16.larpscan.service;

import codejam16.larpscan.domain.BusynessSnapshot;
import codejam16.larpscan.repository.BusynessSnapshotRepository;
import codejam16.larpscan.repository.FloorRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a Floor's Busyness snapshots for the history chart (RQ-07). When no {@code since} is
 * given, history starts at local midnight today in McGill's time zone.
 */
@Service
public class FloorHistoryService {

  /** McGill's time zone, used to find "local midnight". */
  static final ZoneId MCGILL_ZONE = ZoneId.of("America/Montreal");

  private final BusynessSnapshotRepository snapshots;
  private final FloorRepository floors;
  private final Clock clock;

  public FloorHistoryService(
      BusynessSnapshotRepository snapshots,
      FloorRepository floors,
      Clock clock
  ) {
    this.snapshots = snapshots;
    this.floors = floors;
    this.clock = clock;
  }

  /**
   * Returns a Floor's snapshots taken at or after {@code since}, oldest first. An unknown Floor
   * in a known Building has no snapshots, so it yields an empty list.
   *
   * @param buildingId building identifier
   * @param floor floor number within the Building
   * @param since inclusive lower bound, or {@code null} for local midnight today
   * @return chronological list of snapshots, possibly empty
   * @throws BuildingNotFoundException if {@code buildingId} does not exist
   */
  @Transactional(readOnly = true)
  public List<BusynessSnapshot> history(String buildingId, int floor, Instant since) {
    if (!floors.existsByBuildingId(buildingId)) {
      throw new BuildingNotFoundException(buildingId);
    }
    Instant from = since != null ? since : localMidnight();
    return snapshots.findByBuildingIdAndFloorAndAtGreaterThanEqualOrderByAtAsc(
        buildingId, floor, from
    );
  }

  private Instant localMidnight() {
    return LocalDate.now(clock.withZone(MCGILL_ZONE)).atStartOfDay(MCGILL_ZONE).toInstant();
  }
}
