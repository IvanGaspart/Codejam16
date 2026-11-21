package codejam16.larpscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One batch received from a Scanner. Kept for {@code WINDOW} and used for Floor coverage.
 *
 * <p>{@code bssid} is a plain column, not a reference to {@link AccessPoint}, because batches with
 * an unknown BSSID are still stored (RQ-03).
 */
@Entity
@Table(name = "batch", indexes = @Index(columnList = "received_at"))
public class Batch {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "scanner_id", nullable = false)
  private UUID scannerId;

  @Column(nullable = false)
  private String bssid;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  /** Required by JPA. */
  protected Batch() {}

  public Batch(UUID scannerId, String bssid, Instant receivedAt) {
    this.scannerId = scannerId;
    this.bssid = bssid;
    this.receivedAt = receivedAt;
  }

  public Long getId() {
    return id;
  }

  public UUID getScannerId() {
    return scannerId;
  }

  public String getBssid() {
    return bssid;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }
}
