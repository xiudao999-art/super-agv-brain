package com.kunling.scheduling.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.workflow.dto.WorkflowRequests;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = WorkflowControllerTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "flowable.process.enabled=false",
                "flowable.eventregistry.enabled=false",
                "flowable.cmmn.enabled=false",
                "flowable.dmn.enabled=false",
                "flowable.form.enabled=false",
                "flowable.content.enabled=false",
                "flowable.idm.enabled=false",
                "flowable.app.enabled=false"
        }
)
@AutoConfigureMockMvc
class WorkflowControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({WorkflowController.class, WorkflowExceptionHandler.class})
    static class TestApplication {
        @Bean
        WorkflowService workflowService() {
            return mock(WorkflowService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowService workflowService;

    @BeforeEach
    void resetWorkflowService() {
        reset(workflowService);
    }

    @Test
    void deployShouldAcceptBpmnXmlAndReturnProcessDefinition() throws Exception {
        String bpmnXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "targetNamespace=\"http://kunling.com/test\">"
                + "<process id=\"agvTestProcess\" name=\"AGV测试流程\" isExecutable=\"true\">"
                + "<startEvent id=\"start\" name=\"开始\"/>"
                + "<serviceTask id=\"moveToPickup\" name=\"移动到取料点\"/>"
                + "<serviceTask id=\"pickup\" name=\"执行取料\"/>"
                + "<serviceTask id=\"moveToDropoff\" name=\"移动到放料点\"/>"
                + "<serviceTask id=\"dropoff\" name=\"执行放料\"/>"
                + "<userTask id=\"manualConfirm\" name=\"人工确认\"/>"
                + "<endEvent id=\"end\" name=\"结束\"/>"
                + "<sequenceFlow id=\"flow1\" sourceRef=\"start\" targetRef=\"moveToPickup\"/>"
                + "<sequenceFlow id=\"flow2\" sourceRef=\"moveToPickup\" targetRef=\"pickup\"/>"
                + "<sequenceFlow id=\"flow3\" sourceRef=\"pickup\" targetRef=\"moveToDropoff\"/>"
                + "<sequenceFlow id=\"flow4\" sourceRef=\"moveToDropoff\" targetRef=\"dropoff\"/>"
                + "<sequenceFlow id=\"flow5\" sourceRef=\"dropoff\" targetRef=\"manualConfirm\"/>"
                + "<sequenceFlow id=\"flow6\" sourceRef=\"manualConfirm\" targetRef=\"end\"/>"
                + "</process></definitions>";

        WorkflowRequests.DeployDefinition request = new WorkflowRequests.DeployDefinition();
        request.setName("AGV测试流程");
        request.setResourceName("agv-test.bpmn20.xml");
        request.setCategory("AGV");
        request.setBpmnXml(bpmnXml);

        WorkflowResponses.Definition response = new WorkflowResponses.Definition(
                "agvTestProcess:1:1001",
                "agvTestProcess",
                "AGV测试流程",
                1,
                "deployment-1000",
                "agv-test.bpmn20.xml",
                "AGV"
        );

        when(workflowService.deploy(argThat(value ->
                "AGV测试流程".equals(value.getName())
                        && "agv-test.bpmn20.xml".equals(value.getResourceName())
                        && "AGV".equals(value.getCategory())
                        && bpmnXml.equals(value.getBpmnXml())
        ))).thenReturn(response);

        mockMvc.perform(post("/api/workflows/definitions/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("agvTestProcess:1:1001"))
                .andExpect(jsonPath("$.key").value("agvTestProcess"))
                .andExpect(jsonPath("$.name").value("AGV测试流程"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.deploymentId").value("deployment-1000"))
                .andExpect(jsonPath("$.resourceName").value("agv-test.bpmn20.xml"))
                .andExpect(jsonPath("$.category").value("AGV"));

        verify(workflowService).deploy(argThat(value -> bpmnXml.equals(value.getBpmnXml())));
    }

    @Test
    void deployShouldRejectRequestWhenNameIsBlank() throws Exception {
        WorkflowRequests.DeployDefinition request = new WorkflowRequests.DeployDefinition();
        request.setName(" ");
        request.setResourceName("invalid.bpmn20.xml");
        request.setBpmnXml("<definitions/>");

        mockMvc.perform(post("/api/workflows/definitions/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(workflowService);
    }

    @Test
    void deployShouldRejectRequestWhenBpmnXmlIsMissing() throws Exception {
        WorkflowRequests.DeployDefinition request = new WorkflowRequests.DeployDefinition();
        request.setName("AGV测试流程");
        request.setResourceName("invalid.bpmn20.xml");

        mockMvc.perform(post("/api/workflows/definitions/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(workflowService);
    }
}
