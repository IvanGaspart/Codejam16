package codejam16.larpscan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A Wi-Fi Access Point known to sit on exactly one Floor, loaded from {@code trottier.csv} at
 * startup.
 */
@Entity
@Table(name = "access_point")
public class AccessPoint {

  /** Lowercase colon-separated BSSID. */
  @Id
  private String bssid;

  @Column(name = "building_id", nullable = false)
  private String buildingId;

  @Column(name = "floor", nullable = false)
  private int floor;

  /** Required by JPA. */
  protected AccessPoint() {}

  public AccessPoint(String bssid, String buildingId, int floor) {
    this.bssid = bssid;
    this.buildingId = buildingId;
    this.floor = floor;
  }

  public String getBssid() {
    return bssid;
  }

  public String getBuildingId() {
    return buildingId;
  }

  public int getFloor() {
    return floor;
  }
}
