package codejam16.larpscan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.FloorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
// activate test profile to keep base YAML config
@ActiveProfiles("test")
class LarpScanApplicationTests {

	@Autowired
	private FloorRepository floors;

	@Autowired
	private AccessPointRepository accessPoints;

	@Test
	void contextLoads() {
	}

	@Test
	void startupLoadsReferenceDataFromCsvs() {
		assertEquals(6, floors.count());
		assertEquals(359, accessPoints.count());
	}

}
