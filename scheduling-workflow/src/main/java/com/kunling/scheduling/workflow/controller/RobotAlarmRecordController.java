package com.kunling.scheduling.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kunling.scheduling.common.exception.ResourceNotFoundException;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import com.kunling.scheduling.workflow.dto.ExceptionCallBackDto;
import com.kunling.scheduling.workflow.entity.RobotAlarmRecord;
import com.kunling.scheduling.workflow.resp.RobotAlarmRecordResp;
import com.kunling.scheduling.workflow.service.FlowService;
import com.kunling.scheduling.workflow.service.RobotAlarmRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/robotAlarm")
@Tag(name = "机器人异常记录", description = "机器人异常记录的增删改查")
public class RobotAlarmRecordController extends BaseController {

    @Resource
    private FlowService flowService;

    private final RobotAlarmRecordService robotAlarmRecordService;

    public RobotAlarmRecordController(RobotAlarmRecordService robotAlarmRecordService) {
        this.robotAlarmRecordService = robotAlarmRecordService;
    }

    @GetMapping
    @Operation(summary = "分页查询机器人异常记录")
    public ApiResult<IPage<RobotAlarmRecordResp>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @Parameter(description = "异常编号") @RequestParam(required = false) String alarmNo,
            @Parameter(description = "异常分类编码") @RequestParam(required = false) String alarmCategoryCode,
            @Parameter(description = "处置状态") @RequestParam(required = false) Integer handlingStatus,
            @Parameter(description = "流程节点ID") @RequestParam(required = false) Long nodeId) {
        return success(robotAlarmRecordService.pageResp(pageNum, pageSize, alarmNo,
                alarmCategoryCode, handlingStatus, nodeId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询机器人异常记录详情")
    public ApiResult<RobotAlarmRecord> get(@PathVariable Long id) {
        return success(required(id));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "处理机器人异常记录状态")
    public ApiResult<Void> handleStatus(
            @PathVariable Long id,
            @Parameter(description = "处置状态：0-待处置，1-处置中，2-已恢复，3-处置失败，4-已关闭",
                    required = true)
            @RequestParam Integer status) {
        if (status == null || status < 0 || status > 4) {
            throw new IllegalArgumentException("处置状态只能是0、1、2、3或4");
        }

        required(id);
        RobotAlarmRecord entity = new RobotAlarmRecord();
        entity.setId(id);
        entity.setHandlingStatus(status);
        robotAlarmRecordService.updateById(entity);
        return success();
    }

    @PostMapping("/exceptions/handle")
    @Operation(
            summary = "人工处理失败流程",
            description = "客户选择完成当前节点时dealStatus传SUCCEEDED；选择跳过当前节点时传SKIPPED"
    )
    public ApiResult<Boolean> handleException(@RequestBody ExceptionCallBackDto dto) {
        RobotAlarmRecord alarm = robotAlarmRecordService.getById(dto.getAlarmId());
        alarm.setHandlingStatus(2);
        robotAlarmRecordService.updateById(alarm);
        dto.setNodeId(alarm.getNodeId());
        flowService.dealExceptionCallBack(dto);
        return ApiResult.success(true);
    }


    private RobotAlarmRecord required(Long id) {
        RobotAlarmRecord entity = robotAlarmRecordService.getById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("机器人异常记录不存在: " + id);
        }
        return entity;
    }

}
