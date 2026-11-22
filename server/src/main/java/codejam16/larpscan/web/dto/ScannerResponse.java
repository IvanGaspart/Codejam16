package codejam16.larpscan.web.dto;

import java.util.UUID;

public record ScannerResponse(UUID id, Integer floor, Double lat, Double lon, long ageS, int[] rssis) {}
