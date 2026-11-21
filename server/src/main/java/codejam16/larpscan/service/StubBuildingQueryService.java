package codejam16.larpscan.service;

import codejam16.larpscan.domain.BusynessLevel;
import codejam16.larpscan.web.dto.BuildingResponse;
import codejam16.larpscan.web.dto.FloorBusynessResponse;
import codejam16.larpscan.web.dto.FloorHistoryResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Temporary {@link BuildingQueryService} stub returning Trottier Building data until persistence
 * and Busyness aggregation are wired in.
 */
@Service
public class StubBuildingQueryService implements BuildingQueryService {

  private static final String TROTTIER_ID = "trottier";
  private static final int MIN_FLOOR = 1;
  private static final int MAX_FLOOR = 5;
  private static final BuildingResponse TROTTIER =
      new BuildingResponse(TROTTIER_ID, "Trottier Building");

  @Override
  public List<BuildingResponse> listBuildings() {
    return List.of(TROTTIER);
  }

  @Override
  public List<FloorBusynessResponse> listFloors(String buildingId) {
    requireTrottier(buildingId);
    return List.of(
        floor(1, 120, true, 34, 28),
        floor(2, 90, true, 45, 50),
        floor(3, 110, true, 77, 70),
        floor(4, 80, false, 0, 0),
        floor(5, 60, false, 0, 0)
    );
  }

  @Override
  public List<FloorHistoryResponse> getFloorHistory(String buildingId, int floor, Instant since) {
    requireTrottier(buildingId);
    if (floor < MIN_FLOOR || floor > MAX_FLOOR) {
      throw new FloorNotFoundException(buildingId, floor);
    }
    return sampleHistory(floor, since);
  }

  private static void requireTrottier(String buildingId) {
    if (!TROTTIER_ID.equals(buildingId)) {
      throw new BuildingNotFoundException(buildingId);
    }
  }

  private static List<FloorHistoryResponse> sampleHistory(int floor, Instant since) {
    boolean covered = floor <= 3;
    int latestPct = switch (floor) {
      case 1 -> 28;
      case 2 -> 50;
      case 3 -> 70;
      default -> 0;
    };
    int startPct = covered ? Math.max(0, latestPct - 4) : 0;
    int midPct = covered ? Math.max(0, latestPct - 2) : 0;
    return List.of(
        new FloorHistoryResponse(since, startPct, covered),
        new FloorHistoryResponse(since.plus(Duration.ofMinutes(1)), midPct, covered),
        new FloorHistoryResponse(since.plus(Duration.ofMinutes(2)), latestPct, covered)
    );
  }

  private static FloorBusynessResponse floor(
      int floor,
      int capacity,
      boolean covered,
      int estimatedPeople,
      int busynessPct
  ) {
    int people = covered ? estimatedPeople : 0;
    int pct = covered ? busynessPct : 0;
    return new FloorBusynessResponse(
        floor,
        capacity,
        covered,
        people,
        pct,
        BusynessLevel.from(covered, pct)
    );
  }
}
