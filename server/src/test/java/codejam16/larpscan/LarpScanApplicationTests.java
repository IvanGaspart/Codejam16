package codejam16.larpscan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
// activate test profile to keep base YAML config
@ActiveProfiles("test")
class LarpScanApplicationTests {

	@Test
	void contextLoads() {
	}

}
