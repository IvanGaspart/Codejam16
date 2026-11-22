package codejam16.larpscan.service;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.domain.BusynessLevel;
import codejam16.larpscan.domain.BusynessSnapshot;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.repository.BusynessSnapshotRepository;
import codejam16.larpscan.repository.FloorRepository;
import codejam16.larpscan.web.dto.BuildingResponse;
import codejam16.larpscan.web.dto.FloorBusynessResponse;
import codejam16.larpscan.web.dto.FloorHistoryResponse;
import codejam16.larpscan.web.dto.ScannerResponse;
import java.time.Clock;
import java.time.Duration;
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
 * <p>Smoothing (RQ-07): {@code estimatedPeople} is the mean people estimate of the covered
 * snapshots among the Floor's last {@code SMOOTHING_SNAPSHOTS} snapshots, and {@code busynessPct}
 * is computed from that mean, so the two always agree; {@code level} is derived from the
 * percentage. Uncovered snapshots are left out so their zeros do not drag the mean down; if none of
 * the recent snapshots is covered (e.g. just after startup), the live unsmoothed values are used.
 * Snapshots stored before people were recorded contribute {@code busynessPct × capacity / 100}.
 *
 * <p>{@code covered} (and so {@code NO_DATA}) and {@code scanners} come from the live calculation
 * at the request instant, not from snapshots, so a Floor shows "No data" as soon as it loses
 * coverage.
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
  private final BatchRepository batches;
  private final Clock clock;

  public JpaBuildingQueryService(
      BusynessCalculator calculator,
      FloorHistoryService history,
      FloorRepository floors,
      BusynessSnapshotRepository snapshots,
      LarpProperties properties,
      BatchRepository batches,
      Clock clock
  ) {
    this.calculator = calculator;
    this.history = history;
    this.floors = floors;
    this.snapshots = snapshots;
    this.properties = properties;
    this.batches = batches;
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
  @Transactional(readOnly = true)
  public List<ScannerResponse> listScanners(String buildingId) {
    requireTrottier(buildingId);
    Instant now = clock.instant();
    return batches.latestScannerBatches(buildingId, now.minus(BatchRepository.XRAY_LOOKBACK), now)
        .stream().map(row -> {
          var batch = row.batch();
          return new ScannerResponse(batch.getScannerId(), row.floor(), batch.getLatitude(),
              batch.getLongitude(), Duration.between(batch.getReceivedAt(), now).toSeconds(),
              batch.getRssis());
        }).toList();
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
    int people = live.estimatedPeople();
    int pct = live.busynessPct();
    if (live.covered()) {
      OptionalDouble smoothed = smoothedPeople(live);
      if (smoothed.isPresent()) {
        people = (int) Math.round(smoothed.getAsDouble());
        pct = BusynessCalculator.pctOf(smoothed.getAsDouble(), live.capacity());
      }
    }
    return new FloorBusynessResponse(
        live.floor(),
        live.capacity(),
        live.covered(),
        live.scanners(),
        people,
        pct,
        BusynessLevel.from(live.covered(), pct)
    );
  }

  /** Mean people of the covered snapshots among the last {@code SMOOTHING_SNAPSHOTS}, if any. */
  private OptionalDouble smoothedPeople(FloorBusyness live) {
    return snapshots
        .findByBuildingIdAndFloorOrderByAtDescIdDesc(
            live.buildingId(), live.floor(), Limit.of(properties.smoothingSnapshots())
        ).stream()
        .filter(BusynessSnapshot::isCovered)
        .mapToDouble(s -> s.getEstimatedPeople() != null
            ? s.getEstimatedPeople()
            : s.getBusynessPct() * live.capacity() / 100.0)
        .average();
  }
}
