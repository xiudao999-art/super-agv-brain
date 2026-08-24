package com.kunling.scheduling.app.controller;

import com.kunling.scheduling.agvflow.config.MybatisPlusConfig;
import com.kunling.scheduling.agvflow.mapper.LabConfigMapper;
import com.kunling.scheduling.agvflow.service.LabConfigApplicationService;
import com.kunling.scheduling.agvflow.service.LabConfigDraftEditor;
import com.kunling.scheduling.agvflow.service.LabConfigQueryService;
import com.kunling.scheduling.agvflow.service.LabConfigurationValidator;
import com.kunling.scheduling.agvflow.service.LabLocationReferenceChecker;
import com.kunling.scheduling.agvflow.service.LabMapPointProjector;
import com.kunling.scheduling.app.file.FileWebConfiguration;
import com.kunling.scheduling.app.file.ImageStorageService;
import com.kunling.scheduling.app.config.SchedulingAppOpenApiConfiguration;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(
        classes = LabConfigApiTest.TestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:lab_config;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.sql.init.mode=never",
                "flowable.process.enabled=false",
                "flowable.eventregistry.enabled=false",
                "flowable.idm.enabled=false",
                "knife4j.enable=true",
                "kunling.file.storage-directory=target/test-images"
        })
@AutoConfigureMockMvc
@Sql(scripts = "/db/test/lab_config_test_schema.sql")
class LabConfigApiTest {

    @Resource
    private MockMvc mockMvc;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Test
    void 初始化唯一实验室后可以查询首个草稿() throws Exception {
        mockMvc.perform(post("/api/lab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"实验室 A\","
                                + "\"map\":{\"name\":\"实验室总览地图\",\"version\":\"V1.0\","
                                + "\"imageUrl\":\"/files/lab-a-v1.png\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.revision").value(1));

        mockMvc.perform(get("/api/lab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("实验室 A"))
                .andExpect(jsonPath("$.data.draft.revision").value(1))
                .andExpect(jsonPath("$.data.draft.map.version").value("V1.0"))
                .andExpect(jsonPath("$.data.draft.map.imageUrl").value("/files/lab-a-v1.png"));
    }

    @Test
    void 唯一实验室不能重复初始化() throws Exception {
        initializeLab();

        mockMvc.perform(post("/api/lab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"另一个实验室\","
                                + "\"map\":{\"name\":\"另一张地图\",\"version\":\"V1.0\","
                                + "\"imageUrl\":\"/files/another.png\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("实验室已初始化，不能重复创建"));
    }

    @Test
    void 上传地图图片后返回可提交给地图接口的地址() throws Exception {
        byte[] png = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
        };

        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/images")
                        .file(new MockMultipartFile("file", "map.png", "image/png", png)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.matchesPattern(
                        "/files/[0-9a-f-]+\\.png")))
                .andReturn();
        String imageUrl = com.jayway.jsonpath.JsonPath.read(
                uploadResult.getResponse().getContentAsString(), "$.data.imageUrl");
        mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes(png));

        mockMvc.perform(multipart("/api/files/images")
                        .file(new MockMultipartFile(
                                "file", "map.txt", "text/plain", "not-image".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("仅支持 PNG、JPEG、GIF、WEBP 图片"));
    }

    @Test
    void 可以在草稿中新增通行节点并从配置详情读取() throws Exception {
        Long configId = initializeLabAndGetConfigId();

        mockMvc.perform(post("/api/lab-configs/{configId}/nodes", configId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"N01\",\"name\":\"立库出口\",\"type\":\"NAVIGATION\","
                                + "\"x\":11.8200,\"y\":6.1100,\"yaw\":90.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").isNumber());

        mockMvc.perform(get("/api/lab-configs/{configId}", configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.nodes[0].code").value("N01"))
                .andExpect(jsonPath("$.data.nodes[0].yaw").value(90.0))
                .andExpect(jsonPath("$.data.machines").isEmpty())
                .andExpect(jsonPath("$.data.points").isEmpty())
                .andExpect(jsonPath("$.data.links").isEmpty());
    }

    @Test
    void 可以在三表模型中构建完整实验室配置图() throws Exception {
        Long configId = initializeLabAndGetConfigId();
        Long startNodeId = postAndGetId("/api/lab-configs/" + configId + "/nodes",
                "{\"code\":\"N01\",\"name\":\"起点\",\"type\":\"NAVIGATION\","
                        + "\"x\":1.0,\"y\":2.0,\"yaw\":0}");
        Long endNodeId = postAndGetId("/api/lab-configs/" + configId + "/nodes",
                "{\"code\":\"N02\",\"name\":\"终点\",\"type\":\"NAVIGATION\","
                        + "\"x\":3.0,\"y\":4.0,\"yaw\":180}");
        Long machineId = postAndGetId("/api/lab-configs/" + configId + "/machines",
                "{\"code\":\"M01\",\"name\":\"贴标机台\",\"type\":\"LABELER\","
                        + "\"anchorX\":3.2,\"anchorY\":4.1,\"anchorYaw\":90}");
        Long pointId = postAndGetId("/api/lab-configs/" + configId + "/points",
                "{\"machineId\":" + machineId + ",\"navNodeId\":" + endNodeId + ","
                        + "\"code\":\"P01\",\"name\":\"放料点\",\"type\":\"ARM_PLACE\","
                        + "\"frame\":\"MACHINE\",\"x\":0.42,\"y\":-0.135,\"z\":0.88,"
                        + "\"rx\":0,\"ry\":0,\"rz\":180}");
        Long linkId = postAndGetId("/api/lab-configs/" + configId + "/links",
                "{\"code\":\"L01\",\"startNodeId\":" + startNodeId
                        + ",\"endNodeId\":" + endNodeId
                        + ",\"direction\":\"BIDIRECTIONAL\",\"speedLimit\":0.6}");

        mockMvc.perform(get("/api/lab-configs/{configId}", configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes.length()").value(2))
                .andExpect(jsonPath("$.data.machines[0].id").value(machineId))
                .andExpect(jsonPath("$.data.points[0].id").value(pointId))
                .andExpect(jsonPath("$.data.points[0].machineId").value(machineId))
                .andExpect(jsonPath("$.data.points[0].navNodeId").value(endNodeId))
                .andExpect(jsonPath("$.data.links[0].id").value(linkId))
                .andExpect(jsonPath("$.data.links[0].speedLimit").value(0.6));

        mockMvc.perform(get("/api/lab-configs/{configId}/map-points", configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].kind").value("TRAFFIC_NODE"))
                .andExpect(jsonPath("$.data[2].kind").value("MACHINE"))
                .andExpect(jsonPath("$.data[3].id").value(pointId))
                .andExpect(jsonPath("$.data[3].kind").value("MACHINE_POINT"))
                .andExpect(jsonPath("$.data[3].x").value(3.335))
                .andExpect(jsonPath("$.data[3].y").value(4.52))
                .andExpect(jsonPath("$.data[3].yaw").value(-90));

        mockMvc.perform(get("/api/lab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draft.counts.nodeCount").value(2))
                .andExpect(jsonPath("$.data.draft.counts.machineCount").value(1))
                .andExpect(jsonPath("$.data.draft.counts.pointCount").value(1))
                .andExpect(jsonPath("$.data.draft.counts.linkCount").value(1));
    }

    @Test
    void 发布后不可修改且新草稿会重建全部内部引用() throws Exception {
        LabInitialization creation = initializeLab();
        Long startNodeId = postAndGetId("/api/lab-configs/" + creation.configId + "/nodes",
                "{\"code\":\"N01\",\"name\":\"起点\",\"type\":\"NAVIGATION\","
                        + "\"x\":1,\"y\":2,\"yaw\":0}");
        Long endNodeId = postAndGetId("/api/lab-configs/" + creation.configId + "/nodes",
                "{\"code\":\"N02\",\"name\":\"终点\",\"type\":\"NAVIGATION\","
                        + "\"x\":3,\"y\":4,\"yaw\":90}");
        Long machineId = postAndGetId("/api/lab-configs/" + creation.configId + "/machines",
                "{\"code\":\"M01\",\"name\":\"机台\",\"type\":\"LABELER\","
                        + "\"anchorX\":3,\"anchorY\":4,\"anchorYaw\":90}");
        postAndGetId("/api/lab-configs/" + creation.configId + "/points",
                "{\"machineId\":" + machineId + ",\"navNodeId\":" + endNodeId + ","
                        + "\"code\":\"P01\",\"name\":\"放料点\",\"type\":\"ARM_PLACE\","
                        + "\"frame\":\"MACHINE\",\"x\":0.4,\"y\":0.1,\"z\":0.8,"
                        + "\"rx\":0,\"ry\":0,\"rz\":180}");
        postAndGetId("/api/lab-configs/" + creation.configId + "/links",
                "{\"code\":\"L01\",\"startNodeId\":" + startNodeId
                        + ",\"endNodeId\":" + endNodeId
                        + ",\"direction\":\"ONE_WAY\",\"speedLimit\":0.5}");

        mockMvc.perform(post("/api/lab-configs/{configId}/validate", creation.configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
        mockMvc.perform(post("/api/lab-configs/{configId}/publish", creation.configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mockMvc.perform(post("/api/lab-configs/{configId}/nodes", creation.configId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"N03\",\"name\":\"禁止写入\",\"type\":\"PATH\","
                                + "\"x\":0,\"y\":0,\"yaw\":0}"))
                .andExpect(status().isConflict());

        MvcResult draftResult = mockMvc.perform(post("/api/lab/drafts"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.revision").value(2))
                .andReturn();
        Number draftConfigIdValue = com.jayway.jsonpath.JsonPath.read(
                draftResult.getResponse().getContentAsString(), "$.data.configId");
        Long draftConfigId = draftConfigIdValue.longValue();

        MvcResult detailResult = mockMvc.perform(get("/api/lab-configs/{configId}", draftConfigId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes.length()").value(2))
                .andExpect(jsonPath("$.data.machines.length()").value(1))
                .andExpect(jsonPath("$.data.points.length()").value(1))
                .andExpect(jsonPath("$.data.links.length()").value(1))
                .andReturn();
        Map<String, Object> detail = com.jayway.jsonpath.JsonPath.read(
                detailResult.getResponse().getContentAsString(), "$.data");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) detail.get("nodes");
        List<Map<String, Object>> machines = (List<Map<String, Object>>) detail.get("machines");
        List<Map<String, Object>> points = (List<Map<String, Object>>) detail.get("points");
        List<Map<String, Object>> links = (List<Map<String, Object>>) detail.get("links");
        Long clonedStartNodeId = findIdByCode(nodes, "N01");
        Long clonedEndNodeId = findIdByCode(nodes, "N02");
        Long clonedMachineId = findIdByCode(machines, "M01");
        org.junit.jupiter.api.Assertions.assertNotEquals(startNodeId, clonedStartNodeId);
        org.junit.jupiter.api.Assertions.assertEquals(clonedMachineId,
                ((Number) points.get(0).get("machineId")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(clonedEndNodeId,
                ((Number) points.get(0).get("navNodeId")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(clonedStartNodeId,
                ((Number) links.get(0).get("startNodeId")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(clonedEndNodeId,
                ((Number) links.get(0).get("endNodeId")).longValue());

        mockMvc.perform(post("/api/lab-configs/{configId}/publish", draftConfigId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mockMvc.perform(get("/api/lab-configs/{configId}", creation.configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mockMvc.perform(get("/api/lab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published.id").value(draftConfigId))
                .andExpect(jsonPath("$.data.draft").doesNotExist());
    }

    @Test
    void 草稿支持完整维护且被引用对象不能直接删除() throws Exception {
        LabInitialization creation = initializeLab();
        mockMvc.perform(put("/api/lab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"实验室 A（东区）\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data").hasJsonPath())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(put("/api/lab-configs/{configId}/map", creation.configId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"东区地图\",\"version\":\"V1.1\","
                                + "\"imageUrl\":\"/files/lab-a-v1.1.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").hasJsonPath())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        Long startNodeId = postAndGetId("/api/lab-configs/" + creation.configId + "/nodes",
                "{\"code\":\"N01\",\"name\":\"起点\",\"type\":\"NAVIGATION\","
                        + "\"x\":1,\"y\":2,\"yaw\":0}");
        Long endNodeId = postAndGetId("/api/lab-configs/" + creation.configId + "/nodes",
                "{\"code\":\"N02\",\"name\":\"终点\",\"type\":\"NAVIGATION\","
                        + "\"x\":3,\"y\":4,\"yaw\":90}");
        Long machineId = postAndGetId("/api/lab-configs/" + creation.configId + "/machines",
                "{\"code\":\"M01\",\"name\":\"机台\",\"type\":\"LABELER\","
                        + "\"anchorX\":3,\"anchorY\":4,\"anchorYaw\":90}");
        Long pointId = postAndGetId("/api/lab-configs/" + creation.configId + "/points",
                "{\"machineId\":" + machineId + ",\"navNodeId\":" + endNodeId + ","
                        + "\"code\":\"P01\",\"name\":\"放料点\",\"type\":\"ARM_PLACE\","
                        + "\"frame\":\"MACHINE\",\"x\":0.4,\"y\":0.1,\"z\":0.8,"
                        + "\"rx\":0,\"ry\":0,\"rz\":180}");
        Long linkId = postAndGetId("/api/lab-configs/" + creation.configId + "/links",
                "{\"code\":\"L01\",\"startNodeId\":" + startNodeId
                        + ",\"endNodeId\":" + endNodeId
                        + ",\"direction\":\"ONE_WAY\",\"speedLimit\":0.5}");

        mockMvc.perform(put("/api/lab-configs/{configId}/nodes/{nodeId}", creation.configId, startNodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"N01\",\"name\":\"新起点\",\"type\":\"WAITING\","
                                + "\"x\":1.5,\"y\":2.5,\"yaw\":10}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/lab-configs/{configId}/machines/{machineId}", creation.configId, machineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"M01\",\"name\":\"新机台\",\"type\":\"LABELER\","
                                + "\"anchorX\":5,\"anchorY\":6,\"anchorYaw\":45}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/lab-configs/{configId}/points/{pointId}", creation.configId, pointId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"machineId\":" + machineId + ",\"navNodeId\":" + endNodeId + ","
                                + "\"code\":\"P01\",\"name\":\"新放料点\",\"type\":\"ARM_PLACE\","
                                + "\"frame\":\"MACHINE\",\"x\":0.5,\"y\":0.2,\"z\":0.9,"
                                + "\"rx\":0,\"ry\":0,\"rz\":90}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/lab-configs/{configId}/links/{linkId}", creation.configId, linkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"L01\",\"startNodeId\":" + startNodeId
                                + ",\"endNodeId\":" + endNodeId
                                + ",\"direction\":\"BIDIRECTIONAL\",\"speedLimit\":0.8}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/lab-configs/{configId}/nodes/{nodeId}", creation.configId, endNodeId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("通行节点仍被连接或机台点位引用: " + endNodeId));
        mockMvc.perform(delete("/api/lab-configs/{configId}/points/{pointId}", creation.configId, pointId))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/lab-configs/{configId}/machines/{machineId}", creation.configId, machineId))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/lab-configs/{configId}/links/{linkId}", creation.configId, linkId))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/lab-configs/{configId}/nodes/{nodeId}", creation.configId, endNodeId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/lab-configs/{configId}", creation.configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.labName").value("实验室 A（东区）"))
                .andExpect(jsonPath("$.data.map.version").value("V1.1"))
                .andExpect(jsonPath("$.data.nodes[0].name").value("新起点"))
                .andExpect(jsonPath("$.data.nodes[0].type").value("WAITING"))
                .andExpect(jsonPath("$.data.links").isEmpty())
                .andExpect(jsonPath("$.data.machines").isEmpty())
                .andExpect(jsonPath("$.data.points").isEmpty());

        mockMvc.perform(delete("/api/lab-configs/{configId}", creation.configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").hasJsonPath())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/api/lab-configs/{configId}", creation.configId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void 校验失败会阻止发布且跨配置引用返回冲突() throws Exception {
        LabInitialization first = initializeLab();
        Long foreignNodeId = postAndGetId("/api/lab-configs/" + first.configId + "/nodes",
                "{\"code\":\"N01\",\"name\":\"外部节点\",\"type\":\"NAVIGATION\","
                        + "\"x\":1,\"y\":2,\"yaw\":0}");
        mockMvc.perform(post("/api/lab-configs/{configId}/publish", first.configId))
                .andExpect(status().isOk());
        MvcResult secondResult = mockMvc.perform(post("/api/lab/drafts"))
                .andExpect(status().isCreated())
                .andReturn();
        Number secondConfigValue = com.jayway.jsonpath.JsonPath.read(
                secondResult.getResponse().getContentAsString(), "$.data.configId");
        Long secondConfigId = secondConfigValue.longValue();
        mockMvc.perform(post("/api/lab-configs/{configId}/nodes", secondConfigId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"INVALID\",\"name\":\"无效库位节点\",\"type\":\"NAVIGATION\","
                                + "\"locationId\":999,\"x\":3,\"y\":4,\"yaw\":90}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        jdbcTemplate.update("INSERT INTO location(id, location_code) VALUES (?, ?)", 10L, "LOC-10");
        Long localNodeId = postAndGetId("/api/lab-configs/" + secondConfigId + "/nodes",
                "{\"code\":\"N02\",\"name\":\"本地节点\",\"type\":\"NAVIGATION\","
                        + "\"locationId\":10,\"x\":3,\"y\":4,\"yaw\":90}");
        postAndGetId("/api/lab-configs/" + secondConfigId + "/nodes",
                "{\"code\":\"N03\",\"name\":\"重复库位节点\",\"type\":\"WAITING\","
                        + "\"locationId\":10,\"x\":5,\"y\":6,\"yaw\":0}");
        Long machineId = postAndGetId("/api/lab-configs/" + secondConfigId + "/machines",
                "{\"code\":\"M01\",\"name\":\"机台\",\"type\":\"LABELER\","
                        + "\"anchorX\":3,\"anchorY\":4,\"anchorYaw\":90}");

        mockMvc.perform(post("/api/lab-configs/{configId}/links", secondConfigId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"L01\",\"startNodeId\":" + foreignNodeId
                                + ",\"endNodeId\":" + localNodeId
                                + ",\"direction\":\"ONE_WAY\",\"speedLimit\":0.5}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/lab-configs/{configId}/points", secondConfigId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"machineId\":" + machineId + ",\"navNodeId\":" + foreignNodeId + ","
                                + "\"code\":\"P01\",\"name\":\"放料点\",\"type\":\"ARM_PLACE\","
                                + "\"frame\":\"MACHINE\",\"x\":0.4,\"y\":0.1,\"z\":0.8,"
                                + "\"rx\":0,\"ry\":0,\"rz\":180}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/lab-configs/{configId}/validate", secondConfigId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.issues[0].code").value("DUPLICATE_LOCATION_BINDING"));
        mockMvc.perform(post("/api/lab-configs/{configId}/publish", secondConfigId))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/lab/drafts"))
                .andExpect(status().isConflict());
    }

    @Test
    void OpenApi契约包含实验室配置接口() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/lab']").exists())
                .andExpect(jsonPath("$.paths['/api/files/images']").exists())
                .andExpect(jsonPath("$.paths['/api/lab-configs/{configId}/map-points']").exists())
                .andExpect(jsonPath("$.paths['/api/lab-configs/{configId}/publish']").exists())
                .andExpect(jsonPath("$.paths['/api/lab-configs/{configId}/nodes/{nodeId}']").exists());

        mockMvc.perform(get("/v3/api-docs/resource-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..properties.code.example")
                        .value(org.hamcrest.Matchers.hasItem(200)))
                .andExpect(jsonPath("$.paths['/api/files/images']").exists())
                .andExpect(jsonPath("$.paths['/api/lab']").exists())
                .andExpect(jsonPath("$.paths['/api/lab-configs/{configId}/map-points']").exists());
    }

    @Test
    void 实验室Controller的每个接口都返回统一Result() {
        for (Class<?> controllerType : Arrays.asList(LabController.class, LabConfigController.class)) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    BaseController.class.isAssignableFrom(controllerType),
                    controllerType.getSimpleName() + " 必须继承 BaseController");
            for (Method method : controllerType.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                    continue;
                }
                org.junit.jupiter.api.Assertions.assertTrue(
                        ApiResult.class.equals(method.getReturnType()),
                        method.getName() + " 必须直接返回 ApiResult<T>");
            }
        }
    }

    @Test
    void 并发创建草稿时唯一实验室只有一个请求成功() throws Exception {
        LabInitialization creation = initializeLab();
        mockMvc.perform(post("/api/lab-configs/{configId}/publish", creation.configId))
                .andExpect(status().isOk());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(post("/api/lab/drafts"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            start.countDown();
            List<Integer> statuses = Arrays.asList(futures.get(0).get(), futures.get(1).get());
            Collections.sort(statuses);
            org.junit.jupiter.api.Assertions.assertEquals(Arrays.asList(201, 409), statuses);
        } finally {
            executor.shutdownNow();
        }

        mockMvc.perform(get("/api/lab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published.revision").value(1))
                .andExpect(jsonPath("$.data.draft.revision").value(2));
    }

    private Long initializeLabAndGetConfigId() throws Exception {
        return initializeLab().configId;
    }

    private LabInitialization initializeLab() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/lab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"实验室 A\","
                                + "\"map\":{\"name\":\"实验室总览地图\",\"version\":\"V1.0\","
                                + "\"imageUrl\":\"/files/lab-a-v1.png\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andReturn();
        Number configId = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.configId");
        return new LabInitialization(configId.longValue());
    }

    private Long postAndGetId(String path, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andReturn();
        Number id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private Long findIdByCode(List<Map<String, Object>> values, String code) {
        return values.stream()
                .filter(value -> code.equals(value.get("code")))
                .map(value -> ((Number) value.get("id")).longValue())
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到对象: " + code));
    }

    private static final class LabInitialization {
        private final Long configId;

        private LabInitialization(Long configId) {
            this.configId = configId;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = LabConfigMapper.class)
    @Import({
            MybatisPlusConfig.class,
            LabConfigController.class,
            LabController.class,
            ImageUploadController.class,
            ImageStorageService.class,
            FileWebConfiguration.class,
            LabConfigApplicationService.class,
            LabConfigDraftEditor.class,
            LabConfigQueryService.class,
            LabMapPointProjector.class,
            LabConfigurationValidator.class,
            LabLocationReferenceChecker.class,
            SchedulingAppOpenApiConfiguration.class
    })
    static class TestApplication {
    }
}
