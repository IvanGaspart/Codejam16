package codejam16.larpscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A Floor's Busyness recorded every {@code SNAPSHOT_INTERVAL}. Snapshots are kept and feed
 * smoothing and history (RQ-07).
 */
@Entity
@Table(
    name = "busyness_snapshot",
    indexes = @Index(columnList = "building_id, floor, at")
)
public class BusynessSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "building_id", nullable = false)
  private String buildingId;

  @Column(name = "floor", nullable = false)
  private int floor;

  @Column(nullable = false)
  private Instant at;

  /** Unsmoothed percentage of capacity in use ({@code 0..100}). */
  @Column(name = "busyness_pct", nullable = false)
  private int busynessPct;

  @Column(nullable = false)
  private boolean covered;

  /** Required by JPA. */
  protected BusynessSnapshot() {}

  public BusynessSnapshot(
      String buildingId,
      int floor,
      Instant at,
      int busynessPct,
      boolean covered
  ) {
    this.buildingId = buildingId;
    this.floor = floor;
    this.at = at;
    this.busynessPct = busynessPct;
    this.covered = covered;
  }

  public Long getId() {
    return id;
  }

  public String getBuildingId() {
    return buildingId;
  }

  public int getFloor() {
    return floor;
  }

  public Instant getAt() {
    return at;
  }

  public int getBusynessPct() {
    return busynessPct;
  }

  public boolean isCovered() {
    return covered;
  }
}
