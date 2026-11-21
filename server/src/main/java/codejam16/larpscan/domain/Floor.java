package codejam16.larpscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One level of a Building with its seat capacity, loaded from {@code floors.csv} at startup.
 */
@Entity
@Table(
    name = "floor",
    uniqueConstraints = @UniqueConstraint(columnNames = {"building_id", "floor"})
)
public class Floor {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "building_id", nullable = false)
  private String buildingId;

  @Column(name = "floor", nullable = false)
  private int floor;

  @Column(nullable = false)
  private int capacity;

  /** Required by JPA. */
  protected Floor() {}

  public Floor(String buildingId, int floor, int capacity) {
    this.buildingId = buildingId;
    this.floor = floor;
    this.capacity = capacity;
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

  public int getCapacity() {
    return capacity;
  }
}
