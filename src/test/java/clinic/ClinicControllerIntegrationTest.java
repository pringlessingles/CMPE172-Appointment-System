package clinic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClinicControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homeReadsProvidersAndServicesFromDatabase() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemName").value("Community Health Clinic Appointment System"))
                .andExpect(jsonPath("$.providers", hasSize(2)))
                .andExpect(jsonPath("$.services", hasSize(4)));
    }

    @Test
    void slotsSupportsFilteringAndPagination() throws Exception {
        mockMvc.perform(get("/slots")
                        .param("providerId", "1")
                        .param("date", "2026-10-05")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].providerName").value("Dr. Maya Chen"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void invalidPageSizeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/slots").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
    }
}

