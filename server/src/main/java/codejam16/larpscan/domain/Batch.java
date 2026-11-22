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
 * One batch received from a Scanner, holding one RSSI per device it heard during the interval.
 * Kept for {@code WINDOW} and used for Floor coverage and device counts.
 *
 * <p>{@code bssid} is a plain column, not a reference to {@link AccessPoint}, because batches with
 * an unknown BSSID are still stored (RQ-03). A batch with no RSSIs is still stored, since it proves
 * the Floor is covered (RQ-10).
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

  /** Scanner clock; informational only. */
  @Column(name = "sent_at", nullable = false)
  private Instant sentAt;

  /** Server clock; the window and coverage are computed from this. */
  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  /** Signal strengths in dBm, one per device heard. Its length is the batch's device count. */
  @Column(nullable = false)
  private int[] rssis;

  /** Scanner position trilaterated from nearby APs at ingest; {@code null} when unknown. */
  private Double latitude;

  /** See {@link #latitude}. */
  private Double longitude;

  /** Required by JPA. */
  protected Batch() {}

  /** Creates a batch without a Scanner position. */
  public Batch(UUID scannerId, String bssid, Instant sentAt, Instant receivedAt, int[] rssis) {
    this(scannerId, bssid, sentAt, receivedAt, rssis, null, null);
  }

  public Batch(
      UUID scannerId,
      String bssid,
      Instant sentAt,
      Instant receivedAt,
      int[] rssis,
      Double latitude,
      Double longitude
  ) {
    this.scannerId = scannerId;
    this.bssid = bssid;
    this.sentAt = sentAt;
    this.receivedAt = receivedAt;
    this.rssis = rssis.clone();
    this.latitude = latitude;
    this.longitude = longitude;
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

  public Instant getSentAt() {
    return sentAt;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public int[] getRssis() {
    return rssis.clone();
  }

  public Double getLatitude() {
    return latitude;
  }

  public Double getLongitude() {
    return longitude;
  }
}
