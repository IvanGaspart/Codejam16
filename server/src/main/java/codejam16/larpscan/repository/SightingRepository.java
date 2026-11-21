package codejam16.larpscan.repository;

import codejam16.larpscan.domain.Sighting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Sighting}.
 */
public interface SightingRepository extends JpaRepository<Sighting, Long> {}
