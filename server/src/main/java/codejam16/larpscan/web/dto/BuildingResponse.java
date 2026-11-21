package codejam16.larpscan.web.dto;

/**
 * Summary representation of a monitored Building returned by {@code GET /api/buildings}.
 *
 * @param id stable URL identifier (e.g. {@code "trottier"})
 * @param name display name of the Building
 */
public record BuildingResponse(String id, String name) {}