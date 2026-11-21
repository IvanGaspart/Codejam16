package codejam16.larpscan.repository;

import codejam16.larpscan.domain.AccessPoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link AccessPoint}.
 */
public interface AccessPointRepository extends JpaRepository<AccessPoint, String> {

  /**
   * Returns every Access Point known to sit in a Building.
   *
   * @param buildingId building identifier
   * @return the Building's Access Points
   */
  List<AccessPoint> findByBuildingId(String buildingId);
}
