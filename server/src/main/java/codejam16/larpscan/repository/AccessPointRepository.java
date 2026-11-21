package codejam16.larpscan.repository;

import codejam16.larpscan.domain.AccessPoint;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link AccessPoint}.
 */
public interface AccessPointRepository extends JpaRepository<AccessPoint, String> {}
