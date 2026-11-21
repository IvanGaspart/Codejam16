package codejam16.larpscan.repository;

import codejam16.larpscan.domain.BusynessSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link BusynessSnapshot}.
 */
public interface BusynessSnapshotRepository extends JpaRepository<BusynessSnapshot, Long> {}
