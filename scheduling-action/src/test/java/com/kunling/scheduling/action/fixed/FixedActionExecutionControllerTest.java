package com.kunling.scheduling.action.fixed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.fixed.application.FixedActionExecutionService;
import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionState;
import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView;
import com.kunling.scheduling.action.fixed.interfaces.FixedActionExecutionController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FixedActionExecutionControllerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void startEndpointAcceptsBusinessInputAndReturnsExecutionLocation() throws Exception {
        FixedActionExecutionService service = mock(FixedActionExecutionService.class);
        when(service.start(any())).thenReturn(view());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FixedActionExecutionController(service)).build();

        mvc.perform(post("/api/v1/robot-action-executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionInstanceId\":\"action-1\",\"robotId\":\"ROBOT-01\",\"actionType\":\"MOVE\",\n \"input\":{\"pointName\":\"P01\",\"speed\":0.5,\n   \"pose\":{\"x\":1,\"y\":2,\"yaw\":3,\"map\":\"LAB\"}}}\n"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/robot-action-executions/action-1"))
                .andExpect(jsonPath("$.state").value("DISPATCHED"));
    }

    private RobotActionExecutionView view() {
        Instant now = Instant.parse("2026-08-19T01:00:00Z");
        return new RobotActionExecutionView("action-1", "ROBOT-01", "device-1", "MOVE", "1.0", "1.0.0",
                "request-hash", "package-hash", RobotActionExecutionState.DISPATCHED, false,
                null, null, objectMapper.createObjectNode(), null, null, null, now, now, null);
    }
}
