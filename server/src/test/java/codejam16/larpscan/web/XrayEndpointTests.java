package codejam16.larpscan.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.repository.BatchRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {"larp.k=1.4", "larp.close-d=6", "larp.far-d=18",
    "larp.close-rssi=-67", "larp.far-rssi=-85", "larp.far-downscale-factor=0.6",
    "larp.window-seconds=121"})
@ActiveProfiles("test")
@Transactional
class XrayEndpointTests {

  @Autowired private BatchRepository batches;
  @Autowired private WebApplicationContext context;

  @Test
  void returnsOnlyLiveCalculatorSettings() throws Exception {
    MockMvcBuilders.webAppContextSetup(context).build().perform(get("/api/xray/constants"))
        .andExpect(status().isOk())
        .andExpect(content().json("""
            {"windowSeconds":121,"k":1.4,"closeRssi":-67,"closeD":6,
             "farRssi":-85,"farD":18,"farDownscaleFactor":0.6}
            """, JsonCompareMode.STRICT));
  }

  @Test
  void returnsLatestBatchesIncludingUnplacedScanners() throws Exception {
    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
    String url = "/api/buildings/trottier/scanners";
    batches.deleteAll();
    mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().json("[]"));
    UUID placed = new UUID(0, 1);
    UUID unknown = new UUID(0, 2);
    Instant at = Instant.now().minusSeconds(600);
    String ap = "44:48:c1:a4:1a:50";
    batches.save(new Batch(placed, ap, at, at, new int[] {-80}));
    batches.save(new Batch(placed, ap, at, at, new int[] {-59}, 45.5, -73.5));
    batches.save(new Batch(unknown, "00:00:00:00:00:00", at, at, new int[0]));
    batches.save(new Batch(UUID.randomUUID(), ap, at, at.minusSeconds(3600), new int[0]));

    mvc.perform(get(url)).andExpect(status().isOk())
        .andExpect(content().json("""
            [{"id":"%s","floor":3,"lat":45.5,"lon":-73.5,"rssis":[-59]},
             {"id":"%s","floor":null,"lat":null,"lon":null,"rssis":[]}]
            """.formatted(placed, unknown)))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .jsonPath("$[0].ageS", org.hamcrest.Matchers.greaterThanOrEqualTo(600)));
  }
}
