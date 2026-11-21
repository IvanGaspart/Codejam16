package codejam16.larpscan.repository;

import codejam16.larpscan.domain.Batch;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Batch}.
 */
public interface BatchRepository extends JpaRepository<Batch, Long> {}
