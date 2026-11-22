package codejam16.larpscan.service;

import codejam16.larpscan.repository.FloorRepository;
import codejam16.larpscan.web.dto.BuildingResponse;
import codejam16.larpscan.web.dto.FloorBusynessResponse;
import codejam16.larpscan.web.dto.FloorHistoryResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * {@link BuildingQueryService} backed by {@link BusynessCalculator} and {@link FloorHistoryService}.
 * Trottier is the only Building, so it is a constant rather than a table.
 *
 * <p>{@code busynessPct} is currently the unsmoothed value at the request instant; smoothing over
 * recent snapshots is added with the snapshot job.
 */
@Service
public class JpaBuildingQueryService implements BuildingQueryService {

  private static final BuildingResponse TROTTIER =
      new BuildingResponse("trottier", "Trottier Building");

  private final BusynessCalculator calculator;
  private final FloorHistoryService history;
  private final FloorRepository floors;
  private final Clock clock;

  public JpaBuildingQueryService(
      BusynessCalculator calculator,
      FloorHistoryService history,
      FloorRepository floors,
      Clock clock
  ) {
    this.calculator = calculator;
    this.history = history;
    this.floors = floors;
    this.clock = clock;
  }

  @Override
  public List<BuildingResponse> listBuildings() {
    return List.of(TROTTIER);
  }

  @Override
  public List<FloorBusynessResponse> listFloors(String buildingId) {
    requireTrottier(buildingId);
    return calculator.calculate(buildingId, clock.instant()).stream()
        .map(f -> new FloorBusynessResponse(
            f.floor(),
            f.capacity(),
            f.covered(),
            f.estimatedPeople(),
            f.busynessPct(),
            f.level()
        ))
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
}
