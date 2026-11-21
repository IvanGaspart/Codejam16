package codejam16.larpscan.service;

/**
 * Thrown when a client requests a {@code buildingId} that is not configured in LARP-scan.
 */
public class BuildingNotFoundException extends RuntimeException {

  private final String buildingId;

  /**
   * Creates an exception for an unknown Building identifier.
   *
   * @param buildingId requested building ID
   */
  public BuildingNotFoundException(String buildingId) {
    super("Unknown building: " + buildingId);
    this.buildingId = buildingId;
  }

  /**
   * Returns the requested unknown Building identifier.
   *
   * @return building ID
   */
  public String getBuildingId() {
    return buildingId;
  }
}