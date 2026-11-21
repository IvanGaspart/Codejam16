package codejam16.larpscan.repository;

import codejam16.larpscan.domain.Floor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Floor}.
 */
public interface FloorRepository extends JpaRepository<Floor, Long> {

  /**
   * Returns every Floor of a Building, lowest first.
   *
   * @param buildingId building identifier
   * @return the Building's Floors ordered by floor number
   */
  List<Floor> findByBuildingIdOrderByFloorAsc(String buildingId);
}
