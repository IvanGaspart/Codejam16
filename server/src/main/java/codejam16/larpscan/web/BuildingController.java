package codejam16.larpscan.web;

import codejam16.larpscan.service.BuildingQueryService;
import codejam16.larpscan.web.dto.BuildingResponse;
import codejam16.larpscan.web.dto.FloorBusynessResponse;
import codejam16.larpscan.web.dto.FloorHistoryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read endpoints exposing monitored Buildings, current per-Floor Busyness, and history.
 */
@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

  private static final ZoneId TROTTIER_ZONE = ZoneId.of("America/Toronto");

  private final BuildingQueryService buildingQueryService;

  /**
   * Creates a controller backed by the given query service.
   *
   * @param buildingQueryService building and floor query service
   */
  public BuildingController(BuildingQueryService buildingQueryService) {
    this.buildingQueryService = buildingQueryService;
  }

  /**
   * Lists all monitored Buildings.
   *
   * @return list of Buildings
   */
  @GetMapping
  public List<BuildingResponse> listBuildings() {
    return buildingQueryService.listBuildings();
  }

  /**
   * Lists current Busyness for every Floor in the specified Building.
   *
   * @param buildingId building identifier
   * @return current Floor Busyness entries
   */
  @GetMapping("/{buildingId}/floors")
  public List<FloorBusynessResponse> listFloors(@PathVariable String buildingId) {
    return buildingQueryService.listFloors(buildingId);
  }

  /**
   * Lists Busyness history snapshots for a single Floor since {@code since} (or local midnight in
   * {@code America/Toronto} when omitted).
   *
   * @param buildingId building identifier
   * @param floor floor number
   * @param since optional inclusive ISO-8601 start timestamp
   * @return chronological Busyness snapshots
   */
  @GetMapping("/{buildingId}/floors/{floor}/history")
  public List<FloorHistoryResponse> getFloorHistory(
      @PathVariable String buildingId,
      @PathVariable int floor,
      @RequestParam(required = false) Instant since
  ) {
    return buildingQueryService.getFloorHistory(buildingId, floor, resolveSince(since));
  }

  private static Instant resolveSince(Instant since) {
    if (since != null) {
      return since;
    }
    return LocalDate.now(TROTTIER_ZONE).atStartOfDay(TROTTIER_ZONE).toInstant();
  }
}
