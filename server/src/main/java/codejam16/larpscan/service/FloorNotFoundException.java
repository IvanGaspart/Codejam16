package codejam16.larpscan.service;

/**
 * Thrown when a requested Floor does not exist within an existing Building.
 */
public class FloorNotFoundException extends RuntimeException {

  /**
   * Creates an exception for the unknown Floor within {@code buildingId}.
   *
   * @param buildingId valid Building identifier
   * @param floor requested Floor number
   */
  public FloorNotFoundException(String buildingId, int floor) {
    super("Unknown floor " + floor + " for building: " + buildingId);
  }
}