package de.mhus.nimbus.world.player.api;

import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(RegionWorldControllerTest.MockConfig.class)
class RegionWorldControllerTest {

    @Configuration
    static class MockConfig {
        @Bean WWorldService worldService() { return Mockito.mock(WWorldService.class); }
    }

    MockMvc mockMvc;
    WWorldService localService;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        localService = Mockito.mock(WWorldService.class);
        // Default: return empty for getByWorldId to simulate non-existing worlds
        Mockito.when(localService.getByWorldId(Mockito.any(String.class))).thenReturn(Optional.empty());

        ObjectMapper mapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new RegionWorldController(localService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void createMainWorldOk() throws Exception {
        String body = "{\"worldId\":\"region:terra\",\"info\":{\"name\":\"Terra\"}}";
        WorldInfo info = new WorldInfo();
        info.setName("Terra");
        WWorld created = WWorld.builder()
            .worldId("region:terra")
            .publicData(info)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .enabled(true)
            .build();

        // Mock createWorld - verwende any() für beide Parameter
        Mockito.when(localService.createWorld(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            System.out.println("Mock called with: " + invocation.getArguments()[0] + ", " + invocation.getArguments()[1]);
            return created;
        });

        mockMvc.perform(post("/world/region/world")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(body))
            .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.worldId").value("region:terra"));
    }

    @Test
    void getUnknownWorldReturns404() throws Exception {
        mockMvc.perform(get("/world/region/world").param("worldId","region:terra"))
            .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
            .andExpect(status().isNotFound());
    }
}
