package codejam16.larpscan.web;

import codejam16.larpscan.config.LarpProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class XrayController {

  private final LarpProperties properties;

  public XrayController(LarpProperties properties) {
    this.properties = properties;
  }

  public record Constants(int windowSeconds, double k, int closeRssi, double closeD,
                          int farRssi, double farD, double farDownscaleFactor) {}

  @GetMapping("/api/xray/constants")
  public Constants constants() {
    return new Constants(properties.windowSeconds(), properties.k(), properties.closeRssi(),
        properties.closeD(), properties.farRssi(), properties.farD(), properties.farDownscaleFactor());
  }
}
