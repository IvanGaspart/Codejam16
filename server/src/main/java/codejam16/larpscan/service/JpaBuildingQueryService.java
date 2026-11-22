package codejam16.larpscan.service;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.domain.BusynessLevel;
import codejam16.larpscan.domain.BusynessSnapshot;
import codejam16.larpscan.repository.BusynessSnapshotRepository;
import codejam16.larpscan.repository.FloorRepository;
import codejam16.larpscan.web.dto.BuildingResponse;
import codejam16.larpscan.web.dto.FloorBusynessResponse;
import codejam16.larpscan.web.dto.FloorHistoryResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link BuildingQueryService} backed by {@link BusynessCalculator}, the stored
 * {@link BusynessSnapshot}s and {@link FloorHistoryService}. Trottier is the only Building, so it is a constant rather than a
 * table.
 *
 * <p>Smoothing (RQ-07): {@code busynessPct} is the mean of the covered snapshots among the Floor's
 * last {@code SMOOTHING_SNAPSHOTS} snapshots, rounded half up, and {@code level} is derived from
 * it. Uncovered snapshots are left out so their zeros do not drag the mean down; if none of the
 * recent snapshots is covered (e.g. just after startup), the live unsmoothed percentage is used.
 *
 * <p>{@code covered} (and so {@code NO_DATA}), {@code scanners}, and {@code estimatedPeople} come
 * from the live calculation at the request instant, not from snapshots, so a Floor shows "No data"
 * as soon as it loses coverage.
 */
@Service
public class JpaBuildingQueryService implements BuildingQueryService {

  /** Identifier of the Trottier Building, the only one monitored (RQ-09). */
  static final String TROTTIER_ID = "trottier";

  private static final BuildingResponse TROTTIER =
      new BuildingResponse(TROTTIER_ID, "Trottier Building");

  private final BusynessCalculator calculator;
  private final FloorHistoryService history;
  private final FloorRepository floors;
  private final BusynessSnapshotRepository snapshots;
  private final LarpProperties properties;
  private final Clock clock;

  public JpaBuildingQueryService(
      BusynessCalculator calculator,
      FloorHistoryService history,
      FloorRepository floors,
      BusynessSnapshotRepository snapshots,
      LarpProperties properties,
      Clock clock
  ) {
    this.calculator = calculator;
    this.history = history;
    this.floors = floors;
    this.snapshots = snapshots;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public List<BuildingResponse> listBuildings() {
    return List.of(TROTTIER);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FloorBusynessResponse> listFloors(String buildingId) {
    requireTrottier(buildingId);
    return calculator.calculate(buildingId, clock.instant()).stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  public List<FloorHistoryResponse> getFloorHistory(String buildingId, int floor, Instant since) {
    requireTrottier(buildingId);
    if (!floors.existsByBuildingIdAndFloor(buildingId, floor)) {
      throw new FloorNotFoundException(buildingId, floor);
    }
    return history.history(buildingId, floor, since).stream()
        .map(s -> new FloorHistoryResponse(s.getAt(), s.getBusynessPct(), s.isCovered()))
        .toList();
  }

  private static void requireTrottier(String buildingId) {
    if (!TROTTIER.id().equals(buildingId)) {
      throw new BuildingNotFoundException(buildingId);
    }
  }

  private FloorBusynessResponse toResponse(FloorBusyness live) {
    int pct = live.covered() ? smoothedPct(live) : live.busynessPct();
    return new FloorBusynessResponse(
        live.floor(),
        live.capacity(),
        live.covered(),
        live.scanners(),
        live.estimatedPeople(),
        pct,
        BusynessLevel.from(live.covered(), pct)
    );
  }

  /** Mean of the covered snapshots among the last {@code SMOOTHING_SNAPSHOTS}, else the live pct. */
  private int smoothedPct(FloorBusyness live) {
    OptionalDouble mean = snapshots
        .findByBuildingIdAndFloorOrderByAtDescIdDesc(
            live.buildingId(), live.floor(), Limit.of(properties.smoothingSnapshots())
        ).stream()
        .filter(BusynessSnapshot::isCovered)
        .mapToInt(BusynessSnapshot::getBusynessPct)
        .average();
    return mean.isPresent() ? (int) Math.round(mean.getAsDouble()) : live.busynessPct();
  }
}
