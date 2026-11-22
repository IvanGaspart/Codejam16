package codejam16.larpscan.repository;

import codejam16.larpscan.domain.Floor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Floor}.
 */
public interface FloorRepository extends JpaRepository<Floor, Long> {

  /** Returns whether any Floor is configured for {@code buildingId}, i.e. the Building exists. */
  boolean existsByBuildingId(String buildingId);

  /** Returns whether {@code floor} is configured for {@code buildingId}. */
  boolean existsByBuildingIdAndFloor(String buildingId, int floor);

  /**
   * Returns every Floor of a Building, lowest first.
   *
   * @param buildingId building identifier
   * @return the Building's Floors ordered by floor number
   */
  List<Floor> findByBuildingIdOrderByFloorAsc(String buildingId);
}
