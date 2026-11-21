package codejam16.larpscan.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class BuildingControllerTests {

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void listsBuildings() throws Exception {
    mockMvc.perform(get("/api/buildings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("trottier"))
        .andExpect(jsonPath("$[0].name").value("Trottier Building"));
  }

  @Test
  void listsFloorsForTrottier() throws Exception {
    mockMvc.perform(get("/api/buildings/trottier/floors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].floor").value(1))
        .andExpect(jsonPath("$[0].capacity").value(120))
        .andExpect(jsonPath("$[0].covered").value(true))
        .andExpect(jsonPath("$[0].estimatedPeople").value(34))
        .andExpect(jsonPath("$[0].busynessPct").value(28))
        .andExpect(jsonPath("$[0].level").value("QUIET"))
        .andExpect(jsonPath("$[3].floor").value(4))
        .andExpect(jsonPath("$[3].covered").value(false))
        .andExpect(jsonPath("$[3].estimatedPeople").value(0))
        .andExpect(jsonPath("$[3].busynessPct").value(0))
        .andExpect(jsonPath("$[3].level").value("NO_DATA"));
  }

  @Test
  void rejectsUnknownBuildingWithNotFound() throws Exception {
    mockMvc.perform(get("/api/buildings/unknown/floors"))
        .andExpect(status().isNotFound());
  }
}