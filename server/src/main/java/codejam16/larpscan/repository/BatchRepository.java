package codejam16.larpscan.repository;

import codejam16.larpscan.domain.Batch;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link Batch}.
 */
public interface BatchRepository extends JpaRepository<Batch, Long> {

  /**
   * Deletes every batch received at or before {@code cutoff} in one bulk statement.
   *
   * @return the number of batches deleted
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from Batch b where b.receivedAt <= :cutoff")
  int deleteReceivedAtOrBefore(@Param("cutoff") Instant cutoff);
}
