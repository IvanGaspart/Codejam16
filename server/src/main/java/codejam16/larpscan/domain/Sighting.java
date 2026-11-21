package codejam16.larpscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One BLE address heard by one Scanner at one moment. Kept for {@code WINDOW}.
 */
@Entity
@Table(name = "sighting", indexes = @Index(columnList = "seen_at"))
public class Sighting {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "batch_id", nullable = false)
  private Batch batch;

  /** Lowercase colon-separated BLE address. */
  @Column(nullable = false)
  private String address;

  /** Signal strength in dBm. */
  @Column(nullable = false)
  private int rssi;

  @Column(name = "seen_at", nullable = false)
  private Instant seenAt;

  /** Required by JPA. */
  protected Sighting() {}

  public Sighting(Batch batch, String address, int rssi, Instant seenAt) {
    this.batch = batch;
    this.address = address;
    this.rssi = rssi;
    this.seenAt = seenAt;
  }

  public Long getId() {
    return id;
  }

  public Batch getBatch() {
    return batch;
  }

  public String getAddress() {
    return address;
  }

  public int getRssi() {
    return rssi;
  }

  public Instant getSeenAt() {
    return seenAt;
  }
}
