package codejam16.larpscan;

import codejam16.larpscan.config.LarpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the LARP-scan backend server.
 * Enables Spring's task scheduler for periodic busyness snapshots and batch expiry jobs,
 * and registers typed {@link LarpProperties}.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(LarpProperties.class)
public class LarpScanApplication {

	public static void main(String[] args) {
		SpringApplication.run(LarpScanApplication.class, args);
	}
}
