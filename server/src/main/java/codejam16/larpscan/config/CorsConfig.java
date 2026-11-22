package codejam16.larpscan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures CORS for public {@code GET} endpoints under {@code /api/**} so the Lovable frontend
 * can query building, floor, and history data across origins (RQ-06, RQ-04).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  private final LarpProperties properties;

  /**
   * Creates a CORS configuration backed by validated {@code larp.cors.*} properties.
   *
   * @param properties validated {@code larp.*} configuration properties
   */
  public CorsConfig(LarpProperties properties) {
    this.properties = properties;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns(properties.cors().allowedOrigins().toArray(String[]::new))
        .allowedMethods("GET", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(false)
        .maxAge(3600);
  }
}
