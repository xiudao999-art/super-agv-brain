package com.kunling.scheduling.app.hometest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.app.controller.HomeTestController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HomeTestControllerTest {

    private MutableClock clock;
    private HomeOverviewTestService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC);
        HomeTestDataRepository repository = new HomeTestDataRepository(
                new ObjectMapper(),
                new ClassPathResource(HomeTestDataRepository.DEFAULT_RESOURCE_PATH)
        );
        service = new HomeOverviewTestService(repository, clock);
        mockMvc = MockMvcBuilders.standaloneSetup(new HomeTestController(service)).build();
    }

    @Test
    void 接口返回运行总览所需的全部测试数据且硬件模组均在线() throws Exception {
        mockMvc.perform(get("/api/home-test/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.agvStatus.agvCode").value("AGV-01"))
                .andExpect(jsonPath("$.data.agvStatus.online").value(true))
                .andExpect(jsonPath("$.data.agvStatus.executionStatus").value("EXECUTING"))
                .andExpect(jsonPath("$.data.agvStatus.batteryPercent").value(78))
                .andExpect(jsonPath("$.data.currentOrder.executingCount").value(1))
                .andExpect(jsonPath("$.data.currentOrder.queuedCount").value(3))
                .andExpect(jsonPath("$.data.locationConsistency.rate").value(98.6))
                .andExpect(jsonPath("$.data.locationConsistency.pendingConfirmationCount").value(1))
                .andExpect(jsonPath("$.data.todayTaskCompletion.completedCount").value(47))
                .andExpect(jsonPath("$.data.todayTaskCompletion.totalCount").value(48))
                .andExpect(jsonPath("$.data.todayTaskCompletion.completionRate").value(97.9))
                .andExpect(jsonPath("$.data.hardwareModules.length()").value(5))
                .andExpect(jsonPath("$.data.hardwareModules[0].online").value(true))
                .andExpect(jsonPath("$.data.hardwareModules[1].online").value(true))
                .andExpect(jsonPath("$.data.hardwareModules[2].online").value(true))
                .andExpect(jsonPath("$.data.hardwareModules[3].online").value(true))
                .andExpect(jsonPath("$.data.hardwareModules[4].online").value(true));
    }

    @Test
    void 电量每满两分钟下降百分之一且最低保持百分之十() {
        assertThat(service.getOverview().getAgvStatus().getBatteryPercent()).isEqualTo(78);

        clock.advance(Duration.ofSeconds(119));
        assertThat(service.getOverview().getAgvStatus().getBatteryPercent()).isEqualTo(78);

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.getOverview().getAgvStatus().getBatteryPercent()).isEqualTo(77);

        clock.advance(Duration.ofDays(1));
        assertThat(service.getOverview().getAgvStatus().getBatteryPercent()).isEqualTo(10);
    }

    /** 可由测试推进的时钟，确保时间规则测试不依赖真实等待。 */
    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
