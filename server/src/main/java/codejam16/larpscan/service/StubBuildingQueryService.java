package codejam16.larpscan.service;

import codejam16.larpscan.domain.BusynessLevel;
import codejam16.larpscan.web.dto.BuildingResponse;
import codejam16.larpscan.web.dto.FloorBusynessResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Temporary {@link BuildingQueryService} stub returning Trottier Building data until persistence
 * and Busyness aggregation are wired in.
 */
@Service
public class StubBuildingQueryService implements BuildingQueryService {

  private static final String TROTTIER_ID = "trottier";
  private static final BuildingResponse TROTTIER =
      new BuildingResponse(TROTTIER_ID, "Trottier Building");

  @Override
  public List<BuildingResponse> listBuildings() {
    return List.of(TROTTIER);
  }

  @Override
  public List<FloorBusynessResponse> listFloors(String buildingId) {
    if (!TROTTIER_ID.equals(buildingId)) {
      throw new BuildingNotFoundException(buildingId);
    }
    return List.of(
        floor(1, 120, true, 34, 28),
        floor(2, 90, true, 45, 50),
        floor(3, 110, true, 77, 70),
        floor(4, 80, false, 0, 0),
        floor(5, 60, false, 0, 0)
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