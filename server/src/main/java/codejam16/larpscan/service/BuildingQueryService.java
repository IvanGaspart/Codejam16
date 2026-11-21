package codejam16.larpscan.service;

import codejam16.larpscan.web.dto.BuildingResponse;
import codejam16.larpscan.web.dto.FloorBusynessResponse;
import java.util.List;

/**
 * Read-side contract for listing monitored Buildings and per-Floor Busyness snapshots.
 */
public interface BuildingQueryService {

  /**
   * Returns all Buildings monitored by the server.
   *
   * @return list of Buildings
   */
  List<BuildingResponse> listBuildings();

  /**
   * Returns the current Busyness status for every Floor in the given Building.
   *
   * @param buildingId building identifier
   * @return ordered list of Floor statuses
   * @throws BuildingNotFoundException if {@code buildingId} does not exist
   */
  List<FloorBusynessResponse> listFloors(String buildingId);
}