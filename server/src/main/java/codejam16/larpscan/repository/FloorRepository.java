package codejam16.larpscan.repository;

import codejam16.larpscan.domain.Floor;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Floor}.
 */
public interface FloorRepository extends JpaRepository<Floor, Long> {}
