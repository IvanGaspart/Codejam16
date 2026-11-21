package codejam16.larpscan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the LARP-scan backend server.
 * Enables Spring's task scheduler for periodic busyness snapshots and sighting expiry jobs.
 */
@SpringBootApplication
@EnableScheduling
public class LarpScanApplication {

	public static void main(String[] args) {
		SpringApplication.run(LarpScanApplication.class, args);
	}
}