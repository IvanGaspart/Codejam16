package codejam16.larpscan.repository;

import codejam16.larpscan.domain.BusynessSnapshot;
import java.time.Instant;
import java.util.List;
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
}
