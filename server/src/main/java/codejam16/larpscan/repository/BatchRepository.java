package codejam16.larpscan.repository;

import codejam16.larpscan.domain.Batch;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Batch}.
 */
public interface BatchRepository extends JpaRepository<Batch, Long> {

  /**
   * Returns every batch received in the half-open interval {@code (after, upTo]}.
   *
   * @param after exclusive lower bound on {@code received_at}
   * @param upTo inclusive upper bound on {@code received_at}
   * @return batches in the interval, in no particular order
   */
  List<Batch> findByReceivedAtGreaterThanAndReceivedAtLessThanEqual(Instant after, Instant upTo);
}
