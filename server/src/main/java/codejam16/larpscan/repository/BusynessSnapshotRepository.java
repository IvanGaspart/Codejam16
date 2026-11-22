package codejam16.larpscan.repository;

import codejam16.larpscan.domain.BusynessSnapshot;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link BusynessSnapshot}.
 */
public interface BusynessSnapshotRepository extends JpaRepository<BusynessSnapshot, Long> {

  /** Returns a Floor's snapshots taken at or after {@code since}, oldest first. */
  List<BusynessSnapshot> findByBuildingIdAndFloorAndAtGreaterThanEqualOrderByAtAsc(
      String buildingId,
      int floor,
      Instant since
  );

  /**
   * Returns a Floor's most recent snapshots, newest first; ties on {@code at} go to the higher id.
   *
   * @param buildingId building identifier
   * @param floor floor number within the Building
   * @param limit maximum number of snapshots to return
   * @return up to {@code limit} snapshots, newest first
   */
  List<BusynessSnapshot> findByBuildingIdAndFloorOrderByAtDescIdDesc(
      String buildingId,
      int floor,
      Limit limit
  );
}
