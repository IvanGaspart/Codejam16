package codejam16.larpscan.web;

import codejam16.larpscan.service.BuildingQueryService;
import codejam16.larpscan.web.dto.BuildingResponse;
import codejam16.larpscan.web.dto.FloorBusynessResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read endpoints exposing monitored Buildings and current per-Floor Busyness.
 */
@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

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
}